"""Render actual plugin display transforms exported by LightDesignReviewTest.

Blender --background --python tools/render_catalog.py -- --minecraft-jar=<client.jar>
Uses the project's real item JSONs and the local 1.21.4 client's native block models.
No Minecraft files are added to the plugin/resource pack. Lighting is a studio simulation.
"""
from pathlib import Path
import sys, zipfile, json, math

ROOT=Path(__file__).resolve().parents[1]
# Reuse only the mesh/material/scene helpers, without generating the older scenes.
source=(ROOT/'tools/render_previews.py').read_text(encoding='utf-8')
exec(compile(source.split("if '--stairs-only' not in sys.argv:")[0],str(ROOT/'tools/render_previews.py'),'exec'),globals())
from mathutils import Matrix, Quaternion, Vector

def option(name,default):
    return next((arg.split('=',1)[1] for arg in sys.argv if arg.startswith('--'+name+'=')),default)

manifest=Path(option('manifest',str(ROOT/'previews/design-manifest.json')))
client=Path(option('minecraft-jar',str(Path.home()/'AppData/Roaming/.minecraft/versions/1.21.4/1.21.4.jar')))
if not client.is_file(): raise FileNotFoundError('Supply the local 1.21.4 client with --minecraft-jar=<path>')
archive=zipfile.ZipFile(client)
mcroot=ROOT/'.tools/minecraft-preview/assets/minecraft'; TEXTURE_ROOTS['minecraft']=mcroot
native_cache={}

def qualified(name): return name if ':' in name else 'minecraft:'+name
def native_model(name):
    name=qualified(name)
    if name in native_cache: return native_cache[name]
    namespace,path=name.split(':',1)
    raw=json.loads(archive.read(f'assets/{namespace}/models/{path}.json'))
    parent=native_model(raw['parent']) if 'parent' in raw and not raw['parent'].startswith('builtin/') else {}
    merged={**parent,**raw,'textures':{**parent.get('textures',{}),**raw.get('textures',{})}}
    native_cache[name]=merged; return merged

def block_mesh(state):
    ident=state.split('[',1)[0]; namespace,ident=qualified(ident).split(':',1)
    name=ident
    if ident in ['lantern','soul_lantern'] and 'hanging=true' in state: name+='_hanging'
    definition=native_model('block/'+name)
    textures=dict(definition.get('textures',{}))
    for key,value in textures.items():
        for _ in range(16):
            if not value.startswith('#'): break
            value=textures[value[1:]]
        value=qualified(value); textures[key]=value
        ns,path=value.split(':',1); target=mcroot/('textures/'+path+'.png')
        if not target.exists():
            target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(archive.read(f'assets/{ns}/textures/{path}.png'))
    definition={**definition,'textures':textures}
    return model_mesh('minecraft:'+name,definition=definition,block=True)

convert=Matrix(((1,0,0),(0,0,-1),(0,1,0)))
catalog=json.loads(manifest.read_text(encoding='utf-8'))
def instance(value,offset):
    if 'model' in value:
        item=value['model'].split(':',1)[1]
        definition=json.loads((ASSETS/'items'/f'{item}.json').read_text(encoding='utf-8'))['model']
        name=definition['model'].removeprefix('xmaslights:item/')
        colours=tuple(value['colours']) or None
        mesh=model_mesh(name,colours)
    else: mesh=block_mesh(value['block'])
    obj=bpy.data.objects.new(value.get('model',value.get('block')),mesh); scene.collection.objects.link(obj); dynamic.append(obj)
    p=Vector(value['position'])+Vector(value['translation']); obj.location=convert@p+Vector(offset)
    x,y,z,w=value['rotation']; q=Quaternion((w,x,y,z))
    obj.rotation_mode='QUATERNION'; obj.rotation_quaternion=(convert@q.to_matrix()@convert.transposed()).to_quaternion()
    sx,sy,sz=value['scale']; obj.scale=(sx,sz,sy)
    if 'matrix' in value:
        # JOML exports column-major matrices; retain both rotations exactly.
        raw=value['matrix']; matrix=Matrix(tuple(tuple(raw[col*4+row] for col in range(4)) for row in range(4)))
        if 'model' in value:
            # ItemDisplayRenderer applies this after the supplied transformation,
            # even with transform NONE. Match Minecraft rather than omitting it.
            matrix=matrix@Matrix.Rotation(math.pi,4,'Y')
        origin=convert@Vector(value['position'])+Vector(offset)
        obj.matrix_world=Matrix.Translation(origin)@convert.to_4x4()@matrix@convert.transposed().to_4x4()
    return obj

def label(text,location,size=.22):
    data=bpy.data.curves.new('type label','FONT'); data.body=text; data.size=size
    obj=bpy.data.objects.new('type label',data); scene.collection.objects.link(obj); dynamic.append(obj)
    obj.location=location; obj.rotation_euler=(math.pi/2,0,0); obj.data.materials.append(plain('label '+text,0xE5EBDD))

def studio():
    world.node_tree.nodes['Background'].inputs['Strength'].default_value=.55
    light('Large softbox',(-7,-10,22),0xF0F6FF,2200,12)
    light('Soft fill',(12,-6,13),0xFFFFFF,1200,10)

scene.cycles.samples=32
rows=math.ceil(len(catalog)/4)
scene.render.resolution_x=2800; scene.render.resolution_y=2800
for i,entry in enumerate(catalog):
    column=i%4; row=i//4; offset=(-14.8+column*7.8,0,1.8+(rows-1-row)*2.5)
    for entity in entry['entities']: instance(entity,offset)
    label(entry['id'],(offset[0],-.12,offset[2]+.44),.25)
    label(str(len(entry['entities']))+' displays',(offset[0]+4.5,-.12,offset[2]+.44),.18)
center=1.8+(rows-1)*1.25
studio(); camera((.1,-48,center+7),(.1,0,center),max(32.5,rows*2.5+4))
caption(f'XmasLights 2.8 · {len(catalog)} tipos · Wall, wall-inline y diseños con caída',font_scale=2)
scene.render.filepath=str(OUT/'08-all-light-types.png'); bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'XmasLights-2.8-catalog.blend')); bpy.ops.render.render(write_still=True)
clear()

scene.render.resolution_x=1800; scene.render.resolution_y=1500
for ident,height in [('rainbow',5.5),('christmas',3.5),('wall-rainbow',1.5)]:
    entry=next(entry for entry in catalog if entry['id']==ident)
    for entity in entry['entities']: instance(entity,(-3,0,height))
    label(ident,(-3,-.12,height+.4),.25)
studio(); camera((3,-14,8.6),(0,0,3),9)
caption('XmasLights 2.8 · Cubos compactos, casquillos y cable wall recto')
scene.render.filepath=str(OUT/'07-cables-and-cubes.png'); bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'XmasLights-2.8-cables.blend')); bpy.ops.render.render(write_still=True)
clear()
scene.render.resolution_x=2100; scene.render.resolution_y=1200
for row,base in enumerate(['warm','christmas','lanterns','garland-rgb']):
    for column,ident in enumerate(['wall-'+base,'wall-inline-'+base]):
        entry=next(entry for entry in catalog if entry['id']==ident)
        offset=(-7.5+column*8,0,8-row*2)
        for entity in entry['entities']: instance(entity,offset)
        label(ident,(offset[0],-.12,offset[2]+.45),.23)
        label(str(len(entry['entities']))+' displays',(offset[0]+4.6,-.12,offset[2]+.45),.17)
studio();camera((.0,-24,11),(0,0,5),17)
caption('XmasLights 2.8.1 · Con cuelgue / Inline sin casquillo')
scene.render.filepath=str(OUT/'09-wall-inline-comparison.png');bpy.ops.file.pack_all()
bpy.ops.wm.save_as_mainfile(filepath=str(OUT/'XmasLights-2.8-inline.blend'));bpy.ops.render.render(write_still=True)
archive.close()
print('CATALOG COMPLETE:',OUT)
