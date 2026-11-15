"""Run with Blender --background --python tools/render_previews.py.
Renders the actual Minecraft JSON/PNG model geometry. Lighting is a Blender simulation.
"""
import bpy, json, math, random, sys
from pathlib import Path
from mathutils import Vector, Matrix, Quaternion

ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'model-pack/assets/xmaslights'
TEXTURE_ROOTS={'xmaslights':ASSETS}
OUT=ROOT/'previews'; OUT.mkdir(exist_ok=True)
random.seed(191225)
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.context.preferences.filepaths.save_version=0
bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete(use_global=False)
scene=bpy.context.scene
scene.render.engine='CYCLES'; scene.cycles.samples=32; scene.cycles.use_denoising=True
scene.render.resolution_x=1600; scene.render.resolution_y=1200; scene.render.resolution_percentage=100
scene.render.image_settings.file_format='PNG'; scene.render.film_transparent=False
scene.view_settings.view_transform='Standard'; scene.view_settings.look='Medium High Contrast' if 'Medium High Contrast' in [] else 'None'
scene.render.image_settings.color_mode='RGBA'
world=bpy.data.worlds.new('Studio world'); scene.world=world; world.use_nodes=True
world.node_tree.nodes['Background'].inputs['Color'].default_value=(.19,.23,.29,1)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.35

def srgb(v):
    return v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4
def rgb(hexval): return tuple(srgb(((hexval>>s)&255)/255) for s in [16,8,0])
materials={}
def material(texture,tint=None,emission=False):
    key=(texture,tint,emission)
    if key in materials: return materials[key]
    mat=bpy.data.materials.new(texture+str(tint)); mat.use_nodes=True
    nodes=mat.node_tree.nodes; links=mat.node_tree.links; principled=nodes.get('Principled BSDF')
    namespace,path=texture.split(':',1) if ':' in texture else ('xmaslights',texture)
    tex=nodes.new('ShaderNodeTexImage'); tex.image=bpy.data.images.load(str(TEXTURE_ROOTS[namespace]/('textures/'+path+'.png')),check_existing=True); tex.interpolation='Closest'
    if tint is None: links.new(tex.outputs['Color'],principled.inputs['Base Color'])
    else:
        principled.inputs['Base Color'].default_value=rgb(tint)+(1,)
    links.new(tex.outputs['Alpha'],principled.inputs['Alpha'])
    principled.inputs['Roughness'].default_value=.72
    if emission:
        if emission=='exact':
            node=nodes.new('ShaderNodeEmission'); node.inputs['Strength'].default_value=1
            if tint is None: links.new(tex.outputs['Color'],node.inputs['Color'])
            else: node.inputs['Color'].default_value=rgb(tint)+(1,)
            links.new(node.outputs['Emission'],nodes.get('Material Output').inputs['Surface'])
        else:
            principled.inputs['Emission Color'].default_value=rgb(tint or 0xFFD38A)+(1,)
            principled.inputs['Emission Strength'].default_value=1.6
    materials[key]=mat; return mat

mesh_cache={}
def model_mesh(name,tint=None,definition=None,block=False):
    key=(name,tint,block)
    if key in mesh_cache: return mesh_cache[key]
    model=definition if definition is not None else json.loads((ASSETS/f'models/item/{name}.json').read_text())
    vertices=[]; faces=[]; uv_faces=[]; material_ids=[]; slots=[]
    for element in model['elements']:
        x0,y0,z0=element['from']; x1,y1,z1=element['to']
        corners={
          'north':[(x1,y0,z0),(x0,y0,z0),(x0,y1,z0),(x1,y1,z0)],
          'south':[(x0,y0,z1),(x1,y0,z1),(x1,y1,z1),(x0,y1,z1)],
          'east':[(x1,y0,z1),(x1,y0,z0),(x1,y1,z0),(x1,y1,z1)],
          'west':[(x0,y0,z0),(x0,y0,z1),(x0,y1,z1),(x0,y1,z0)],
          'up':[(x0,y1,z0),(x0,y1,z1),(x1,y1,z1),(x1,y1,z0)],
          'down':[(x0,y0,z1),(x0,y0,z0),(x1,y0,z0),(x1,y0,z1)]}
        rotation=element.get('rotation'); transform=None; origin=None
        if rotation:
            origin=Vector(rotation['origin']); transform=Matrix.Rotation(math.radians(rotation['angle']),3,rotation['axis'].upper())
        for side,face in element['faces'].items():
            quad=[]
            for corner in corners[side]:
                point=Vector(corner)
                if transform: point=origin+transform@(point-origin)
                centre=0 if block else 8
                quad.append(((point.x-centre)/16,-(point.z-centre)/16,(point.y-centre)/16))
            start=len(vertices); vertices.extend(quad); faces.append(tuple(start+i for i in range(4)))
            u0,v0,u1,v1=face.get('uv',[0,0,16,16]); uv_faces.append([(u0/16,1-v1/16),(u1/16,1-v1/16),(u1/16,1-v0/16),(u0/16,1-v0/16)])
            texture=model['textures'][face['texture'][1:]]
            tint_index=face.get('tintindex',-1)
            face_tint=tint[tint_index%len(tint)] if tint_index>=0 and isinstance(tint,tuple) else tint if tint_index>=0 else None
            emission=('exact' if name.startswith(('colored_bulb','colored_cube')) else name.startswith('garland_lights')) if tint_index>=0 else False
            mat=material(texture,face_tint,emission)
            if mat not in slots: slots.append(mat)
            material_ids.append(slots.index(mat))
    mesh=bpy.data.meshes.new(name+str(tint)); mesh.from_pydata(vertices,[],faces); mesh.update()
    for mat in slots: mesh.materials.append(mat)
    uv_layer=mesh.uv_layers.new()
    for polygon,uvs,mat_id in zip(mesh.polygons,uv_faces,material_ids):
        polygon.material_index=mat_id
        for loop_index,uv in zip(polygon.loop_indices,uvs): uv_layer.data[loop_index].uv=uv
    mesh_cache[key]=mesh; return mesh

dynamic=[]
def model(name,position,direction,length,size=1,tint=None,wall=False):
    obj=bpy.data.objects.new(name,model_mesh(name,tint)); scene.collection.objects.link(obj); dynamic.append(obj)
    obj.location=position; obj.rotation_mode='QUATERNION'
    # Match LightGeometry.module in Minecraft coordinates, then convert its basis to Blender.
    x=Vector((direction.x,direction.z,-direction.y)).normalized()
    if wall:
        z=Vector((0,0,1)); z=(z-x*z.dot(x)).normalized(); y=z.cross(x).normalized()
    else:
        if abs(x.x)>=abs(x.z):
            if x.x<0: x.negate()
        elif x.z<0: x.negate()
        y=Vector((0,1,0))-x*x.y
        if y.length_squared<1e-6: y=Vector((0,0,1))
        y.normalize(); z=x.cross(y).normalized()
    convert=lambda v:Vector((v.x,-v.z,v.y))
    obj.rotation_quaternion=Matrix((convert(x),-convert(z),convert(y))).transposed().to_quaternion()
    obj.scale=(length,size,size)
    return obj

def point(a,b,t,sag): return a.lerp(b,t)-Vector((0,0,sag*4*t*(1-t)))
def chain(a,b,variant='classic',palette=(0xFFD38A,),sag_factor=.16,night=False,wall=False,back=False,covered=0):
    a=Vector(a); b=Vector(b); horizontal=math.hypot(b.x-a.x,b.y-a.y); sag=horizontal*sag_factor
    length=sum((point(a,b,(i+1)/16,sag)-point(a,b,i/16,sag)).length for i in range(16)); count=max(1,math.ceil(length/1.0))
    for i in range(count):
        left=point(a,b,i/count,sag); right=point(a,b,(i+1)/count,sag); center=point(a,b,(i+.5)/count,sag)
        direction=right-left; color=palette[i%len(palette)]
        suffix='_no_back' if covered<0 else '_no_front' if covered>0 else ''
        body=model(f'garland_{variant}_body'+suffix,center,direction,direction.length+.035,wall=wall)
        led=model('garland_lights'+suffix,center,direction,direction.length+.035,tint=tuple(palette[(i+j)%len(palette)] for j in range(8)),wall=wall)
        if back:
            body.rotation_quaternion=Quaternion((0,0,1),math.pi)@body.rotation_quaternion
            led.rotation_quaternion=Quaternion((0,0,1),math.pi)@led.rotation_quaternion
        stride=3 if len(palette)>1 else 2
        distance=center.z+.045
        if night and i%stride==0 and 1.8<distance<=8:
            attenuation=1/(1+(distance/6)**2)
            pool=light('Simulated surface impact',(center.x,center.y,-.005),color,3*attenuation,.18,'POINT')
            if hasattr(pool.data,'use_custom_distance'):
                pool.data.use_custom_distance=True; pool.data.cutoff_distance=min(2.25*math.sqrt(attenuation),distance-.7)

def plain(name,color):
    mat=bpy.data.materials.new(name); mat.diffuse_color=rgb(color)+(1,); mat.use_nodes=True
    mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'].default_value=rgb(color)+(1,)
    mat.node_tree.nodes['Principled BSDF'].inputs['Roughness'].default_value=.85
    return mat
stone=[plain('stone'+str(i),c) for i,c in enumerate([0x777D77,0x85887F,0x8F9188,0x6A756E])]
wood=[plain('oak'+str(i),c) for i,c in enumerate([0x805437,0x976842,0xA27148,0x6B472F])]
brass=plain('brass',0xC29948); dark=plain('dark metal',0x2D302D); panel=plain('studio backdrop',0x19282A)
def cube(name,location,size,mat):
    bpy.ops.mesh.primitive_cube_add(size=1,location=location); obj=bpy.context.object; obj.name=name; obj.dimensions=size
    obj.data.materials.append(mat); dynamic.append(obj); return obj
def light(name,position,color,energy,size,kind='AREA'):
    data=bpy.data.lights.new(name,kind); data.color=rgb(color); data.energy=energy
    if kind=='AREA': data.shape='DISK'; data.size=size
    else: data.shadow_soft_size=size
    obj=bpy.data.objects.new(name,data); scene.collection.objects.link(obj); obj.location=position; dynamic.append(obj)
    if kind=='AREA': obj.rotation_euler=(Vector((0,0,2))-obj.location).to_track_quat('-Z','Y').to_euler()
    return obj
def camera(location,target,ortho):
    data=bpy.data.cameras.new('Preview Camera'); obj=bpy.data.objects.new('Preview Camera',data); scene.collection.objects.link(obj); dynamic.append(obj)
    obj.location=location; obj.rotation_euler=(Vector(target)-obj.location).to_track_quat('-Z','Y').to_euler(); data.type='ORTHO'; data.ortho_scale=ortho; scene.camera=obj
def caption(title,font_scale=1):
    backdrop=bpy.data.materials.new('caption backdrop'); backdrop.use_nodes=True
    nodes=backdrop.node_tree.nodes; nodes.clear()
    output=nodes.new('ShaderNodeOutputMaterial'); emission=nodes.new('ShaderNodeEmission')
    emission.inputs['Color'].default_value=(.015,.025,.028,1)
    backdrop.node_tree.links.new(emission.outputs[0],output.inputs['Surface'])
    mat=plain('caption',0xE8EDE8)
    mat.node_tree.nodes['Principled BSDF'].inputs['Emission Color'].default_value=(.8,.85,.8,1)
    mat.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value=1
    width=scene.camera.data.ortho_scale; height=width*scene.render.resolution_y/scene.render.resolution_x
    for y0,y1 in [(height/2-.36,height/2),(-height/2,-height/2+.27)]:
        mesh=bpy.data.meshes.new('caption background')
        mesh.from_pydata([(-width/2,y0,0),(width/2,y0,0),(width/2,y1,0),(-width/2,y1,0)],[],[(0,1,2,3)])
        obj=bpy.data.objects.new('caption background',mesh); scene.collection.objects.link(obj); dynamic.append(obj)
        obj.parent=scene.camera; obj.location=(0,0,-1.01); mesh.materials.append(backdrop)
    for text,size,y in [(title,.14,height/2-.27),('Geometría del pack · Iluminación simulada en Blender · No es una captura de Minecraft',.065,-height/2+.12)]:
        data=bpy.data.curves.new('Caption','FONT'); data.body=text; data.size=size*font_scale
        obj=bpy.data.objects.new('Caption',data); scene.collection.objects.link(obj); dynamic.append(obj)
        obj.parent=scene.camera; obj.location=(-width/2+.17,y,-1); obj.data.materials.append(mat)
def clear():
    for obj in dynamic: bpy.data.objects.remove(obj,do_unlink=True)
    dynamic.clear()
def door(night=False):
    for row in range(10):
        for col in range(-5,6): cube('stone brick',(col*.64+(row%2)*.32,.5,row*.55+.24),(.625,.38,.535),random.choice(stone))
    for col in range(-3,4): cube('door planks',(col*.305,-.025,1.82),(.299,.18,3.64),wood[(col+3)%4])
    for x in [-1.13,1.13]: cube('oak frame',(x,-.03,1.84),(.18,.27,3.9),wood[2])
    cube('lintel',(0,-.03,3.77),(2.42,.28,.2),wood[2])
    for x in [-.5,.5]:
        for z in [.9,2.2]: cube('raised door panel',(x,-.15,z),(.82,.08,1.02),wood[0])
    for x in [-.98,.98]: cube('door border',(x,-.18,1.8),(.05,.04,3.45),brass)
    for z in [.15,1.5,3.4]: cube('door border',(0,-.18,z),(2.0,.04,.045),brass)
    cube('door handle',(.77,-.24,1.55),(.09,.13,.22),dark)
    for x in range(-5,6):
        for y in range(-4,2): cube('floor block',(x*.7,y*.7,-.16),(.69,.69,.23),stone[(x+y)%4])
    colors=(0xFF4141,0x60E47C,0xFFD557,0x609BFF) if night else (0xFFD38A,)
    variant='rgb' if night else 'classic'
    chain((-1.43,-.35,.3),(-1.43,-.35,4.05),variant,colors,night=night,wall=True)
    chain((-1.43,-.35,4.05),(1.43,-.35,4.05),variant,colors,.16,night,wall=True)
    chain((1.43,-.35,4.05),(1.43,-.35,.3),variant,colors,night=night,wall=True)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value=.025 if night else .42
    light('Soft key',(-3,-5,7),0xC9DFFF,80 if night else 700,5)
    light('Warm fill',(4,-2,5),0xFFD8A5,30 if night else 330,4)
    camera((5.7,-10,6.2),(0,0,2.05),6.8)
    caption('XmasLights · Guirnalda RGB nocturna' if night else 'XmasLights · Abeto clásico con luces cálidas')

def render(name):
    selection=next((arg.split('=',1)[1] for arg in sys.argv if arg.startswith('--only=')),None)
    if '--stairs-only' in sys.argv: selection='04-draped-stairs'
    if selection and Path(name).stem!=selection: return
    scene.render.filepath=str(OUT/name); bpy.ops.file.pack_all(); bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'XmasLights-model-review.blend'))
    bpy.ops.render.render(write_still=True)

if '--stairs-only' not in sys.argv:
    door(); render('01-classic-door.png'); clear()
    cube('dark background',(0,.8,2),(6,.2,5),panel)
    world.node_tree.nodes['Background'].inputs['Strength'].default_value=.4
    for z,variant,colors in [(3.6,'classic',(0xFFD38A,)),(2.1,'snowy',(0xFFE6BA,)),(.6,'rgb',(0xFF4141,0x60E47C,0xFFD557,0x609BFF))]:
        chain((-2,-.1,z),(2,-.1,z),variant,colors,.04)
    light('Studio key',(-3,-4,7),0xFFFFFF,900,5); light('Studio fill',(4,-1,5),0xD6E5FF,450,4)
    camera((.8,-9,4.4),(0,0,2.1),5.3)
    caption('XmasLights · Clásica / Nevada / Multicolor')
    render('02-model-closeups.png'); clear()
    door(True); render('03-rgb-night.png'); clear()

ivory=plain('painted balusters',0xE0E2DC)
cube('staircase wall',(0,1.1,2.6),(7,.35,5.2),ivory)
for i in range(7):
    height=(i+1)*.32; x=-2.25+i*.65
    cube('oak stair',(x,.25,height/2),(.64,1.7,height),wood[i%4])
    cube('tread',(x,.20,height+.035),(.69,1.85,.07),wood[2])
    rail_z=height+1.15
    cube('painted baluster',(x,-.5,height+.55),(.085,.085,1.1),ivory)
for x,z in [(-2.55,1.25),(1.95,3.49)]: cube('newel post',(x,-.5,z/2),(.17,.17,z),ivory)
def bar(a,b,width,mat):
    delta=Vector(b)-Vector(a); obj=cube('oak handrail',(Vector(a)+Vector(b))*.5,(width,width,delta.length),mat)
    obj.rotation_euler=delta.to_track_quat('Z','Y').to_euler()
bar((-2.55,-.5,1.45),(1.95,-.5,3.62),.14,wood[2])
chain((-2.55,-.72,1.42),(1.95,-.72,3.59),'classic',(0xFFD38A,),.20)
chain((1.95,-.72,3.59),(1.95,-.72,.15),'classic',(0xFFD38A,),.20)
for x in range(-6,7): cube('oak floorboard',(x*.55,-.8,-.12),(.54,5,.16),wood[x%4])
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.42
light('Daylight',(-3,-4,7),0xF2FAFF,850,5); light('Warm interior',(4,-2,6),0xFFE3BF,360,4)
camera((5.5,-10,6.1),(-.1,0,1.8),7.1)
caption('XmasLights · Abeto con caída · Luces enrolladas 360°')
render('04-draped-stairs.png')
clear()
cube('studio backdrop',(0,.8,2),(7,.2,5),panel)
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.4
chain((-2.5,0,3.4),(2.5,0,3.4),'classic',(0xFFD38A,),.08)
chain((-2.5,0,1.4),(2.5,0,1.4),'classic',(0xFFD38A,),.08,back=True)
light('Studio key',(-3,-4,7),0xFFFFFF,900,5); light('Studio fill',(4,-1,5),0xD6E5FF,450,4)
camera((.6,-10,5.3),(0,0,2.15),6.5)
caption('XmasLights · Frente arriba / Reverso abajo · Mismo modelo')
render('05-front-and-back.png'); clear()
cube('studio backdrop',(0,.8,2),(7,.2,5),panel)
for i,color in enumerate([0xFF3030,0x38E658,0x2878FF,0xFFE12B,0xFF871F]):
    model('colored_bulb',(-2.2+i*1.1,0,2.7),Vector((1,0,0)),.8,size=.8,tint=color)
for i in range(15):
    model('colored_bulb',(-2.5+i*.36,0,1.1-.18*math.sin(i/14*math.pi)),Vector((1,0,0)),.23,size=.23,tint=[0xFF3030,0x38E658,0xFFD64D][i%3])
world.node_tree.nodes['Background'].inputs['Strength'].default_value=.45
light('Studio key',(-3,-4,7),0xFFFFFF,750,5); light('Studio fill',(4,-1,5),0xFFFFFF,350,4)
camera((.5,-10,4.5),(0,0,2),6.2)
caption('XmasLights · Rojo / Verde / Azul / Amarillo / Naranja')
render('06-colored-bulbs.png')
print('PREVIEWS COMPLETE:',OUT)
