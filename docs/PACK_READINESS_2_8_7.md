# Estado del render con Nexo/TRP — 2.8.7

El log remoto `latest (6).log` confirma Paper 1.21.4, XmasLights 2.8.6,
StrobeLights 0.10.40, TRP 0.4.19 y Nexo 1.15. No se estaba ejecutando una versión
antigua de los dos JAR. Hay 774 segmentos guardados. No se ha inspeccionado la
configuración remota ni se conoce el presupuesto de entidades efectivo.

Se descargó el ZIP publicado por Nexo al final del log. Incluye el contrato
StrobeLights 0.10.40, la firma corregida para OptiFine, el carrier y sus referencias
ofuscadas válidas. Sus 32 programas de shaders efectivos para formato 46 compilan
en GPU. La ruta RGB del ZIP publicado supera 240 casos de transporte, 20 pases de
iluminación y la composición sin fuga de paquetes, usando la textura preparada
como OptiFine. Esto no demuestra que el cliente esté ejecutando esa composición.

El log registra INVALID_URL, FAILED_DOWNLOAD y FAILED_RELOAD. No basta para
atribuirles el fallo actual: el usuario confirma que finalmente ve los modelos.
StrobeLights 0.10.40 tampoco registraba en consola las confirmaciones de carga.

## Correcciones comprobadas en StrobeLights 0.10.41

- Antes, cualquier fallo de pack recibido con Nexo/TRP borraba la disponibilidad
  RGB del jugador. Ahora Nexo se identifica mediante el UUID de `packInfo()` y se
  conserva el estado por pack. Descartar una generación anterior no borra la nueva.
- La entrada del jugador ya no borra una confirmación recibida previamente. La
  desconexión y la parada sí limpian el estado. La prueba de esta secuencia falla
  antes de la corrección y pasa después.
- Se registran los UUID y la disponibilidad efectiva en los eventos relevantes,
  incluyendo SUCCESSFULLY_LOADED. El mensaje de error ya no afirma que el RGB
  sigue disponible cuando no existe un pack confirmado.

Estos son fallos reproducidos del código. Falta confirmar si explican por completo
la sesión remota del usuario. No se ha modificado ni reiniciado ese servidor Linux.
El shader de 0.10.41 es el mismo corregido en 0.10.40; TRP no decide la disponibilidad
de las fuentes de XmasLights mediante su perfil gráfico.

## Obtener la evidencia que falta

Instalar XmasLights 2.8.7 y StrobeLights 0.10.41 en el servidor remoto, reiniciar y
ejecutar **`/xl debug` junto a un tramo encendido**. Desde consola:
`xmaslights debug <jugador>`. Requiere `xmaslights.admin` y registra la salida en el
log del servidor cuando se ejecuta dentro del juego.

- `packReady=false`: el servidor no conserva una confirmación válida del pack RGB;
  la siguiente línea muestra proveedor, UUID esperado y último estado.
- `displayBudget=0`: la geometría agotó el límite global; no quedan entidades para
  los portadores RGB. Revisar el valor mostrado, sin aumentar límites a ciegas.
- `sources=0` o `selected=0`: no hay fuentes elegibles en los chunks enviados a ese
  jugador, o quedan fuera del rango configurado.
- `noSurface>0`: no se encontró un impacto en las sondas ejecutadas o quedó pendiente
  por el presupuesto de sondas de ese ciclo. `projected` muestra impactos utilizables.
- `carriers>0`, `packReady=true` y `errors=0`: la siguiente comprobación corresponde
  al cliente, sus ajustes, su versión y su log durante la carga del pack.

También hacen falta la versión exacta del cliente/OptiFine y la confirmación de
Fabulous. Si hay portadores, el `latest.log` del **cliente** aporta el motivo de
un FAILED_RELOAD; el log del servidor solo contiene ese estado general.
