> ENTREGA RETIRADA: el renderer alternativo de esta versión fue rechazado y eliminado. Usa 2.9.1; este documento es histórico.

# XmasLights 2.9.0

## Instalación

1. Detén Paper y conserva una copia del mundo y de `plugins/XmasLights/`.
2. Instala un único `XmasLights-2.9.0+mc.v.<rama>.jar` de `dist/2.9.0/`.
3. Inicia con Java 21 para 1.21.x y Java 25 para 26.x. Comprueba `/xl debug`.
4. Acepta el pack nuevo para los modelos detallados y la proyección radial suave.

Hay cuatro JAR: 1.21.X, 26.1.X, 26.2.X y 26.3.X. Cada uno contiene metadatos
de rama y todos los packs de sus parches comprobados; selecciona el del servidor.
Las clases comparten la API pública mínima de 1.21.4 y bytecode Java 21; el soporte
se comprueba arrancando el artefacto específico en la build real de cada Paper.
No se incluyen NMS, clases de Paper ni librerías externas en el plugin.

## Ajustes

```yaml
animated-light: true
view-distance-chunks: 21
limits:
  max-entities: 8192
strobelights:
  enabled: true
  renderer: portable
  view-distance-chunks: 0
updates:
  enabled: true
  notify-admins: true
  check-interval-hours: 6
```

El multiplicador antiguo de displays equivale a cuatro chunks por unidad. El
límite antiguo de RGB se divide entre 16. Se permiten fracciones para conservar
límites personalizados; 0 en RGB sigue los chunks enviados. Estos ajustes no
incrementan el tracking de entidades de Paper ni cargan chunks adicionales.
El radio físico de iluminación permanece en bloques: `surfaces.max-radius`.

Se mantiene el presupuesto total de entidades. Una proyección portátil consume
una entidad por fuente y jugador, igual que un portador shader; se retira al
apagar, desconectar, desaparecer el impacto o perder el chunk. OFF y night-only
conservan su funcionamiento. La proyección no permite ver a través de paredes.

## Alcance visual

Portable usa TextDisplay y una fuente propia con máscara radial RGBA. Si el pack
no está listo, utiliza el círculo de la fuente vanilla. No necesita OptiFine ni
Fabulous; evita depender de los core shaders reemplazados por otros packs.

Es una superposición translúcida paralela a la primera superficie detectada. No
es iluminación física RGB, no colorea entidades ni reconstruye el relieve de los
bloques dentro del círculo. Hay que comprobar su apariencia, tamaño y mezcla con
cada cliente, perfil gráfico y shaderpack antes de dar por cumplida la aceptación
visual. No se declara que «todos los shaders» hayan sido probados.

Shader conserva el contrato anterior con StrobeLights. Solo los parches y packs
1.21.4 incluidos tienen la validación GPU histórica; no se extrapola a 26.x.

## Actualizaciones

Consulta el proyecto Modrinth `SjN2N7xv` (xmas-lights), sin autenticación. Filtra
versiones estables para el Minecraft exacto y loader Paper. Compara componentes
numéricos y descarta el sufijo de distribución `+mc.v.X`; 2.10 supera a 2.9.
El enlace siempre apunta a una versión de ese proyecto. La petición tiene timeout,
límite de tamaño y User-Agent. Un fallo de red no deshabilita el plugin.
No realiza descargas ni instalaciones. El aviso se traduce a los cinco idiomas.

## Evidencia

`dist/2.9.0/verification.json` contiene cada build, hash y estado de ciclo de vida.
Los logs completos están en `.tools/matrix-2.9.0/<Minecraft>/`. Las pruebas crean
segmentos y un display y LIGHT ajenos, guardan, descargan/recargan chunks, reinician,
desactivan XmasLights y vuelven a arrancar sin JAR ni carpeta de datos. Exigen que
las luces ajenas sigan presentes y las propias hayan desaparecido. También crean
proyecciones reales en cinco orientaciones y comprueban sus metadatos y rotación.
Eso no proporciona capturas ni aceptación visual de un cliente Minecraft.

Resultado: 230 pruebas unitarias sin fallos, errores ni pruebas omitidas; 12/12 ciclos
de vida Paper correctos y 51 diseños × 2 modos de modelos × 12 versiones.
La comprobación visual de clientes queda pendiente por el fallo del servicio de captura.

## Corrección del filtro de chunks

Se reprodujo el debug del servidor con un cliente conectado: seis bombillas
activas, válidas y cargadas, pero `sources=0`. En Paper 1.21.4, `isChunkSent`
consultaba la vista vanilla mientras `getSentChunkKeys()` usaba el conjunto de
Moonrise. La primera devolvía false y el segundo sí contenía el chunk.
Se usa ahora una instantánea de `getSentChunkKeys()` por jugador/actualización.
La prueba corregida produjo tres fuentes, impactos y proyecciones, recibidas por
el cliente. En 1.21.11 otra prueba con suelo y pared recibió siete entidades y sus
metadatos. Estos tests verifican el envío al cliente, aunque no son capturas GPU.

Para repetir el cliente de protocolo local:

```powershell
npm.cmd install --prefix .tools/client-probe --no-audit --no-fund --ignore-scripts minecraft-protocol@1.68.0
python tools/verify_player_sources.py --work-name player-source-probe-fixed
python tools/verify_player_sources.py --work-name player-source-probe-floor-wall-2111 --minecraft 1.21.11
```

Con una caché Paper ya verificada, `python tools/verify_branches.py` ejecuta cada
mismo JAR de rama en todos sus parches, con mundos y datos nuevos. El empaquetador
exige que todos los hashes de esos ensayos coincidan con los cuatro JAR entregados.

La comprobación con cliente conectado en Paper 26.1.2 también recibió siete
proyecciones de suelo y pared y catorce paquetes de metadatos. Los datos de las
tres reproducciones están incluidos en el ZIP de pruebas.
