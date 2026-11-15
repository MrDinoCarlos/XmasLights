# XmasLights 2.8.8: retirada sin cables ni luces residuales

Hasta 2.8.7, los cables y las bombillas eran displays persistentes: Minecraft los
guardaba dentro de los chunks. Borrar el JAR y `plugins/XmasLights/` no borraba
esas entidades. Además, `onDisable` guardaba los datos sin retirar la geometría.

Desde 2.8.8, cables, cuelgues, bombillas y guirnaldas son displays temporales
(`persistent=false`), igual que los portadores RGB. `lights.yml` conserva las
colocaciones, identificadores, propietarios, grupos, cadenas y efectos. El plugin
reconstruye gradualmente las partes que falten en chunks cargados, sin duplicar las
que siguen presentes ni cargar otros chunks para renderizar. Los tramos ocultos
con `/xl despawn` siguen ocultos al reiniciar.

La propiedad de los bloques LIGHT se conserva aunque sus chunks se descarguen.
Al desactivar el plugin se detienen primero las tareas y se visitan exactamente
las posiciones registradas para restaurar también los bloques de chunks descargados.
Se retiran los portadores RGB, todos sus displays cargados y sus bloques LIGHT. No se
eliminan displays ajenos, bloques modificados por jugadores ni LIGHT preexistentes.
La limpieza se hace antes de la parada/descarga del mundo, evitando escribir bloques
desde `ChunkUnloadEvent`: en Paper esas escrituras pueden provocar recargas continuas.

## Actualizar y desinstalar

1. Detén el servidor. Sustituye el JAR antiguo por `XmasLights-2.8.8.jar`.
2. Arranca una vez con 2.8.8. Conserva `plugins/XmasLights/` para que pueda localizar
   las colocaciones anteriores.
3. Detén el servidor con `stop`. En esta primera parada también se visitan los
   chunks registrados de versiones anteriores para retirar displays persistentes,
   incluidos los que no estaban cargados. Este trabajo es una migración puntual;
   la representación nueva no necesita cargar chunks para dibujar.
4. Con el servidor detenido, elimina el JAR y, si quieres, `plugins/XmasLights/`.
   Arranca de nuevo: ya no habrá geometría ni iluminación de esas colocaciones.

El marcador `render-storage: ephemeral-v1` se escribe cuando termina la limpieza
anterior. Las posiciones de chunks antiguos pendientes se conservan en
`legacy-geometry-chunks`; si un mundo no está disponible, su limpieza sigue pendiente
hasta que vuelva a estar cargado con el plugin instalado.

Si **ya borraste la carpeta de datos**, reinstala 2.8.8 y carga las zonas afectadas:
los displays marcados como XmasLights sin un tramo correspondiente se eliminan al
cargar sus entidades. Después ejecuta `stop` y retira el plugin. Sin `lights.yml`,
no se conocen las coordenadas de restos antiguos en chunks sin visitar ni se puede
atribuir con seguridad un bloque LIGHT antiguo; no se borran luces ajenas a ciegas.

La limpieza automática requiere una parada normal. Un cierre forzado del proceso
puede dejar una instantánea previa de bloques LIGHT en disco; conserva los datos y
arranca/detén 2.8.8 antes de desinstalar en ese caso. Un plugin ya borrado no puede
ejecutar una limpieza retrospectiva.

## Verificación

`RenderLifecycleTest` comprueba los 51 tipos, retirada de geometría y huérfanos,
restauración de LIGHT, conservación de decoraciones ajenas, reconstrucción tras
reinicio y descarga parcial, ausencia de duplicados y respeto de tramos ocultos.

`tools/verify_lifecycle.py` ejecuta una prueba adicional en un Paper 1.21.4 aislado:
guarda el mundo, solicita la descarga real de chunks, vuelve a cargarlos, reinicia
con el plugin, lo desactiva y finalmente arranca con el JAR y la carpeta de datos
fuera de `plugins/`. Un inspector independiente verifica la ausencia de displays
y LIGHT propios y la conservación de un display y un LIGHT ajenos.

Resultado del 14 de noviembre de 2026: **218 pruebas, sin fallos ni omisiones**.
La comprobación real pasó sus tres fases (`create`, `restore`, `absent`) en
**Paper 1.21.4 build 232 y Java 21.0.12.1**: 71 displays temporales, dos tramos,
descarga y reconstrucción de chunks, reinicio, desactivación y arranque sin
XmasLights ni su carpeta. Se conservaron el display y el LIGHT ajenos.
Los registros locales están en `.tools/lifecycle-smoke-288-shutdown/`.
