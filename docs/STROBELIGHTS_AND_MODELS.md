# StrobeLights y modelos de XmasLights 2.8.6

Para OptiFine, usar StrobeLights **0.10.40** y aceptar su pack nuevo:
[causa y corrección del fallo de superficies](OPTIFINE_RGB_2_8_6.md).

XmasLights adapta los colores de sus bombillas al render RGB de StrobeLights. StrobeLights
debe estar instalado, activo y ser compatible con el servidor. El adaptador no crea
strobes: no usa su registro, editor, persistencia, efectos de cámara ni bloques del mundo.
Los portadores de render son ItemDisplay privados por espectador y no persistentes.

## Instalar en Paper 1.21.4

1. Instala `XmasLights-2.8.6.jar` con Java 21. Si quieres iluminación RGB de superficies,
   instala también **StrobeLights 0.10.40 para 1.21.4** y activa su pack.
   Si tienes TRPServerEdition, usa **0.4.19** para el shader combinado.
2. El pack de modelos se entrega automáticamente por Nexo, TRP o el alojamiento integrado.
   Hay 51 tipos: 17 originales, 17 de pared y 17 de pared sin ramal (`wall-inline-*`).
   Se conservan los IDs y los datos anteriores.
3. Acepta los packs en el cliente. Para el render RGB, selecciona gráficos **Fabulous**
   y desactiva los shaders externos de OptiFine que sustituyan esa composición.
   El perfil gráfico de TRP no controla las fuentes RGB de XmasLights.
4. Ejecuta `/xl give garland-classic 16`, coloca dos extremos y continúa el contorno.
   Para una barandilla usa `garland-draped`. `/xl types` muestra los identificadores.

El adaptador admite el formato de StrobeLights 0.10.38 y verifica el carrier
incluido en su JAR. La comprobación automatizada de esta entrega se ha realizado con
0.10.38 para 1.21.4, incluyendo sus GLSL reales en GPU y el shader combinado de TRP 0.4.19.
El JAR y el pack de modelos de XmasLights no incluyen los shaders de StrobeLights.
La validación del resultado visual con ambos plugins en Minecraft está pendiente.

## Entregar el pack de modelos

La prioridad automática es **Nexo → TRP → URL configurada → alojamiento integrado**.
`model-pack.auto-send: true` envía el pack al entrar y tras cambiar de proveedor. `/xl pack`
permite solicitarlo de nuevo. El cliente conserva su decisión de aceptar la descarga;
`required` configura la obligatoriedad del envío propio. Nexo y TRP conservan sus políticas.

**Con Nexo:** no necesitas TRP ni copiar archivos a mano. XmasLights crea/actualiza solamente
`plugins/Nexo/pack/external_packs/XmasLights-Models.zip`. El nombre es estable al actualizar.
Se prepara en `onLoad` para que esté disponible antes de `onEnable` de Nexo. No se llama a
su API de ejecución durante esa preparación. Los recursos van a su pack principal mediante
la [entrada documentada de packs externos](https://docs.nexomc.com/configuration/resourcepack).
No se cambian sus ajustes ni se registra además otra copia de los modelos a través de TRP.

Si se instala o cambia el ZIP con Nexo activo, se espera a que termine su generación actual,
se pide una regeneración enfocada del pack y se espera a su publicación. Las solicitudes
de los jugadores quedan pendientes hasta entonces. Se usa la API pública de generación y
envío; `/nexo reload pack` es la alternativa cuando falta la API de regeneración. Una recarga
de XmasLights no reescribe ni regenera entradas idénticas. Si desactivas
`model-pack.nexo.auto-rebuild`, debes ejecutar `/nexo reload pack` o reiniciar para aplicar
una entrada nueva. La integración directa puede desactivarse con `model-pack.nexo.enabled`.

**Con TRP y sin Nexo seleccionado:** deja `model-pack.trp-overlay: true`. XmasLights
registra un overlay de recursos en el pack compartido mediante la API pública de TRP.

**Sin proveedor compartido:** XmasLights sirve el ZIP automáticamente desde memoria y lo
añade a los packs del cliente, sin sustituir el pack de StrobeLights. Solo expone una URL
del ZIP, con hash en la ruta; no expone carpetas ni archivos de configuración. El puerto
debe estar asignado/abierto y accesible para los jugadores:

```yaml
model-pack:
  auto-send: true
  embedded:
    enabled: true
    bind-address: "0.0.0.0"
    port: 8164
    public-url: ""
```

Con `public-url` vacío se usa el hostname que el jugador utilizó al entrar, con ese puerto.
En una conexión local será la dirección local. Tras un proxy, NAT o panel de hosting,
configura una dirección pública alcanzable, por ejemplo `http://tu-dominio.example:8164`.
Es una URL **base**: el plugin añade la ruta del ZIP. Para un proxy HTTPS, apunta la URL base
al proxy que reenvía al listener. Si no hay hostname de entrada ni IP configurada en el
servidor, la alternativa automática es `127.0.0.1`, válida solo para clientes locales.
No se detectan ni abren puertos del router automáticamente.

Con Nexo/TRP seleccionados, o con una URL externa, no se abre el alojamiento integrado.
Desactivarlo con `model-pack.embedded.enabled: false` permite usar exclusivamente otro
sistema. Si cambias un puerto o dirección, ejecuta `/xl reload`.

**Alojamiento externo opcional:** se sigue exportando el ZIP a
`plugins/XmasLights/resource-pack/XmasLights-Models-1.21.4.zip`. Puedes publicarlo por tu
cuenta y configurar su URL completa de descarga:

```yaml
model-pack:
  trp-overlay: false
  url: "https://tu-dominio.example/packs/XmasLights-Models-1.21.4.zip"
  auto-send: true
  assume-installed: false
```

Ejecuta `/xl reload` y `/xl pack`. El ZIP se añade a la pila de packs del cliente mediante
`addResourcePack`, con SHA-1. Al usar esta URL no se abre el alojamiento integrado. Usa el ZIP exportado
correspondiente al JAR instalado para que coincida su huella. La URL de ejemplo debe
sustituirse por un alojamiento real. Con `auto-send: false` no se realizan solicitudes
automáticas de XmasLights; los proveedores externos conservan sus propios envíos.

**Instalación manual o gestor externo:** incluye los recursos `assets/xmaslights` en el
pack final, o instala el ZIP como pack local en cada cliente. Solo cuando hayas verificado
que todos lo utilizan, activa `model-pack.assume-installed: true`. Minecraft no comunica
al servidor qué packs locales ha instalado un usuario. Este ajuste declara que tú has
comprobado la instalación; no descarga ni verifica el pack.

Sin esa declaración, los modelos solo se muestran tras una carga correcta reportada al
servidor. Rechazar o descartar el pack los oculta. La misma comprobación protege su colocación.
El pack de modelos solo contiene definiciones y texturas bajo `xmaslights`: no reemplaza
objetos vanilla, shaders ni recursos de StrobeLights. Su formato actual es el de 1.21.4.

Las pruebas de Nexo usan un sustituto de su API pública y de su generación. La aceptación
con el JAR real de Nexo y un cliente Minecraft sigue pendiente; anota versión, configuración
de ofuscación y proveedor de alojamiento al comprobarla.

## Modo vanilla, colores y apoyo en paredes

El pack permanece activado por defecto. `model-pack.enabled: false` selecciona un renderer
vanilla global y detiene su entrega: hojas y pequeñas luces hechas con BlockDisplay,
con los mismos dos displays por módulo. Sus colores son los de los materiales de Minecraft;
el modo con pack utiliza tinte RGB en una textura blanca. No se generan dos versiones
simultáneas por jugador. Cambiar el modo reconstruye gradualmente las entidades existentes,
conservando las cadenas y grupos. `/xl pack` explica que no hace falta el pack en modo vanilla.

Los tipos integrados red/green/blue/yellow/orange y los Christmas anteriores dejan de usar
las cabezas con dibujos. Se reconocen y actualizan las definiciones originales; las cabezas
personalizadas se respetan. Los kits originales de cabezas también se actualizan al entrar
o recargar, conservando cantidades. Las luces ya colocadas se reconstruyen tras la actualización.

Los módulos de abeto muestran adornos delante y detrás y ocho LED en hélice. Con apoyo
opaco completo en una cara, se usa una variante que omite la decoración y los LED ocultos.
El número de entidades permanece igual; se reduce la geometría del cliente. Cristal,
hojas, huecos o chunks descargados conservan la versión completa. Roturas, colocaciones,
explosiones, pistones y carga de entidades actualizan la caché por una cola gradual.
Para cambios de mundo que omitan los eventos de Bukkit, usa `/xl regenerate all`.

`garlands.sag: 0.16` da caída también a los contornos de abeto; los tramos verticales
siguen rectos. `sag` por tipo permite sobrescribirla, por ejemplo 0.20 en garland-draped.
Los contornos normales usan `sag` (0.08 por defecto) y ahora comparten la curva
con su cable y los cuelgues. Un tipo con `sag: 0` sigue siendo recto. `chain` es la
distancia vertical entre el cable y el casquillo, también para tipos de pared.
Los cubos compactos usan `model: cube`; las bombillas redondeadas, `model: bulb`.
Ambos contienen el casquillo dentro del mismo modelo. Los farolillos usan su cadena vanilla.

## Impactos RGB estables sobre bloques

El transporte nuevo requiere StrobeLights 0.10.38 y su pack actualizado. Con TRP,
se necesita también TRPServerEdition 0.4.19. Se mantiene el shader de StrobeLights y
el render de haces de TRP; los modelos de XmasLights no incluyen shaders de Minecraft.

Cada emisor recibe un canal propio entre 128 posibles por espectador. Las señales
no se pisan al comprimirse la perspectiva. La posición XYZ y el FOV se codifican de
forma independiente; el paquete se valida antes de usarlo. También funciona cuando
otros modelos ocupan la capa de items. La composición retira los pequeños paquetes
técnicos del borde superior de la imagen. No se crean strobes, tokens de sombra ni
entidades auxiliares por canal.

```yaml
strobelights:
  max-sources-per-player: 32
  max-carriers-total: 128
  view-distance: 192.0
  selection-hysteresis: 6.0
  intensity-scale: 0.55
  surfaces:
    reach: 8.0
    max-radius: 2.25
    distance-fade: 8.0
    follow-effects: false
    refresh-ticks: 20
    probes-per-update: 16
```

El radio es independiente de la intensidad. La distancia de la bombilla a la superficie
aplica `1 / (1 + (distancia / distance-fade)^2)` a la energía y su raíz al radio máximo.
El radio se cuantiza hacia abajo en 16 bandas entre 0.25 y 8 bloques. `expansion`
permanece como dato de compatibilidad, pero el transporte estable usa directamente
`surfaces.max-radius`. La composición de estas fuentes usa una caída radial cuadrática
y menos ganancia; las luces nativas de StrobeLights conservan su comportamiento.

La distancia del jugador no modifica la energía. `view-distance` limita la selección
de fuentes (4..512) y mantiene ocho bloques de margen para las existentes. Fuera del
rango o con la zona descargada dejan de enviarse; no se fuerzan cargas de chunks.
La caché conserva hasta 1024 impactos, con 16 sondeos por actualización y caducidad
escalonada de 20..39 ticks. Los cambios de fuentes reutilizan entidades y canales.

El presupuesto sigue siendo 4096 displays en total, incluidos hasta 128 portadores RGB.
`follow-effects: true` hace que el impacto siga el modo; apagar el tramo o night-only apaga
ambas capas. Mientras StrobeLights y su integración estén habilitados no hay LIGHT blancos,
incluso si se rechaza la versión o el pack. La alternativa vanilla sigue controlada por
`light-blocks` cuando el host o la integración están desactivados.

Los clientes deben aceptar el pack actualizado y usar Fabulous. OptiFine se utiliza
con shaderpacks externos desactivados; un shader que sustituya la composición Fabulous
necesita una integración propia. El servidor no puede conocer ni forzar esa opción.

Los tipos `wall-*` son rectos; `wall-inline-*` sitúa el centro de la bombilla en el cable
sin ramal. Los datos antiguos de `straight` siguen cargándose. El abeto conserva su
mínimo visual configurable de 5/15, sin LIGHT ni displays nuevos.

Consulta [las pruebas con GLSL real](STABLE_RGB.md) y [el enlace con DecoSwap](DECOSWAP.md).

## Editar modelos y reproducir las imágenes

Los recursos editables están en `model-pack/assets/xmaslights/`. Cada módulo usa dos
entidades: el abeto/adornos con sombreado normal y sus ocho LED con brillo/color animado.
No hay una entidad por hoja ni por adorno. `tools/build_models.py` genera los JSON,
texturas y ZIP a partir de un diseño procedural original y requiere Python con Pillow.
Si editas a mano los JSON o PNG, empaqueta `model-pack/` como ZIP en lugar de ejecutar
el generador, porque el generador vuelve a crear esos archivos.

```powershell
python tools/build_models.py
blender --background --python tools/render_previews.py
```

El primer comando actualiza también el ZIP integrado en `src/main/resources/packs/`;
después hay que recompilar el plugin. El segundo usa Blender 5.2 para renderizar los
modelos generados y guarda una escena editable con texturas incluidas.
Consulta [la galería](../previews/README.md) y [las pruebas en Paper](TESTING.md).

Para preparar los archivos de entrega tras `clean verify`, usa
`python tools/package_release.py`. No requiere StrobeLights instalado para compilar;
la dependencia es opcional en tiempo de ejecución. La comprobación adicional de su JAR
puede ejecutarse con `-Dstrobelights.test.jar=<ruta-al-jar-1.21.4>` en Maven.
