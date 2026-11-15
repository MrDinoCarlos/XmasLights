# OFF y efectos RGB en XmasLights 2.8.2

OFF detiene los efectos de la decoración y su iluminación. Al volver a ON se
reanuda el modo que estuviera guardado. El RGB de StrobeLights sigue el modo
elegido por defecto, tanto en color como en intensidad.

El fallo se reprodujo con el depurador de IntelliJ en una prueba aislada:
`on=false`, `mode=CHASE`, `step=15`, variante anterior `2`, siguiente `3` y
`surfaceEffects=false`. El cálculo de CHASE seguía cambiando los objetos aunque
su brillo se redujera a OFF. La configuración anterior también mantenía el RGB
fijo por defecto, por lo que las bombillas animadas y sus impactos no coincidían.

- OFF conserva el último color, aplica el brillo apagado y deja la decoración
  en reposo. Los cambios posteriores de modo solo guardan la elección para ON.
- Se retiran los portadores RGB privados de la decoración apagada y deja de
  participar en el muestreo de impactos. Sus modelos visibles permanecen
  colocados; no se duplican ni se reconstruyen por encender o apagar.
- Los LIGHT vanilla propios, si se usa esa alternativa, se retiran al apagar.
  Las luces solapadas que siguen encendidas conservan su nivel.
- `night-only` también pausa los efectos y el RGB durante el día y los reanuda
  durante la noche. El abeto apagado recupera su iluminación natural.
- El encendido progresivo sigue disponible. OFF es inmediato y tiene prioridad
  sobre los modos y las transiciones.
- Los intervalos oscuros de BLINK, BREATHE y otros modos con la decoración ON
  conservan sus portadores de energía cero, evitando recrearlos en cada destello.

`strobelights.surfaces.follow-effects` pasa a `true` por defecto. Se puede dejar
en `false` para mantener los impactos fijos mientras las bombillas se animan.
La configuración v11 migra el antiguo `false` cuando reconoce su comentario
original. Un valor acompañado de comentarios personalizados se conserva. En
v11 y posteriores, un `false` explícito permanece intacto.

Se mantienen Paper 1.21.4, Java 21, StrobeLights 0.10.38 y, si se usa, TRP 0.4.19.
Los datos de luces siguen en v4, los idiomas en v8 y los modelos conservan su
geometría de 2.8.1. Los shaders de StrobeLights no cambian en esta revisión.

La compilación verifica 177 pruebas: OFF con todos los modos, modelos con pack y
vanilla, pausa diurna, retirada/recuperación RGB, color de CHASE, modo BLINK con
energía cero sin reemplazar entidades, limpieza LIGHT, y migración de ajustes.
La aceptación visual dentro de Minecraft requiere cargar el JAR actualizado.
