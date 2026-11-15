# Corrección de superficies con OptiFine — XmasLights 2.8.6

La dependencia para el RGB sigue siendo **StrobeLights**, actualizado a **0.10.40
para Paper 1.21.4**. XmasLights 2.8.6 admite esa versión sin activar
`allow-untested-version`. Se mantienen Fabulous y `Shaders: OFF`.

## Causa reproducida

El carrier estable identificaba su textura por RGBA `(37,211,83,5)`. OptiFine
1.21.4 HD U J3 prepara el atlas mediante `TextureUtils.scaleImage` y Java2D
SRC_OVER. Con alfa 5, el redondeo de la premultiplicación produce
`(51,204,102,5)`, incluso al usar interpolación nearest-neighbour. La ruta nativa
de StrobeLights ya admitía esa firma; la extensión de fuentes estables usada por
XmasLights no. Rechazaba la fuente antes de enviarla al pase de iluminación.

Se comprobó la operación en el OptiFine instalado y se reprodujo con Java2D,
a tamaños 16, 32, 64 y 128. Con la textura resultante, el GLSL de StrobeLights
0.10.39 detecta **0 de 32 fuentes** en la primera escena. No era suficiente
probar únicamente un texel sintético con el color original.

La corrección admite las dos firmas exactas. Sigue comprobando alfa, etiqueta de
fuente y proyección de cámara. No amplía arbitrariamente la tolerancia de color.
El shader corregido pertenece a StrobeLights; el pack de modelos de XmasLights
continúa sin shaders.

## TRP

XmasLights no consulta las preferencias gráficas ni la capacidad agregada de TRP.
Se prueba también el pack combinado creado por el merger de producción de
**TRP 0.4.19**, que incorpora la extensión corregida de StrobeLights. Si TRP o Nexo
distribuyen el pack, deben regenerarlo para que llegue el shader nuevo al cliente.
No se puede garantizar la combinación con una versión de TRP que no conserve esa
extensión o con otro pack que sustituya sus shaders.

## Reproducir y actualizar

`tools/PrepareOptifineCarrier.java` prepara el PNG con la operación de Java2D.
`tools/verify_strobe_gpu.py --carrier-texture <PNG>` ejecuta el GLSL real con ese
PNG; sin el argumento utiliza la textura del JAR, en vez del antiguo texel
sintético. Requiere Python con numpy, moderngl y Pillow, y Java 21 para el preparador.
Usar `--pipeline --occupied` comprueba transporte, agregación, iluminación y
composición sobre una capa de objetos ya ocupada.

Instalar ambos JAR, reiniciar Paper y aceptar el pack nuevo de StrobeLights.
Actualizar solamente XmasLights no corrige el shader antiguo. Los segmentos y
la configuración se conservan; no es necesario recolocar las luces.

La validación GPU reproduce el procesamiento relevante de OptiFine, pero no
sustituye una aceptación visual dentro del cliente. Comprobar suelo y paredes,
movimiento de cámara, OFF/ON y efectos con Fabulous y `Shaders: OFF`.
