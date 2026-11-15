"""Exercise real StrobeLights GLSL with a hardware OpenGL context.

Requires moderngl, numpy and Pillow. Reads shaders from the supplied JAR and client;
no Minecraft/StrobeLights assets are copied into the XmasLights model pack.
"""
from pathlib import Path
import io, zipfile, re, math, json, sys, argparse, hashlib
import numpy as np
import moderngl as gl
from PIL import Image

ROOT=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser()
parser.add_argument('--strobe-jar',required=True,type=Path)
parser.add_argument('--minecraft-jar',required=True,type=Path)
parser.add_argument('--source-pack',type=Path)
parser.add_argument('--hybrid-pack',type=Path)
parser.add_argument('--carrier-texture',type=Path,help='Client-prepared carrier PNG (e.g. OptiFine Java2D atlas resize). Defaults to the actual embedded texture.')
parser.add_argument('--report',type=Path,default=ROOT/'.tools/strobe-gpu-verification.json')
parser.add_argument('--legacy',action='store_true')
parser.add_argument('--runtime-sources',type=Path,help='Replay actual carrier payloads/positions captured from Paper + StrobeLights')
parser.add_argument('--size',default='1360x768')
parser.add_argument('--pipeline',action='store_true',help='Also verify the real centers and six aggregation passes.')
parser.add_argument('--occupied',action='store_true',help='Exercise carriers over already rendered opaque item geometry.')
parser.add_argument('--distances',default='8,24,48,96,192,256,480,512,680,768',help='Comma-separated camera distances in blocks.')
parser.add_argument('--far-plane',type=float,default=2048,help='Minecraft depth far plane; 1920 for 30 chunks, 2048 for 32.')
args=parser.parse_args()
distances=[float(value) for value in args.distances.split(',')]
stable=not args.legacy
host=zipfile.ZipFile(args.strobe_jar)
pack=zipfile.ZipFile(io.BytesIO(host.read('embedded/StrobeLights-ResourcePack-1.21.4.zip')))
client=zipfile.ZipFile(args.minecraft_jar)
hybrid=zipfile.ZipFile(args.hybrid_pack) if args.hybrid_pack else None
def read(name):
    if hybrid and name in hybrid.namelist():return hybrid.read(name).decode('utf-8')
    if args.source_pack:
        local=args.source_pack/name
        if local.is_file(): return local.read_text(encoding='utf-8')
    return (pack if name in pack.namelist() else client).read(name).decode('utf-8')
def shader(name, nested=False):
    text=read(name)
    text=re.sub(r'#moj_import\s+[<"]([^>"]+)[>"]',lambda m:shader('assets/'+(m[1] if ':' in m[1] else 'minecraft:'+m[1]).replace(':','/shaders/include/'),True),text)
    return re.sub(r'^#version[^\n]*','',text,flags=re.M) if nested else text
position_contract=read('assets/strobelights/shaders/include/stable_slot_common.glsl') if stable else ''
limit_match=re.search(r'STROBE_STABLE_POSITION_LIMIT\s*=\s*([0-9.]+)',position_contract)
position_limit=float(limit_match[1]) if limit_match else 512.0
ctx=gl.create_standalone_context(require=330)
core=ctx.program(vertex_shader=shader('assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.vsh'),fragment_shader=shader('assets/minecraft/shaders/core/rendertype_item_entity_translucent_cull.fsh'))
filter_program=ctx.program(vertex_shader=shader('assets/minecraft/shaders/post/filter.vsh'),fragment_shader=shader('assets/minecraft/shaders/post/filter.fsh'))
size=tuple(int(value) for value in args.size.split('x'))
colour=ctx.texture(size,4,dtype='f1');depth=ctx.depth_texture(size);depth.compare_func=''
for texture in [colour,depth]:texture.filter=(gl.NEAREST,gl.NEAREST);texture.repeat_x=False;texture.repeat_y=False
frame=ctx.framebuffer([colour],depth)
result=ctx.texture(size,4,dtype='f1');filtered=ctx.framebuffer([result]);result.filter=(gl.NEAREST,gl.NEAREST)
carrier_image=Image.open(args.carrier_texture if args.carrier_texture else io.BytesIO(pack.read('assets/strobelights/textures/item/internal/strobe_rgb_carrier.png'))).convert('RGBA')
marker=ctx.texture(carrier_image.size,4,carrier_image.tobytes());marker.filter=(gl.NEAREST,gl.NEAREST)
lightmap=ctx.texture((16,16),4,bytes([255]*16*16*4));lightmap.filter=(gl.NEAREST,gl.NEAREST)
quad=np.array([[-1,-1,0,1],[1,-1,0,1],[1,1,0,1],[-1,1,0,1]],dtype='f4');indices=ctx.buffer(np.array([0,1,2,2,3,0],dtype='i4').tobytes())
post=ctx.vertex_array(filter_program,[(ctx.buffer(quad.tobytes()),'4f','Position')],indices)
filter_program['ProjMat'].write(np.eye(4,dtype='f4').tobytes());filter_program['DiffuseSampler']=0;filter_program['DiffuseDepthSampler']=1;filter_program['Range']=512
post_stages={}
def stage(name):
    if name not in post_stages:
        definition=json.loads(read('assets/minecraft/shaders/post/'+name+'.json'))
        vertex=definition['vertex'].split(':',1)[-1];fragment=definition['fragment'].split(':',1)[-1]
        program=ctx.program(vertex_shader=shader('assets/minecraft/shaders/'+vertex+'.vsh'),fragment_shader=shader('assets/minecraft/shaders/'+fragment+'.fsh'))
        vao=ctx.vertex_array(program,[(ctx.buffer(quad.tobytes()),'4f','Position')],indices)
        post_stages[name]=(program,vao)
    return post_stages[name]
def target(dimensions):
    texture=ctx.texture(dimensions,4,dtype='f1');texture.filter=(gl.NEAREST,gl.NEAREST);texture.repeat_x=False;texture.repeat_y=False
    return texture,ctx.framebuffer([texture])
if args.pipeline:
    swap_a,fb_a=target(size);swap_b,fb_b=target(size);centers,fb_centers=target(size);lights,fb_lights=target((1024,7))
    # Compile the lighting stages too, including their shared stable-source decoder.
    stage('light');stage('light_t');stage('transparency')
def draw_stage(name,source,output):
    program,vao=stage(name);output.use();ctx.viewport=(0,0,*output.size)
    for key,value in {'ProjMat':np.eye(4,dtype='f4'),'DiffuseSize':size,'Step':8.0,'Test':0.0,'OutSize':output.size}.items():
        if key in program:
            if isinstance(value,np.ndarray):program[key].write(value.tobytes())
            else:program[key].value=value
    for unit,(key,texture) in enumerate([('DiffuseSampler',source),('ItemEntitySampler',colour),('ItemEntityDepthSampler',depth),('ColoredCentersSampler',centers)]):
        if key in program:program[key].value=unit;texture.use(unit)
    vao.render(gl.TRIANGLES)
def aggregate(sources):
    draw_stage('centers',result,fb_centers)
    draw_stage('aggregate_1',centers,fb_a)
    draw_stage('aggregate_2',swap_a,fb_b)
    draw_stage('aggregate_3',swap_b,fb_a)
    draw_stage('aggregate_4',swap_a,fb_b)
    draw_stage('aggregate_5',swap_b,fb_a)
    draw_stage('aggregate_6',swap_a,fb_lights)
    pixels=np.frombuffer(lights.read(),dtype=np.uint8).reshape(7,1024,4)
    def integer(value):return int(value[0])+int(value[1])*255+int(value[2])*255*255
    count=integer(pixels[0,-1]);maximum=0
    assert count==len(sources),(count,len(sources))
    for i in range(count):
        value=(int(pixels[3,i,0])<<16)|(int(pixels[3,i,1])<<8)|int(pixels[3,i,2])
        slot=[8,9,11,12,14,15,6,7].index(value>>20)*16+((value>>12)&15)
        actual=np.array([integer(pixels[axis,i])*(1 if pixels[axis,i,3]>190 else -1)/1000 for axis in range(3)])
        maximum=max(maximum,float(np.max(np.abs(actual-np.array(sources[slot])))))
        assert abs(int(pixels[5,i,0])/255-.75)<.01
        assert int(pixels[4,i,0])==153 # radius band 9, independently retained
        if 'strobeStableDepthProjection' in position_contract:
            def depth_integer(value):return int(value[0])*62500+int(value[1])*250+int(value[2])
            actual_a=-1-depth_integer(pixels[4,i,1:])/8388608
            actual_b=-depth_integer(pixels[5,i,1:])/134217728
            assert actual_a==float(np.float32(projection[2,2])),(actual_a,projection[2,2])
            assert actual_b==float(np.float32(projection[2,3])),(actual_b,projection[2,3])
    assert maximum<.006,maximum
    return maximum
def set_uniform(name,value):
    if name in core:
        if isinstance(value,np.ndarray):core[name].write(value.astype('f4').T.tobytes())
        else:core[name].value=value
set_uniform('ModelViewMat',np.eye(4));set_uniform('Sampler0',0);set_uniform('Sampler2',1);set_uniform('ColorModulator',(1,1,1,1));set_uniform('Light0_Direction',(0,1,0));set_uniform('Light1_Direction',(0,1,0));set_uniform('FogStart',0);set_uniform('FogEnd',1024);set_uniform('FogColor',(0,0,0,0));set_uniform('FogShape',0)
f=1/math.tan(math.radians(70)/2);near=.05;far=args.far_plane
projection=np.array([[f/(size[0]/size[1]),0,0,0],[0,f,0,0],[0,0,-(far+near)/(far-near),-2*far*near/(far-near)],[0,0,-1,0]],dtype='f4')
set_uniform('ProjMat',projection)
def render(sources,energy=8,payloads=None):
    positions=[];colours=[]
    for slot,(x,y,z) in enumerate(sources):
        for ox,oz in [(-1,-1),(-1,1),(1,1),(1,-1)]:
            positions.append([x+ox*.0000625,y,-z+oz*.0000625]);encoded=0xA00005|(energy<<12)|(round(energy*.8)<<8)|(round(energy*.4)<<4)
            if stable:
                encoded=([8,9,11,12,14,15,6,7][slot//16]<<20)|(9<<16)|((slot%16)<<12)|(energy<<8)|(round(energy*.8)<<4)|round(energy*.4)
            if payloads is not None: encoded=payloads[slot]
            colours.append([((encoded>>16)&255)/255,((encoded>>8)&255)/255,(encoded&255)/255,1])
    values={'Position':np.array(positions,dtype='f4'),'Color':np.array(colours,dtype='f4'),'UV0':np.full((len(positions),2),.5,dtype='f4'),'UV1':np.zeros((len(positions),2),dtype='f4'),'UV2':np.zeros((len(positions),2),dtype='i4'),'Normal':np.tile([0,1,0],(len(positions),1)).astype('f4')}
    buffers=[];attributes=[]
    for name,array in values.items():
        if name not in core:continue
        buffer=ctx.buffer(array.tobytes());buffers.append(buffer);attributes.append((buffer,str(array.shape[1])+('i' if array.dtype==np.int32 else 'f'),name))
    index=ctx.buffer(np.array([i*4+v for i in range(len(sources)) for v in [0,1,2,2,3,0]],dtype='i4').tobytes());vao=ctx.vertex_array(core,attributes,index)
    frame.use();ctx.viewport=(0,0,*size);frame.clear(*((.2,.1,.3,1) if args.occupied else (0,0,0,0)),depth=.99 if args.occupied else 1);ctx.enable(gl.DEPTH_TEST|gl.BLEND|gl.CULL_FACE);ctx.front_face='ccw';ctx.depth_func='<=';ctx.blend_func=(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA,gl.ONE,gl.ONE_MINUS_SRC_ALPHA)
    marker.use(0);lightmap.use(1);vao.render(gl.TRIANGLES)
    ctx.disable(gl.DEPTH_TEST|gl.BLEND);filtered.use();filtered.clear();colour.use(0);depth.use(1);post.render(gl.TRIANGLES)
    pixels=np.frombuffer(filtered.read(components=4),dtype=np.uint8).reshape(size[1],size[0],4);hits=pixels[pixels[:,:,3]>0];encoded=(hits[:,0].astype('i4')<<16)|(hits[:,1].astype('i4')<<8)|hits[:,2]
    decoded=[{'mode':int(v>>20),'size':int((v>>(16 if stable else 12))&15),'rgb':[int((v>>8)&15),int((v>>4)&15),int(v&15)]} for v in encoded]
    position_error=0
    if stable:
        packet_pixels=np.frombuffer(colour.read(),dtype=np.uint8).reshape(size[1],size[0],4)
        for (y,x),value in zip(np.argwhere(pixels[:,:,3]>0),encoded):
            slot=[8,9,11,12,14,15,6,7].index(int(value>>20))*16+int((value>>12)&15)
            components=[packet_pixels[y-1,x],packet_pixels[y-1,x+1],packet_pixels[y,x-1]]
            actual=np.array([(int(v[0])*62500+int(v[1])*250+int(v[2]))/4096-position_limit for v in components])
            position_error=max(position_error,float(np.max(np.abs(actual-np.array(sources[slot])))))
    if args.pipeline and stable:position_error=max(position_error,aggregate(sources))
    for obj in [vao,index,*buffers]:obj.release()
    if payloads is None:
        corrupt=[v for v in decoded if v['size']!=(9 if stable else 0) or v['rgb']!=[energy,round(energy*.8),round(energy*.4)]]
    else:
        corrupt=[]
        for raw,value in zip(encoded,decoded):
            slot=[8,9,11,12,14,15,6,7].index(int(raw>>20))*16+int((raw>>12)&15)
            expected=payloads[slot]
            if value['size'] != ((expected>>16)&15) or value['rgb'] != [(expected>>8)&15,(expected>>4)&15,expected&15]: corrupt.append(value)
    return {'sources':len(sources),'decoded':len(decoded),'position_error':position_error,'corrupt':corrupt,'values':decoded}
report={'gpu':ctx.info['GL_RENDERER'],'size':size,'stable':stable,'occupied':args.occupied,'pipeline':args.pipeline,'far_plane':far,
        'carrier_texture':str(args.carrier_texture) if args.carrier_texture else 'embedded',
        'carrier_rgba':list(carrier_image.getpixel((0,0))),
        'strobe_jar_sha256':hashlib.sha256(args.strobe_jar.read_bytes()).hexdigest(),
        'hybrid_pack_sha256':hashlib.sha256(args.hybrid_pack.read_bytes()).hexdigest() if args.hybrid_pack else None,
        'source_pack_override':bool(args.source_pack),'cases':[]}
for count in [32,128]:
    for fov in [15,70,110]:
        f=1/math.tan(math.radians(fov)/2);projection[0,0]=f/(size[0]/size[1]);projection[1,1]=f;set_uniform('ProjMat',projection)
        for distance in distances:
            for yaw in [0,45,135,240]:
                angle=math.radians(yaw);source=[(x*.9-.05,-5,distance+row*2.5) for row in range(count//8) for x in range(-4,4)]
                sources=[(x*math.cos(angle)-z*math.sin(angle),y,x*math.sin(angle)+z*math.cos(angle)) for x,y,z in source]
                value=render(sources);value.update(distance=distance,yaw=yaw,fov=fov);report['cases'].append(value)
                if stable:assert value['decoded']==count and not value['corrupt'] and value['position_error']<.004,value
if args.runtime_sources:
    runtime=json.loads(args.runtime_sources.read_text())
    replay=render([sample['position'] for sample in runtime],payloads=[sample['payload'] for sample in runtime])
    assert replay['decoded']==len(runtime) and not replay['corrupt'] and replay['position_error']<.004,replay
    report['runtime_replay']=replay
if args.pipeline and stable:
    # Render the actual RGB light passes, not just their transport. A packet can
    # decode correctly while a depth gate or range fade silently removes its light.
    report['lighting_cases']=[]
    dimensions=(65,65); illumination,fb_illumination=target(dimensions)
    comparison=ctx.texture(dimensions,1,np.ones(dimensions,dtype='f4').tobytes(),dtype='f4')
    for distance in distances:
        render([(0,0,distance)])
        plane=(far+near-2*far*near/distance)/(far-near)*.5+.5
        plane_depth=ctx.texture(dimensions,1,np.full(dimensions,plane,dtype='f4').tobytes(),dtype='f4')
        for name in ['light','light_t']:
            program,vao=stage(name)
            for key,value in {'ProjMat':np.eye(4,dtype='f4'),'DiffuseDepthSize':dimensions,
                              'LightsSize':(1024,7),'Range':512.0}.items():
                if key in program:
                    if isinstance(value,np.ndarray):program[key].write(value.tobytes())
                    else:program[key].value=value
            for unit,(key,texture) in enumerate([('LightsSampler',lights),('DiffuseDepthSampler',plane_depth),
                                                 ('OcclusionDepthSampler',comparison),('CompareDepthSampler',comparison)]):
                if key in program:program[key].value=unit;texture.use(unit)
            fb_illumination.use();ctx.viewport=(0,0,*dimensions);ctx.disable(gl.DEPTH_TEST|gl.BLEND);fb_illumination.clear();vao.render(gl.TRIANGLES)
            pixel=np.frombuffer(illumination.read(),dtype=np.uint8).reshape(65,65,4)[32,32]
            red=float(pixel[0])/255
            report['lighting_cases'].append({'stage':name,'distance':distance,'center_red':red})
            assert red>.10,(name,distance,pixel.tolist(),'RGB disappeared at camera distance')
        plane_depth.release()
if args.pipeline and args.occupied:
    # Exercise the real Fabulous compositor too: no encoded packet colours may
    # leak into the final picture, even over a completely occupied item layer.
    program,_=stage('transparency');output,fb_output=target(size)
    w,h=size
    pixel_quad=np.array([[0,0,0,1],[w,0,0,1],[w,h,0,1],[0,h,0,1]],dtype='f4')
    compositor=ctx.vertex_array(program,[(ctx.buffer(pixel_quad.tobytes()),'4f','Position')],indices)
    ortho=np.array([[2/w,0,0,-1],[0,2/h,0,-1],[0,0,1,0],[0,0,0,1]],dtype='f4')
    program['ProjMat'].write(ortho.T.tobytes());program['OutSize'].value=size
    if 'Test' in program:program['Test'].value=0
    background=ctx.texture(size,4,bytes([19,27,43,255])*(w*h));empty=ctx.texture(size,4,bytes(4*w*h))
    far_depth=ctx.texture(size,1,np.full((h,w),.999,dtype='f4').tobytes(),dtype='f4')
    for texture in [background,empty,far_depth]:texture.filter=(gl.NEAREST,gl.NEAREST);texture.repeat_x=False;texture.repeat_y=False
    names=['DiffuseSampler','DiffuseDepthSampler','TranslucentSampler','TranslucentDepthSampler','ItemEntitySampler','ItemEntityDepthSampler','ItemEntityLightSampler','ParticlesSampler','ParticlesDepthSampler','CloudsSampler','CloudsDepthSampler','WeatherSampler','WeatherDepthSampler']
    for unit,name in enumerate(names):
        if name not in program:continue
        texture=colour if name=='ItemEntitySampler' else depth if name=='ItemEntityDepthSampler' else background if name=='DiffuseSampler' else far_depth if 'Depth' in name else empty
        texture.use(unit);program[name].value=unit
    fb_output.use();ctx.viewport=(0,0,w,h);ctx.disable(gl.BLEND|gl.DEPTH_TEST);compositor.render(gl.TRIANGLES)
    colours=np.unique(np.frombuffer(output.read(),dtype=np.uint8).reshape(-1,4)[:,:3],axis=0).astype(int)
    allowed=[np.array([19,27,43]),np.array([51,26,76])]
    assert all(any(np.max(np.abs(value-reference))<=1 for reference in allowed) for value in colours),colours.tolist()
    report['compositor_packet_leaks']=0
args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2))
print(json.dumps({'gpu':report['gpu'],'stable':stable,'size':size,'cases':len(report['cases']),'missing':sum(c['sources']-c['decoded'] for c in report['cases']),'corrupt':sum(len(c['corrupt']) for c in report['cases']),'max_position_error':max(c['position_error'] for c in report['cases'])}))
