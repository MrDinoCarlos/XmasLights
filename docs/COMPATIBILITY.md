# Compatibilidad de XmasLights 2.9.2

2.9.2 conserva las familias y sus packs. Cambia la negociación RGB para aceptar
un StrobeLights compatible por su contrato, sin una lista fija de versiones.
**Para 26.3 usar StrobeLights 0.10.43 y XmasLights 2.9.2**, actualizando ambos JAR
y aceptando el pack nuevo. Las 232 pruebas Java y la prueba conjunta de Paper
26.3 están descritas en [la entrega 2.9.2](RELEASE_2_9_2.md).

La matriz completa de doce servidores descrita a continuación es evidencia de
2.9.1; no se ha repetido íntegramente para 2.9.2. La prueba conjunta nueva verifica
el pack y las entidades en Paper 26.3; los shaders se ejecutan aparte en GPU.

Se distribuyen cuatro JAR por rama: 1.21.X, 26.1.X, 26.2.X y 26.3.X.
Cada uno incluye los packs de todos sus parches comprobados. La implementación usa la API
pública mínima de 1.21.4 y bytecode Java 21; cada artefacto lleva el `api-version` mínimo
de su rama, los parches comprobados en `compatibility.properties` y los packs
correspondientes. Al arrancar selecciona el pack del Minecraft real.
El JAR 1.21.X es exactamente el mismo archivo en los ocho servidores 1.21
comprobados; ocurre lo mismo con el de 26.1.X en sus dos parches.

Los resultados definitivos están en `dist/2.9.1/verification.json`. La matriz de
servidores no sustituye la aceptación visual en un cliente Minecraft.

| Paper | Java de prueba | Formato de recursos |
| --- | --- | --- |
| 1.21.4 | 21 | 46 |
| 1.21.5 | 21 | 55 |
| 1.21.6 | 21 | 63 |
| 1.21.7 | 21 | 64 |
| 1.21.8 | 21 | 64 |
| 1.21.9 | 21 | 69.0 |
| 1.21.10 | 21 | 69.0 |
| 1.21.11 | 21 | 75.0 |
| 26.1.1 | 25 | 84.0 |
| 26.1.2 | 25 | 84.0 |
| 26.2 | 25 | 88.0 |
| 26.3 | 25 | 97.1 |

Se cubren las publicaciones de Paper disponibles en las ramas pedidas. No se
extrapola la prueba a futuras revisiones 26.1.x/26.2.x/26.3.x que aún no se hayan
publicado o probado. Los requisitos de Java proceden de
[PaperMC](https://docs.papermc.io/paper/getting-started/); las builds y sus SHA-256,
de [su API de descargas](https://fill.papermc.io/v3/projects/paper).
Los formatos están contrastados con los metadatos extraídos de Minecraft en
[mcmeta](https://github.com/misode/mcmeta/tree/summary/versions).

## Qué comprueba la matriz

- Arranque del JAR específico, carga de los tipos y datos.
- Guardado real del mundo y entidades, descarga/recarga de chunks y reconstrucción.
- Reinicio con colocaciones conservadas y sin duplicar displays.
- Desactivación explícita y reinicio sin JAR ni carpeta de XmasLights.
- Ausencia de cables, bombillas y LIGHT propios, conservando display y LIGHT ajenos.
- Creación y retirada de los 51 diseños tanto con modelos del pack como vanilla.
- TextDisplay real: metadatos y rotación para suelo y cuatro caras de pared, en
  ambos modos de pack. No es una captura ni una prueba visual del shader.

`tools/verify_matrix.py` prepara mundos nuevos dentro de `.tools/`, con puertos
libres ligados a 127.0.0.1 y descargas verificadas por SHA-256. Requiere una EULA
ya aceptada mediante `--eula-file`. `tools/verify_designs.py` se ejecuta después.
`tools/package_release.py` exige que el código probado en el ciclo de vida sea
idéntico al entregado y que el JAR final coincida con la prueba de diseños.

## Render RGB real

2.9.1 retira el renderer alternativo de 2.9.0. No hay proyecciones TextDisplay,
fuentes radiales ni círculos dibujados sobre las superficies. Se usa exclusivamente
el ItemDisplay técnico y el shader 3D de StrobeLights. Se conserva la instantánea
getSentChunkKeys que permite generar las fuentes en Paper/Moonrise.

La integración con host real y las pruebas GPU de esta entrega son de
**StrobeLights 0.10.41 para Minecraft 1.21.4**: descarga y confirmación del pack,
fuentes para suelo y pared, recepción de los metadatos reales, cero TextDisplay,
y replay de sus payloads en los GLSL del propio JAR. Se repite con la textura
preparada para el atlas de OptiFine. Los informes están en el ZIP de pruebas.

El RGB requiere un StrobeLights y un pack compatibles con cada cliente. La prueba
de la API de XmasLights en doce versiones no demuestra que un pack StrobeLights
de 1.21.4 sirva para las otras once. No se extrapola ese ensayo gráfico.

Las versiones anteriores a 26.3 usan Fabulous y shaderpacks externos desactivados.
El renderer 26.3 utiliza el efecto permanente `end_of_frame`.
Los packs que sustituyan ese pipeline necesitan soporte propio. Se explicita esa
limitación; no se vuelve a suplir con otro renderer. La aceptación visual en la
instalación Minecraft del usuario sigue siendo distinta de los ensayos GPU.

Nexo/TRP siguen siendo proveedores opcionales. El nuevo pack decorativo de
XmasLights no contiene ningún shader: todos los efectos RGB siguen siendo de
StrobeLights. Folia no está soportado.
