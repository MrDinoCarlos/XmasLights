"""Build editable Minecraft models and the additive 1.21.4 model pack. Requires Pillow.

The render tool consumes these exact generated JSON elements and PNGs; no AI concept art.
"""
import json, math, random, zipfile, xml.etree.ElementTree as ET
from pathlib import Path
from copy import deepcopy
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / 'model-pack'
ASSETS = PACK / 'assets/xmaslights'
TEXTURES = ASSETS / 'textures/item'
MODELS = ASSETS / 'models/item'
for folder in [TEXTURES, MODELS]: folder.mkdir(parents=True, exist_ok=True)
VERSION=ET.parse(ROOT/'pom.xml').findtext('{http://maven.apache.org/POM/4.0.0}version')

def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2)+'\n',encoding='utf-8')

random.seed(191225)
for snowy in [False, True]:
    image=Image.new('RGBA',(64,64),(0,0,0,0)); draw=ImageDraw.Draw(image)
    # Original pine-needle cutout: dense tapered side shoots, irregular pixelated tips.
    draw.line([(2,34),(61,30)],fill=(66,54,25,255),width=3)
    for x in range(4,62,2):
        taper=math.sin(math.pi*(x-2)/64)**.5
        for sign in [-1,1]:
            length=int((15+random.randrange(10))*taper)
            tint=random.choice([(25,67,25,255),(39,86,30,255),(54,101,37,255),(76,119,43,255)])
            end=(min(63,x+random.randrange(6,13)),32+sign*length)
            draw.line([(x,32),end],fill=tint,width=random.choice([2,3]))
            if snowy and random.random()<.58:
                draw.line([(end[0]-3,end[1]-sign*3),end],fill=(211,226,213,255),width=2)
    image.save(TEXTURES/('pine_snow.png' if snowy else 'pine.png'))

bow=Image.new('RGBA',(64,64),(0,0,0,0)); draw=ImageDraw.Draw(bow)
for points in [[(31,23),(9,8),(3,28),(9,43),(31,34)],[(33,23),(55,8),(61,28),(55,43),(33,34)]]:
    draw.polygon(points,fill=(159,17,27,255)); draw.line(points+[points[0]],fill=(211,39,47,255),width=3)
draw.ellipse((11,18,24,32),fill=(0,0,0,0)); draw.ellipse((40,18,53,32),fill=(0,0,0,0))
draw.polygon([(26,32),(33,35),(24,61),(20,52),(13,55)],fill=(164,19,27,255))
draw.polygon([(38,32),(31,35),(40,61),(44,52),(51,55)],fill=(185,23,33,255))
bow.save(TEXTURES/'bow.png')
wire=Image.new('RGBA',(64,64),(0,0,0,0)); draw=ImageDraw.Draw(wire)
draw.line([(0,63),(63,0)],fill=(31,53,25,255),width=5)
draw.line([(0,62),(62,0)],fill=(63,81,37,255),width=2)
wire.save(TEXTURES/'wrapped_wire.png')

colors={'stem':(40,55,23),'red':(175,21,29),'gold':(199,143,35),'blue':(45,96,139),
        'ivory':(219,224,202),'ribbon':(144,15,26),'cone':(95,58,27),'socket':(45,43,37),'led':(255,255,255)}
for name,color in colors.items():
    img=Image.new('RGBA',(16,16)); pixels=img.load()
    for y in range(16):
        for x in range(16):
            noise=random.randrange(-12,13) if name!='led' else 0
            pixels[x,y]=tuple(max(0,min(255,c+noise)) for c in color)+(255,)
    img.save(TEXTURES/(name+'.png'))

def cube(name,low,high,texture,rotation=None,tint=False):
    faces={direction:{'uv':[0,0,16,16],'texture':'#'+texture,**({'tintindex':0} if tint else {})}
           for direction in ['north','south','east','west','up','down']}
    result={'name':name,'from':low,'to':high,'faces':faces}
    if rotation: result['rotation']=rotation
    return result

def body(variant):
    elements=[cube('woven_core',[0,7.45,7.45],[16,8.55,8.55],'stem')]
    for i,x in enumerate([2,6,10,14]):
        for j,(vertical,angle) in enumerate([(False,0),(False,45),(False,-45),(True,0)]):
            low=[x-3.8,4.2,8] if vertical else [x-3.8,8,4.2]
            high=[x+3.8,11.8,8] if vertical else [x+3.8,8,11.8]
            directions=['north','south'] if vertical else ['up','down']
            element={'name':f'radial_pine_{i}_{j}','from':low,'to':high,
                     'faces':{direction:{'uv':[0,0,16,16],'texture':'#pine'} for direction in directions}}
            if angle: element['rotation']={'axis':'x','angle':angle,'origin':[x,8,8],'rescale':False}
            elements.append(element)
    # Full front/back ornament sets, staggered instead of a uniform front-facing row.
    for side in [-1,1]:
        x,y,z=(4.0,7.2,8+side*3.25) if side>0 else (11.7,7.0,8+side*3.25)
        color=('ivory' if side>0 else 'blue') if variant=='snowy' else ('red' if side>0 else 'gold')
        for row,(half,height) in enumerate([(.68,.55),(1.0,.65),(.68,.55)]):
            cy=y+(row-1)*.57
            element=cube(f'bauble_{side}_{row}',[x-half,cy-height/2,z-half],[x+half,cy+height/2,z+half],color)
            # The broad middle row has visible ledges. Removing those faces
            # leaves holes when viewed from above/below; only inner faces go.
            if row==2: element['faces'].pop('down')
            if row==0: element['faces'].pop('up')
            elements.append(element)
        elements.append(cube(f'cap_{side}',[x-.20,y+.9,z-.20],[x+.20,y+1.16,z+.20],'gold'))
        cx=11.7 if side>0 else 4.0
        for j in range(2):
            size=.62-j*.14
            elements.append(cube(f'pinecone_{side}_{j}',[cx-size,6.7+j*.65,8+side*2.9-size],[cx+size,7.3+j*.65,8+side*2.9+size],'cone'))
        bx=7.5 if side>0 else 8.7; bz=8+side*3.3
        elements.append({'name':f'ribbon_loops_{side}','from':[bx-1.8,7.5,bz],'to':[bx+1.8,10.2,bz],
                         'faces':{direction:{'uv':[0,0,16,16],'texture':'#bow'} for direction in ['north','south']}})
        elements.append(cube(f'ribbon_knot_{side}',[bx-.24,8.7,bz-.18],[bx+.24,9.18,bz+.18],'ribbon'))
    # Eight diagonal cutout panels connect around the full circumference into a helix.
    radius=4.0; chord=2*radius*math.sin(math.pi/8)
    for i in range(8):
        theta=(i+.5)*math.pi/4; cy=8+radius*math.cos(theta); cz=8+radius*math.sin(theta); cx=(i+.5)*2
        vertical=i%4 in [1,2]; angle=(-22.5 if i%4==1 else 22.5) if vertical else (22.5 if i%4==0 else -22.5)
        low=[i*2,cy-chord/2,cz] if vertical else [i*2,cy,cz-chord/2]
        high=[(i+1)*2,cy+chord/2,cz] if vertical else [(i+1)*2,cy,cz+chord/2]
        tangent=(math.cos(math.radians(angle)),math.sin(math.radians(angle))) if vertical else (-math.sin(math.radians(angle)),math.cos(math.radians(angle)))
        forward=tangent[0]*-math.sin(theta)+tangent[1]*math.cos(theta)>0
        uv=[0,0,16,16] if forward else [0,16,16,0]
        directions=['north','south'] if vertical else ['up','down']
        elements.append({'name':f'helix_wire_{i}','from':low,'to':high,
            'rotation':{'axis':'x','angle':angle,'origin':[cx,cy,cz],'rescale':False},
            'faces':{direction:{'uv':uv,'texture':'#wrapped_wire'} for direction in directions}})
    textures={key:'xmaslights:item/'+key for key in colors}
    textures.update(pine='xmaslights:item/'+('pine_snow' if variant=='snowy' else 'pine'),particle='xmaslights:item/pine',bow='xmaslights:item/bow',wrapped_wire='xmaslights:item/wrapped_wire')
    return {'credit':f'AstralicaStudio & MrDino | XmasLights {VERSION}','ambientocclusion':True,'gui_light':'side','textures':textures,'elements':elements}

def without_side(model,side):
    def visible(element):
        name=element['name']
        if any(name.startswith(f'{prefix}_{side}') for prefix in ['bauble','cap','pinecone','ribbon_loops','ribbon_knot']): return False
        if name.startswith(('helix_wire_','wrapped_led_')):
            z=(element['from'][2]+element['to'][2])/2
            return (z-8)*side<=0
        return True
    return {**model,'elements':[element for element in model['elements'] if visible(element)]}

for variant in ['classic','snowy','rgb']:
    full=body(variant)
    for suffix,model in [('',full),('_no_back',without_side(full,-1)),('_no_front',without_side(full,1))]:
        write_json(MODELS/f'garland_{variant}_body{suffix}.json',model)
leds=[]
for i in range(8):
    x=(i+.5)*2; theta=(i+.5)*math.pi/4; y=8+4.05*math.cos(theta); z=8+4.05*math.sin(theta)
    leds.append(cube('wrapped_led_'+str(i),[x-.24,y-.24,z-.24],[x+.24,y+.24,z+.24],'led',tint=True))
    for face in leds[-1]['faces'].values(): face['tintindex']=i
    leds[-1]['shade']=False
full_lights={'credit':'AstralicaStudio & MrDino','ambientocclusion':False,
           'textures':{'led':'xmaslights:item/led','particle':'xmaslights:item/led'},'elements':leds}
for suffix,model in [('',full_lights),('_no_back',without_side(full_lights,-1)),('_no_front',without_side(full_lights,1))]:
    write_json(MODELS/f'garland_lights{suffix}.json',model)
bulb=[]
for i,(half,low,high) in enumerate([(4.6,2.0,4.8),(6.0,4.8,9.2),(4.6,9.2,12.0)]):
    element=cube('tinted_bulb_'+str(i),[8-half,low,8-half],[8+half,high,8+half],'led',tint=True)
    element['shade']=False
    if i==2: element['faces'].pop('down')
    if i==0: element['faces'].pop('up')
    bulb.append(element)
bulb.append(cube('bulb_socket',[5.8,12,5.8],[10.2,14,10.2],'socket'))
bulb[-1]['faces'].pop('down')
write_json(MODELS/'colored_bulb.json',{'credit':'AstralicaStudio & MrDino','ambientocclusion':False,
           'textures':{'led':'xmaslights:item/led','socket':'xmaslights:item/socket','particle':'xmaslights:item/led'},'elements':bulb})

# Compact, opaque Minecraft-style lamps: a lit cube under a dark socket.
# A single item display contains the whole lamp; the cable drop is bounded separately.
diffuser=Image.new('RGBA',(16,16)); px=diffuser.load()
for y in range(16):
    for x in range(16):
        edge=min(x,y,15-x,15-y)
        value=222 if edge==0 else 242 if edge==1 else 255
        px[x,y]=(value,value,value,255)
diffuser.save(TEXTURES/'diffuser.png')
cube_lamp=[cube('luminous_cube',[1.5,1.5,1.5],[14.5,11.5,14.5],'diffuser',tint=True),
           cube('cube_socket_collar',[4.5,11.5,4.5],[11.5,13.2,11.5],'socket'),
           cube('cube_socket_neck',[6.2,13.2,6.2],[9.8,16,9.8],'socket')]
cube_lamp[0]['shade']=False
cube_lamp[1]['faces'].pop('down'); cube_lamp[2]['faces'].pop('down')
write_json(MODELS/'colored_cube.json',{'credit':'AstralicaStudio & MrDino','ambientocclusion':False,
           'textures':{'diffuser':'xmaslights:item/diffuser','socket':'xmaslights:item/socket','particle':'xmaslights:item/diffuser'},'elements':cube_lamp})

# Inline wall bulbs attach through their luminous body, without the pendant socket.
# Centre that body at model Y=8, keeping the cable and RGB origin aligned.
for shape,elements,shift in [('bulb',bulb,1.0),('cube',cube_lamp,1.5)]:
    inline=[deepcopy(element) for element in elements if 'socket' not in element['name']]
    for element in inline:
        element['from'][1]+=shift; element['to'][1]+=shift
    texture='led' if shape=='bulb' else 'diffuser'
    write_json(MODELS/f'colored_{shape}_inline.json',{
        'credit':'AstralicaStudio & MrDino','ambientocclusion':False,
        'textures':{texture:f'xmaslights:item/{texture}','particle':f'xmaslights:item/{texture}'},
        'elements':inline})
    write_json(ASSETS/f'items/{shape}_inline.json',{'model':{
        'type':'minecraft:model','model':f'xmaslights:item/colored_{shape}_inline',
        'tints':[{'type':'minecraft:custom_model_data','index':0,'default':0xFFFFFF}]}})

# Namespaced item definitions never replace vanilla/Nexo items or StrobeLights' carrier.
for obsolete in ['assets/minecraft/items/spruce_leaves.json','assets/minecraft/items/glowstone.json']:
    (PACK/obsolete).unlink(missing_ok=True)
for variant in ['classic','snowy','rgb']:
    for suffix in ['','_no_back','_no_front']:
        write_json(ASSETS/f'items/garland/{variant}/body{suffix}.json',{'model':{'type':'minecraft:model','model':f'xmaslights:item/garland_{variant}_body{suffix}'}})
for suffix in ['','_no_back','_no_front']:
    write_json(ASSETS/f'items/garland/lights{suffix}.json',{'model':{'type':'minecraft:model','model':'xmaslights:item/garland_lights'+suffix,
               'tints':[{'type':'minecraft:custom_model_data','index':i,'default':0xFFD38A} for i in range(8)]}})
write_json(ASSETS/'items/bulb.json',{'model':{'type':'minecraft:model','model':'xmaslights:item/colored_bulb',
           'tints':[{'type':'minecraft:custom_model_data','index':0,'default':0xFFFFFF}]}})
write_json(ASSETS/'items/cube.json',{'model':{'type':'minecraft:model','model':'xmaslights:item/colored_cube',
           'tints':[{'type':'minecraft:custom_model_data','index':0,'default':0xFFFFFF}]}})
write_json(PACK/'pack.mcmeta',{'pack':{'pack_format':46,'description':f'XmasLights {VERSION} | Garlands & colored bulbs'}})
with Image.open(ROOT/'tools/assets/pack-icon-source.png') as icon:
    icon.convert('RGB').resize((128,128),Image.Resampling.LANCZOS).save(PACK/'pack.png',optimize=True)
(PACK/'CREDITS.txt').write_text('Original procedural models and textures: AstralicaStudio & MrDino.\n'
                             'Pack icon: original artwork generated with the built-in image generation tool.\n'
                             'Additive assets only: no Minecraft or StrobeLights shaders are replaced.\n',encoding='utf-8')
destination=ROOT/'src/main/resources/packs/XmasLights-Models-1.21.4.zip'
destination.parent.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(PACK.rglob('*')):
        if path.is_file():
            info=zipfile.ZipInfo(path.relative_to(PACK).as_posix(),(2026,11,10,0,0,0))
            archive.writestr(info,path.read_bytes(),compress_type=zipfile.ZIP_DEFLATED)
print('Built model pack:',destination)
for variant in ['classic','snowy','rgb']:
    model=body(variant)
    print(variant, len(model['elements']),'elements',sum(len(e['faces']) for e in model['elements']),'faces per shaded body')
