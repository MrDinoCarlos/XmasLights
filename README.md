# XmasLights 2.9.2

Esta versión corrige el rechazo de StrobeLights actualizado. El bridge comprueba
el contrato del pack real en lugar de limitarse a una lista de versiones del
plugin. Para RGB en **26.3 usar StrobeLights 0.10.44 y su nuevo pack**;
hay que actualizar ambos JAR. El ajuste `allow-untested-version` queda obsoleto
y no permite saltarse la comprobación del protocolo.

StrobeLights 0.10.44 corrige un fallo posterior de horneado del modelo 26.3:
el cliente descartaba la geometría puntual aunque aceptase el pack 0.10.43.
La corrección se ha comprobado en el cliente vanilla real. XmasLights 2.9.2
conserva su ABI y no necesita otro cambio de JAR por ese ajuste del modelo.

Esta versión **retira el renderer alternativo introducido en 2.9.0**. La iluminación
RGB vuelve exclusivamente al render 3D de **StrobeLights**, con su modelo técnico
`strobelights:carrier/rgb`, su protocolo y sus shaders. No dibuja círculos, planos
ni TextDisplay sobre suelo o paredes. Se conserva la corrección de chunks enviados
que evitó que todas las fuentes quedasen descartadas con `sources=0` en Paper.

La entrega está en `dist/2.9.2/`, con **un JAR por rama**:

| JAR | Parches de Paper mantenidos |
| --- | --- |
| `XmasLights-2.9.2+mc.v.1.21.X.jar` | 1.21.4–1.21.11 |
| `XmasLights-2.9.2+mc.v.26.1.X.jar` | 26.1.1 y 26.1.2 |
| `XmasLights-2.9.2+mc.v.26.2.X.jar` | 26.2 |
| `XmasLights-2.9.2+mc.v.26.3.X.jar` | 26.3 |

La matriz completa de Paper, diseños y ciclo de vida corresponde a 2.9.1.
Para 2.9.2 pasan 232 pruebas Java y una prueba conjunta aislada con Paper 26.3
y StrobeLights 0.10.43: pack verificado, fuentes sobre suelo y pared y cero
TextDisplay. StrobeLights comprueba por separado los shaders 26.3 con 38 casos
GPU. El RGB requiere el pack compatible con el cliente; los resultados gráficos
de 1.21.4 no se extrapolan a otros packs. Ver [detalle y límites](docs/RELEASE_2_9_2.md).

## Actualizar

Detén el servidor, sustituye el JAR anterior por el de tu rama y conserva
`plugins/XmasLights/`. Inicia y acepta el pack real de StrobeLights. Al actualizar
se retira automáticamente `strobelights.renderer: portable`; los círculos de 2.9.0
eran temporales y desaparecen con la parada normal. No hace falta borrar las luces.

La configuración conserva los valores solicitados:

```yaml
animated-light: true
view-distance-chunks: 21
limits:
  max-entities: 8192
```

Se mantienen los avisos de Modrinth en consola y para administradores al entrar,
la limpieza de cables/bombillas/LIGHT al desactivar el plugin y los 51 diseños.
Los packs decorativos solo incluyen modelos y texturas; el RGB pertenece a StrobeLights.

**Requisito del StrobeLights instalado:** su RGB funciona con el pack correcto;
los pipelines anteriores a 26.3 usan Fabulous. El renderer de 26.3 utiliza el
efecto permanente `end_of_frame`. OptiFine puede usarse con el pipeline compatible
y los shaderpacks externos apagados.
Un shaderpack que sustituya ese pipeline necesita soporte dentro del propio
StrobeLights o de ese shaderpack; esta versión no oculta esa limitación con una
superposición de color. Sin StrobeLights queda únicamente la alternativa vanilla
configurada con `light-blocks`.

[Detalle y pruebas](docs/RELEASE_2_9_2.md) · [Compatibilidad](docs/COMPATIBILITY.md)

Compilación: `./build.ps1` (Java 21). Empaquetado de cuatro ramas:
`python tools/verify_matrix.py --package-only`.

## Documentación anterior (2.8.8, referencia histórica)

Las secciones siguientes describen el renderer shader y los artefactos anteriores.
Los ajustes de visión, el renderer predeterminado y los destinos de compilación
vigentes son los indicados arriba.

**Desinstalación sin restos:** cables, bombillas y guirnaldas son ahora entidades
temporales. Se reconstruyen desde `lights.yml` mientras el plugin está instalado
y se retiran al apagarlo, junto con sus luces. Para limpiar colocaciones antiguas,
arranca una vez con 2.8.8 conservando los datos y ejecuta `stop` antes de borrar el
JAR y su carpeta. Consulta [la limpieza y sus límites](docs/UNINSTALL_2_8_8.md).

Esta entrega admite **StrobeLights 0.10.41**, que conserva las confirmaciones de
pack al entrar y separa los estados por UUID para que los fallos de otros packs no
apaguen el RGB. `/xl debug [jugador]` muestra la disponibilidad del pack, las
fuentes, los impactos y el presupuesto real de entidades. Ver
[diagnóstico del servidor con Nexo/TRP](docs/PACK_READINESS_2_8_7.md).

La corrección del atlas de OptiFine introducida en StrobeLights 0.10.40 se mantiene
en 0.10.41. Se reprodujo la pérdida de las fuentes al redimensionar el atlas del
cliente, tanto en el pack independiente como en la fusión con TRP 0.4.19.
Detalles y límites de la validación en [la corrección de OptiFine](docs/OPTIFINE_RGB_2_8_6.md).

Proyecto Java editable para IntelliJ IDEA, reconstruido a partir de `XmasLights-2.1.0.jar`.
Autores: **AstralicaStudio y MrDino**. Se mantienen el nombre, el paquete Java,
los comandos y el espacio de claves persistentes del plugin original.

La base de compilación es **Paper 1.21.4 y Java 21**. El soporte posterior queda
abierto; [la hoja de ruta hasta 26.3](docs/COMPATIBILITY.md) distingue las pruebas
realizadas de las versiones pendientes. No hay código NMS ni reflexión sobre Minecraft.

## Abrir y compilar

1. Abre `pom.xml` en IntelliJ como proyecto Maven y deja que importe las dependencias.
2. Selecciona un **JDK 21** como Project SDK y como JRE de Maven. La configuración local
   de este proyecto ya apunta al SDK 21 registrado en IntelliJ.
3. En Windows ejecuta `./build.ps1`. Detecta JDK 21 en `JAVA_HOME`, `.jdks`, `.gradle/jdks`
   o `PATH`, ejecuta las pruebas y genera `target/XmasLights-2.8.8.jar`.
   Puedes indicar otro JDK con `./build.ps1 -JdkHome "C:\ruta\jdk-21"`.
4. Alternativa con `JAVA_HOME` apuntando a JDK 21: `./mvnw.cmd clean verify`.
   En Linux/macOS: `sh ./mvnw clean verify`.

Maven 3.9.9 se descarga mediante el wrapper con comprobación SHA-256. No hace falta
instalar Maven globalmente. La API de Paper y las herramientas de pruebas no se incluyen
en el JAR del plugin. La primera compilación necesita Internet.

## Instalar o actualizar

1. Detén el servidor y haz una copia de `plugins/XmasLights/` y del mundo.
2. Sustituye el JAR anterior por `target/XmasLights-2.8.8.jar` en `plugins/`.
   No dejes dos JAR de XmasLights ni el antiguo LucesNavidad activos a la vez.
3. Conserva `config.yml`, `types.yml` y `lights.yml`; inicia Paper 1.21.4 con Java 21.
4. Se añaden opciones y traducciones que falten, con copias fechadas antes de actualizar.
   Las luces existentes conservan sus UUID, grupos, cadenas, visibilidad y efectos.

Los archivos preparados están en `dist/`: XmasLights 2.8.8, su proyecto editable y el
pack de modelos. Para las correcciones RGB usa también StrobeLights 0.10.41. Si ya usas
TRPServerEdition, sustituye su JAR por el 0.4.19 para 1.21.4 incluido por separado.
La [guía de RGB estable](docs/STABLE_RGB.md) detalla la actualización y sus pruebas.

Los datos de 2.1 no incluían propietario ni constancia de haber consumido un kit.
Se conservan como luces administrables por `xmaslights.manage-all`, sin generar
reembolsos de objetos. Un administrador puede mirar una cadena y ejecutar
`/xl owner <jugador-conectado>` para asignarla. Las luces nuevas registran su propietario.

También se conserva la importación de `plugins/LucesNavidad/` cuando aún no hay archivos
en XmasLights. La importación prepara todos los archivos antes de instalarlos y conserva
los originales. Un YAML malformado detiene el proceso, en lugar de reemplazarlo por datos vacíos.

## Iluminación de StrobeLights y guirnaldas de abeto

**StrobeLights es una dependencia opcional.** Cuando está instalado y activo, XmasLights
puede enviar los colores y la intensidad de sus bombillas a su render RGB. No se crean
strobes, entradas en su editor ni efectos de cámara. Sin StrobeLights, ese render RGB
permanece desactivado y sigue disponible la iluminación normal de Minecraft.

Para OptiFine, el adaptador requiere **StrobeLights 0.10.40** para Paper 1.21.4 y su pack actualizado.
La fusión de shaders con TRPServerEdition **0.4.19** se ha verificado para esta versión.
XmasLights funciona con StrobeLights solo. Desde 2.8.4, el perfil gráfico de TRP
no habilita ni bloquea sus luces RGB: no hay que ejecutar comandos de TRP para verlas.
Se valida el pack propio de StrobeLights, sin consultar su comprobación agregada de TRP.
TRP sigue siendo un proveedor opcional del pack; la fusión debe conservar los shaders de StrobeLights.
El cliente necesita **Fabulous**; OptiFine debe
tener desactivados los shaderpacks externos que sustituyen ese pipeline.

La señal de cada luz ocupa una posición propia, independiente de la posición proyectada
del impacto. Se corrige la mezcla de datos que producía colores erróneos, destellos y
desapariciones al alejarse. La posición XYZ, el radio y la intensidad se transportan por
separado. El impacto queda en una superficie sólida a un máximo de ocho bloques de la
bombilla; solo esa distancia física atenúa la luz. Alejar o girar la cámara no cambia
su energía. El radio máximo predeterminado es **2.25 bloques**, con intensidad **0.55**
y una composición más suave. El alcance predeterminado sigue los **chunks enviados a cada jugador**,
incluidos 30/32 chunks y las diagonales. `strobelights.view-distance: 0` activa este
modo; 4..1024 permite conservar un límite explícito. No se cargan chunks extra.
StrobeLights 0.10.39 transporta coordenadas hasta ±1024 bloques y la proyección real
de profundidad de la cámara; elimina el corte y la atenuación de cámara de estas fuentes.

Los impactos RGB siguen por defecto el modo de las bombillas, en color e intensidad.
`strobelights.surfaces.follow-effects: false` permite dejarlos fijos si se prefiere.
OFF y la pausa diurna de `night-only` detienen los efectos, congelan el color y
retiran los portadores RGB. ON recupera el modo guardado; el encendido progresivo
sigue disponible. Consulta [OFF y efectos](docs/OFF_AND_EFFECTS.md).
Consulta [la corrección y sus pruebas GPU](docs/STABLE_RGB.md).

Con StrobeLights activo y su integración habilitada, **XmasLights retira sus bloques LIGHT
blancos y usa solo la capa RGB**, incluidos los tramos ya colocados. Una versión o pack
incompatible deja el RGB pendiente con un aviso, sin sustituirlo por luz blanca.
La [corrección de 2.8.1](docs/RGB_FIX_2_8_1.md) explica el caso detectado con StrobeLights 0.10.37.
Al desactivar StrobeLights o su
integración, vuelve la alternativa vanilla si `light-blocks: true`. Se conserva la geometría
y no se tocan luces ajenas. La elección es global: un jugador sin el pack RGB no vuelve
a encender luz blanca para los demás. La preferencia Native/Fabulous de TRP no controla XmasLights.

La cara oculta de las guirnaldas coincide desde 2.8.5 con el apoyo real: se compensa
el giro Y=180° que Minecraft aplica a los ItemDisplay. Las guirnaldas existentes se
reconstruyen gradualmente conservando su colocación, identificadores y grupos.
Los cuatro tipos de abeto tienen adornos por ambas caras y ocho LED enrollados por módulo.
Se han revisado los **51 tipos**: bombillas redondeadas, cubos compactos con
casquillo, farolillos vanilla y abeto. Los tipos `wall-*` tienen cable recto y conservan
el cuelgue de las bombillas; cada diseño tiene su correspondiente variante de pared y su
variante `wall-inline-*`. Los cubos y bombillas inline no tienen casquillo superior;
su cuerpo luminoso y su origen RGB están centrados en el cable.
El abeto recibe un mínimo visual suave de **5/15**, configurable con
`garlands.minimum-body-light` (0 desactiva el mínimo); respeta la luz natural más intensa.
Este ajuste del modelo no añade bloques LIGHT blancos ni entidades.
Se conservan los identificadores, grupos y cadenas guardadas.
Los ocho LED de cada módulo multicolor reciben individualmente los colores de su paleta.

| Tipo | Diseño | Colocación |
| --- | --- | --- |
| `garland-classic` | Abeto, lazos, piñas, bolas rojas/doradas y LED cálidos | Contornos con caída natural |
| `garland-snowy` | Abeto nevado, adornos marfil/azules/dorados y LED cálidos | Contornos con caída natural |
| `garland-rgb` | Abeto con adornos y LED multicolor | Contornos con caída natural |
| `garland-draped` | Diseño clásico con caída propia de 0.20 | Barandillas y tramos colgantes |

El modo predeterminado utiliza el **pack de modelos de XmasLights**, con entrega automática
activada por defecto. Al detectar **Nexo**, XmasLights prepara su entrada
`plugins/Nexo/pack/external_packs/XmasLights-Models.zip` durante `onLoad`, antes de activar
los plugins. Nexo lo incorpora a su pack y se encarga de generarlo, alojarlo y entregarlo,
sin necesitar TRP. Si los recursos cambian con Nexo ya activo, se espera a cualquier
generación en curso y se solicita una regeneración; los archivos iguales no se reescriben.

Sin Nexo, se usa el pack compartido de TRP cuando está disponible. Sin ambos, XmasLights
aloja automáticamente el ZIP en el **puerto TCP 8164** y lo envía al entrar, con su huella
SHA-1. El puerto debe ser accesible desde los clientes; en un hosting, proxy o NAT puedes
ajustar `model-pack.embedded.port` y `public-url`. La dirección automática usa el hostname
con el que entró el jugador. También puedes usar una descarga externa con `model-pack.url`.
El pack propio se añade a los ya instalados y solo contiene recursos `xmaslights`.

Para una instalación manual del pack en todos los clientes, consulta `model-pack.assume-installed`
en [la guía de instalación y ajustes](docs/STROBELIGHTS_AND_MODELS.md). Los modelos se ocultan
a los jugadores que aún no han cargado el pack; tampoco pueden colocarlos por accidente.
Los modelos y sus LED pueden verse sin StrobeLights; la iluminación RGB de superficies
requiere StrobeLights y su pack.

Prueba `/xl give garland-classic 16` o `/xl give garland-draped 8`. Sus efectos usan los
mismos comandos de las demás luces. Los nombres, avisos y `/xl pack` están traducidos
a los cinco idiomas. `types.yml` permite ajustar `rgb`, `spacing`, `size` y `sag` por tipo.

Puedes ver el [detalle de cables y cubos](previews/07-cables-and-cubes.png) y el
[catálogo de los 51 tipos](previews/08-all-light-types.png). Usan los modelos reales y las
transformaciones exportadas por el plugin; los [renders y escenas editables](previews/README.md)
usan iluminación de estudio en Blender. La aceptación del shader en un cliente real queda pendiente.

## Coste de entidades y modo vanilla

Una guirnalda usa **dos displays por módulo**, compartidos por todos los jugadores: uno
para abeto/adornos y otro para sus ocho LED. No hay una entidad por hoja, bola o LED.
Con la separación de un bloque y la caída predeterminadas, un tramo horizontal de diez
metros usa **22 displays**. Cada tramo tiene como máximo 64 módulos/128 displays.
Cada lámpara normal usa un display para bombilla/casquillo y otro para su cuelgue.
El cable común usa de 1 a 32 segmentos por tramo, según la curva; no hay una entidad
por pieza del casquillo. Seis metros usan 13 displays en warm/rainbow, 21 en
wall-rainbow y 11 en lanterns con los valores predeterminados.

El presupuesto predeterminado es **4.096 displays en total, incluidas las fuentes RGB**.
Los topes opcionales `limits.max-strings` y `limits.max-strings-per-player` usan **0**
por defecto: no limitan la cantidad de tramos mientras haya presupuesto de entidades.
Los antiguos valores predeterminados de 256 tramos totales y 64 por jugador se migran
automáticamente con copia previa; otros valores personalizados se conservan. Los avisos
identifican cuál de los tres límites se ha alcanzado y su opción en `config.yml`.
Las uniones de pared que comparten bombilla no reservan entidades para la bombilla
ni el cuelgue omitidos. Los tramos de chunks descargados sí reservan su geometría.
XmasLights usa por defecto hasta 128 fuentes RGB por jugador y 512 portadores totales,
si queda presupuesto. Al bajar el límite se ocultan los tramos sobrantes y se conservan
sus datos. Las antiguas opciones de rendimiento que aún tenían exactamente los valores
predeterminados se migran al perfil nuevo, con copia previa. Los ajustes personalizados
distintos se conservan.

Si hay apoyo opaco detrás de un módulo, se selecciona un modelo sin adornos ni LED del
lado tapado: **112 caras frente a 186** en el modelo completo. Se comprueban el centro y
las cuatro esquinas del apoyo; no se recorta por cristal, hojas, huecos o chunks descargados.
Las comprobaciones se cachean y actualizan al cambiar bloques cercanos o cargar entidades,
con cola gradual. El cambio de modelo conserva los mismos dos displays.

Para un servidor que prefiera jugar sin el pack de XmasLights:

```yaml
model-pack:
  enabled: false
```

Ejecuta `/xl reload`. El modo vanilla usa hojas y luces de materiales de Minecraft,
conservando caída, efectos y los mismos dos displays por módulo. Es una representación
simplificada; los colores se limitan a los materiales vanilla. Es una **opción global del
servidor**, sin duplicar versiones por jugador. XmasLights deja de enviar su pack y retira
su entrada gestionada de Nexo, con copia anterior; otros packs de Nexo siguen siendo suyos.
El render RGB de superficies continúa requiriendo StrobeLights y su pack.

`red`, `green`, `blue`, `yellow`, `orange`, `christmas`, `wall-christmas` y `wall-yellow`
ya no usan las cabezas antiguas de los diseños integrados. Se migran las definiciones que
conservaban esas texturas originales; los tipos con `custom: true` o cabezas diferentes
se respetan. Los kits antiguos de esos diseños se actualizan al entrar o recargar,
conservando la cantidad. El [nuevo icono del pack](model-pack/pack.png) se incluye en el ZIP.

## Idiomas y configuración

Se incluyen **inglés (predeterminado), español, francés, italiano y alemán**.
Los catálogos están en `plugins/XmasLights/lang/en.yml`, `es.yml`, `fr.yml`, `it.yml` y `de.yml`.
Incluyen chat, ayuda, errores propios del plugin, registros, nombres de tipos, efectos y
descripciones de los kits. Los detalles originales de errores de YAML, Java o Paper se
conservan dentro de los diagnósticos para poder identificar la causa.

```yaml
language:
  default: en
  use-player-locale: false
```

Usa `default: es` para español, o activa `use-player-locale` para respetar el idioma del
cliente cuando esté disponible. Los clientes con otro idioma y la consola usan el idioma
predeterminado. Los kits ya entregados conservan su texto; los nuevos y reembolsados
usan el idioma de quien los recibe.

Puedes editar todos los textos y los colores `&` de los catálogos. Conserva las claves y
los marcadores como `{count}`; se validan durante la recarga. Las claves nuevas se añaden
automáticamente, sin sustituir tus textos, incluso si el número de versión ya coincide.

`config.yml` explica unidades, rangos, efectos, luz real y límites de rendimiento.
`types.yml` documenta cómo crear tipos nuevos. Sus identificadores no cambian entre idiomas;
los comandos (`give`, `mode`, `all`, `none`, etc.) mantienen identificadores estables para
scripts y autocompletado. Usa `/xl types` para consultar los identificadores disponibles.

En un tipo, `name-key` elige una traducción y `name` permite un nombre literal personalizado.
Cambiar el `name` original de un tipo integrado tiene prioridad sobre su traducción.
Para un tipo nuevo traducible, usa `name-key: names.mi-tipo`, omite `name` y añade esa clave
a cada catálogo. `custom: true` fuerza el nombre literal. Los tipos existentes no reciben
automáticamente nuevas listas `heads`/`blocks`, salvo la migración de los ocho diseños
integrados de cabezas indicada arriba.
Los tipos integrados ausentes se añaden de nuevo; define un reemplazo para personalizarlos.

Después de editar, ejecuta `/xl reload`. Las luces visibles se reconstruyen gradualmente
cuando cambian su geometría o su tipo. Los chunks descargados se actualizan cuando vuelve
a cargarse la zona completa. No se fuerzan cargas de chunks. Si cambias el estilo de una
guirnalda antigua de colgante a pared, vuelve a colocarla para elegir la cara de la pared:
el JAR antiguo no guardaba esa orientación para luces colgantes.

Una recarga inválida mantiene la configuración activa anterior. Un `lights.yml` inválido
impide el arranque y no se sobrescribe al desactivar el plugin. Cada guardado correcto deja
`lights.yml.bak` con el estado anterior; las migraciones de esquema generan además copia fechada.
Para volver a 2.1, restaura sus copias: no rebajes a mano los números de versión.

## Variantes de pared

Cada uno de los 17 diseños tiene su variante `wall-*`, como en `/xmas give wall-warm`.
Las variantes de pared llevan `style: wall` y `sag: 0`: cable recto, bombillas con
su pequeño cuelgue y el mismo desplazamiento respecto al apoyo. Los diseños sin
prefijo conservan su caída. Hay **51 tipos integrados** y todos funcionan con el
pack predeterminado o con la alternativa vanilla.

```text
/xmas give wall-warm
/xmas give wall-christmas
/xmas give wall-garland-rgb
/xmas give wall-soul-lanterns
```

Se añade `wall-` al identificador de cualquier diseño: `warm`, `multicolor`,
`red-white`, `rainbow`, `christmas`, `red`, `green`, `blue`, `yellow`, `orange`,
`lanterns`, `soul-lanterns`, `mixed-lanterns`, `garland-classic`, `garland-snowy`,
`garland-rgb` y `garland-draped`. `/xl types` muestra el catálogo traducido.

Las 17 variantes **`wall-inline-*`** sitúan la bombilla en el centro del cable, sin
ramal vertical. Por ejemplo: `/xmas give wall-inline-warm`, `wall-inline-christmas`,
`wall-inline-rainbow` o `wall-inline-lanterns`. Hay una para cada diseño. Los farolillos
se alinean por el centro de su cuerpo, y el abeto conserva su cable interior.
Seis metros de `wall-inline-warm` usan **11 displays**, frente a los 21 de `wall-warm`.
Consulta [la comparación visual](previews/09-wall-inline-comparison.png).

El comando `straight` y su preferencia de sesión se retiran. Los tramos ya guardados
con ese atributo conservan su forma. Para un tipo personalizado puedes seguir usando
`sag: 0`; las definiciones `custom: true` y una caída indicada explícitamente se respetan.

## Guardar y colocar con DecoSwap

Instala **XmasLights 2.8.5 y DecoSwap 0.1.18** de esta entrega, ambos para la versión
del servidor. La integración es opcional: XmasLights funciona sin DecoSwap.
Al seleccionar una bombilla o una parte del abeto y guardar en DecoSwap, se incluye
el tramo completo. Las piezas deben estar cargadas y necesitas ser propietario o
tener `xmaslights.manage-all`; se conserva el límite de selección de DecoSwap.

El guardado conserva tipo/paleta configurada, caída, orientación, estado, animación,
grupo y relación de cadena. Colocar una copia, también girada, crea identificadores
independientes y no duplica los emisores técnicos RGB ni guarda LIGHT blancos como
bloques permanentes. Las copias no devuelven kits al borrarlas. Recargar o cambiar de
renderer actualiza también las entidades registradas en el diario de DecoSwap.
Pack/hide y restore/rollback retiran el tramo lógico y sus efectos. Aceptar o separar
una decoración conserva las luces como contenido permanente.

Los tipos personalizados deben existir en `types.yml` del servidor de destino.
Las decoraciones guardadas antes de esta integración deben guardarse de nuevo:
se rechaza colocarlas como una copia de displays sin comportamiento.
Consulta [el contrato, instalación y pruebas](docs/DECOSWAP.md).

## Uso y permisos

`/xmaslights`, `/xl` y `/xmas` muestran la ayuda traducida y filtrada por permisos.

| Permiso | Función | Predeterminado |
| --- | --- | --- |
| `xmaslights.use` | Colocar kits y gestionar las luces propias | Operadores |
| `xmaslights.manage-all` | Gestionar luces de otros, usar grupos/`all` como destino y asignar propietarios | Operadores |
| `xmaslights.manage-groups` | Crear, editar, consultar y renombrar grupos compartidos | Operadores |
| `xmaslights.admin` | Entregar kits, guardar, recargar; incluye los tres anteriores | Operadores |

Ejemplo: `/xl give warm 16`, clic derecho en dos puntos y después más clics derechos para
extender la cadena. Clic izquierdo termina la colocación. Sin colocación pendiente, clic
izquierdo retira un tramo y agacharse retira la cadena entera. `/xl chain` cambia este modo.
`/xl mode blink` aplica un efecto a la cadena que miras. `/xl group` explica los grupos.
Los grupos son compartidos, no un sistema de parcelas. Borrar un grupo con luces exige
`xmaslights.manage-all` y repetir el comando con `confirm`.

El plugin respeta las denegaciones de interacción y las roturas/explosiones canceladas.
No implementa una integración específica con cada sistema de protección de parcelas;
comprueba esa integración en tu servidor antes de conceder permisos de colocación generales.

## Estructura y verificación

```text
pom.xml, mvnw, mvnw.cmd, .mvn/  Compilación reproducible
build.ps1                      Compilar y ejecutar pruebas en Windows
src/main/java/                 Código editable y mantenible
src/main/resources/            plugin.yml, config.yml, types.yml y lang/
src/test/java/                 Pruebas de regresión y servidor simulado
docs/                          Compatibilidad, revisión y pruebas manuales
model-pack/                    JSON, texturas y definiciones de modelos editables
tools/                         Generación del pack, renders y archivos de entrega
previews/                      Renders de los modelos y escena Blender
original/                      JAR original y referencia descompilada
target/                        JAR compilado e informes de pruebas
```

La verificación automatizada incluye 177 pruebas. La compilación y las pruebas no sustituyen
la validación visual con un cliente Minecraft.
Consulta el [informe de revisión](docs/REVIEW.md) y la [lista de comprobación en Paper](docs/TESTING.md).
