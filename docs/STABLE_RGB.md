# RGB estable de XmasLights 2.8

Actualiza **XmasLights a 2.8.5 y StrobeLights a 0.10.39**, ambos para Paper 1.21.4.
La fusión de shaders con TRPServerEdition **0.4.19** se ha verificado para esta versión.
TRP continúa siendo opcional: la combinación XmasLights + StrobeLights funciona sin él.
Conserva los datos, sustituye los JAR anteriores, reinicia y acepta el pack actualizado.
Con Nexo, permite que termine la regeneración del pack antes de comprobar el resultado.
DecoSwap 0.1.18 sigue siendo compatible y no necesita otra actualización.

Desde 2.8.4, XmasLights no consulta el perfil gráfico de TRP ni el método
`stableSourceProtocol()` de StrobeLights, que en 0.10.38 también comprobaba TRP.
La integración valida directamente el contrato del pack de StrobeLights y espera
a que su servicio confirme la carga para cada jugador. TRP puede estar ausente,
desactivado o usar el perfil Native sin bloquear los portadores RGB de XmasLights.
No hace falta ejecutar `/trplive graphics fabulous` para encender XmasLights.
El cliente sigue necesitando Fabulous y el pack correcto; un shader combinado
debe conservar los recursos de StrobeLights, incluida la extensión estable.

En `dist/` están los tres JAR para 1.21.4, el ZIP editable de XmasLights y el
ZIP editable de StrobeLights. Los proyectos Java se abren desde `pom.xml` y
`build.gradle.kts`, respectivamente. El proyecto TRP existente se actualiza
en su propia carpeta; su [parche de esta revisión](trp-0.4.19-stable-slots.patch)
se incluye junto al [parche de StrobeLights](strobelights-0.10.38-stable-slots.patch).
Estos parches contienen solo los cambios de esta revisión respecto al estado
local anterior; no son sustitutos de un proyecto completo de TRP.
La ampliación de distancia de 0.10.39 se conserva además en el
[parche de esta revisión](strobelights-0.10.39-chunk-distance.patch) y en su proyecto editable.

La [corrección de 2.8.1](RGB_FIX_2_8_1.md) evita el fallback blanco con un host
instalado pero incompatible y elimina los casquillos de los modelos inline.

## Qué se ha corregido

El transporte anterior proyectaba cada señal de 3x3 píxeles según su posición 3D.
Varias guirnaldas densas podían acabar escribiendo sobre los mismos píxeles. Eso
corrompía el color y el radio o hacía desaparecer la fuente al alejarse o girar.
Se reprodujo con los GLSL reales de StrobeLights 0.10.37 en una NVIDIA RTX 3060:
con 32 fuentes, a 24 bloques se decodificaban 23 colores incorrectos; a 48 se detectaba
solo una fuente y a 64 no se detectaba ninguna en la escena de prueba.

Cada fuente utiliza ahora uno de 128 canales independientes por espectador, situado
en pequeños paquetes al borde superior de la imagen. La posición XYZ, el color,
el radio y el FOV viajan por separado. Los paquetes se validan y se retiran de la
composición Fabulous. También funcionan sobre una capa de items ya ocupada por
modelos; sus bytes evitan las firmas reservadas de los haces de TRP.

StrobeLights conserva el transporte v5 de sus luces nativas, flashes y efectos.
La extensión nueva no crea strobes ni registra nada en su editor. TRP 0.4.19 inserta
la extensión en su shader combinado conservando el código existente de haces.
Los shaders permanecen en StrobeLights; el pack de modelos de XmasLights mantiene
únicamente recursos de su propio espacio de nombres.
La reparación del pack de Nexo incluye los cuatro nuevos archivos GLSL del
espacio `strobelights`, sin sustituir sus otros assets ni sus metadatos.

Desde 2.8.2, el RGB sigue los efectos por defecto. OFF y la pausa diurna retiran
los portadores y congelan el color del modelo. Se conserva `follow-effects: false`
para pedir un suelo fijo expresamente. Consulta [OFF y efectos](OFF_AND_EFFECTS.md).

## Luz moderada y distancia

Los valores reconocibles de 2.7 se actualizan con copia previa:

| Opción | Predeterminado 2.8 |
| --- | --- |
| `strobelights.intensity-scale` | `0.55` |
| `strobelights.surfaces.max-radius` | `2.25` bloques |
| `strobelights.surfaces.distance-fade` | `8.0` bloques |
| `strobelights.view-distance` | `0`: chunks enviados al jugador |
| `strobelights.max-sources-per-player` | `128` |
| `strobelights.max-carriers-total` | `512` |
| `strobelights.surfaces.probes-per-update` | `64` |

El radio se cuantiza hacia abajo e independientemente de la intensidad. La distancia
de la bombilla al bloque atenúa el impacto. La distancia del jugador no altera su
energía: las pruebas conservan el contenido RGB al alejarse 30/32 chunks,
incluidas las diagonales. Desde 2.8.5, el alcance predeterminado usa
[`Player.isChunkSent`](https://jd.papermc.io/paper/1.21.4/org/bukkit/entity/Player.html#isChunkSent(long)):
una fuente deja de enviarse cuando el cliente pierde su chunk, aunque siga cargado
en el servidor por otro jugador. La preselección de `LightManager` aplica la misma regla.
El modo automático no tiene el antiguo corte de 192 bloques. Un límite explícito
admite 4..1024 bloques y conserva un margen de salida de ocho bloques.
El servidor debe enviar los chunks correspondientes; la integración no fuerza su carga.

StrobeLights 0.10.39 amplía el transporte de posición a ±1024 bloques y lleva también
los coeficientes reales de profundidad de la cámara. Minecraft cambia su plano lejano
con la distancia de renderizado (1920 para 30 chunks; 2048 para 32), por lo que asumir
siempre 1024 podía desplazar el suelo reconstruido y perder el impacto RGB. Se conservan
los coeficientes de la matriz con su precisión de origen y se usan solo para las fuentes
de integración. Sus luces ya no reciben el fundido de distancia de las luces nativas.
El ABI de los items permanece `slots3x3_depth_v1`; cambian los paquetes internos del pack.
La fusión con TRP 0.4.19 continúa funcionando sin cambios en sus preferencias.

La configuración pasa a v13. Los antiguos valores predeterminados 192/32/128/16
se actualizan a 0/128/512/64 con copia previa. Otros valores personalizados se conservan.
El protocolo permite 128 fuentes simultáneas por jugador; ampliar el alcance no elimina
este presupuesto ni el límite global de displays.

La composición de las fuentes de integración usa menos ganancia y caída radial
cuadrática. Esto evita la iluminación excesiva de 2.7. El mínimo visual del abeto
permanece configurable en 5/15; no se añaden LIGHT blancos cuando el adaptador está activo.
La selección reutiliza las entidades y los canales existentes. El presupuesto global
permanece en 4096 displays, incluidos los portadores RGB; no hay entidades por píxel.

## OptiFine y comprobación

El modo soportado es **Fabulous con el pack actualizado**, también con OptiFine y
sus shaderpacks externos desactivados. Un shaderpack que sustituye la composición
Fabulous necesita su propia integración. El servidor no puede conocer ni forzar
esa opción del cliente. La preferencia Native/Fabulous de TRP controla TRP;
desde XmasLights 2.8.4 no decide si XmasLights envía sus propias fuentes RGB.

`tools/verify_strobe_gpu.py` ejecuta los shaders reales en OpenGL con blending,
profundidad, culling, filtrado, deduplicación, seis pases de agregación y composición.
Comprueba 32 y 128 fuentes, distancias de 8 a 256 bloques, giros de cámara y FOV
15/70/110, hasta 768 bloques y planos lejanos 1024/1920/2048. Además ejecuta
los pases RGB opaco y transparente a esas distancias. La prueba con capa de items ocupada verifica también que los datos no
aparezcan como colores en la imagen final.

```text
python tools/verify_strobe_gpu.py --strobe-jar <StrobeLights.jar> --minecraft-jar <cliente-1.21.4.jar> --pipeline --occupied
```

Para un pack combinado ya generado, añade `--hybrid-pack <combinado.zip>`.
Requiere `moderngl`, `numpy` y un contexto OpenGL 3.3. El cliente de Minecraft se usa
solo para resolver sus imports GLSL; no se redistribuyen sus recursos.
El [informe de la GPU](../previews/rgb-gpu-verification.json) registra las comprobaciones.
Estas escenas sintéticas complementan la aceptación en un cliente real; no prueban
todas las versiones de OptiFine ni shaderpacks externos.

## Variantes sin ramal

`/xmas give wall-inline-warm` y las otras 16 variantes `wall-inline-*` centran la
bombilla en el cable. Los farolillos se alinean por el cuerpo luminoso. No tienen
ramales verticales: seis metros de warm pasan de 21 a 11 displays. El catálogo tiene
51 tipos y conserva las variantes originales con cuelgue.
Consulta [la comparación](../previews/09-wall-inline-comparison.png).

## Cara de pared de las guirnaldas en 2.8.5

`GarlandBacking` comprobaba la pared en el espacio del módulo, pero Minecraft aplica
Y=180° después de la transformación de cada ItemDisplay, incluso con transform NONE.
Eso invertía la cara que realmente se veía. Los displays de abeto y LED compensan ese
giro en la rotación derecha, antes de la escala; las sondas, la geometría y los modelos
`no_back`/`no_front` coinciden ahora. Se prueban las cuatro orientaciones con ambos
órdenes de anclaje. Una firma de geometría nueva reconstruye las guirnaldas ya colocadas
sin cambiar sus puntos, UUID lógicos, grupos ni efectos. No hace falta recolocarlas.
