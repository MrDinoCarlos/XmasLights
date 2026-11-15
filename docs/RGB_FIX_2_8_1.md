# Corrección RGB e inline de XmasLights 2.8.1

El log del servidor de pruebas muestra XmasLights 2.8.0 con StrobeLights 0.10.37
en el arranque del 10 de noviembre a las 22:38. XmasLights rechazaba ese host,
que aún no tiene la extensión de transporte estable de 0.10.38, y activaba su
alternativa vanilla. El aviso decía explícitamente que se mantenía esa luz.
Por eso los modelos y sus colores se veían, pero el entorno recibía LIGHT blanco.

El rechazo del protocolo también se ha confirmado con el depurador de IntelliJ
en una prueba aislada: `resolve` devuelve `false` al recibir un host sin la
extensión estable. La prueba no se conecta al servidor que está jugando el usuario.

## Comportamiento corregido

- Con StrobeLights activo y la integración habilitada, XmasLights nunca añade
  LIGHT blanco, aunque falte el protocolo, el pack aceptado o el perfil Fabulous.
  También retira sus LIGHT antiguos en los chunks cargados. Los pendientes de
  limpieza en zonas descargadas se conservan hasta que vuelvan a cargarse.
- Si el RGB no puede arrancar, se registra la causa, traducible a los cinco idiomas.
  No se sustituye silenciosamente por iluminación vanilla. La selección del render
  no depende del estado de descarga de cada jugador.
- Sin el plugin StrobeLights activo, o al desactivar expresamente la integración,
  `light-blocks` conserva su alternativa vanilla configurable.
- Se mantiene el brillo suave del abeto en 5/15 y el brillo de sus LED. El ajuste
  del modelo no coloca bloques de luz ni aumenta las entidades.
- Las bombillas y cubos `wall-inline-*` tienen modelos propios sin casquillo
  superior. Su cuerpo luminoso y el origen RGB se centran en el cable. Las
  versiones con cuelgue conservan sus casquillos. No cambia el coste de entidades.

## Actualización necesaria

Usa **XmasLights 2.8.1 y StrobeLights 0.10.38**, ambos para Paper 1.21.4.
Si TRP está instalado, necesita 0.4.19 para su shader combinado. Mantén Fabulous
y los shaderpacks externos desactivados en OptiFine, y acepta el pack actualizado.
La configuración pasa a v10 y los idiomas a v8 con copia previa, conservando
valores y textos personalizados. Los datos de luces permanecen en v4.

Los JAR y proyectos editables están en `dist/`. Paper admite preparar ambos JAR
en `plugins/update/` para aplicarlos en el siguiente reinicio, según su
[documentación de actualización](https://docs.papermc.io/paper/updating/).
No hace falta modificar los datos del mundo ni eliminar luces colocadas.

## Validación

171 pruebas automatizadas cubren el host 0.10.37 detectado, el rechazo de otro
host, el shader combinado no disponible, la recuperación tras recarga, los
LIGHT antiguos, su ausencia con el host correcto, el brillo del abeto y los
modelos inline sin casquillo. Se mantienen las 432 comprobaciones GLSL de GPU
del renderer 0.10.38: los shaders no han cambiado en esta revisión.

La [comparación de modelos](../previews/09-wall-inline-comparison.png) usa
geometría real con iluminación de estudio en Blender. La aceptación visual
final dentro de Minecraft requiere aplicar ambos JAR y el pack nuevo.
