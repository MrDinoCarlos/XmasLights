# Revisión de XmasLights 2.8.2

## Procedencia

Entrada: `XmasLights-2.1.0.jar`, 59 498 bytes.
SHA-256: `1caa514ff1d6ac593519fe4876b94edc152b84318ded65764dedc336524045e3`.
Se revisaron las ocho clases principales, sus clases internas y los tres recursos YAML.
La referencia se obtuvo con CFR 0.152; el código mantenido es exclusivamente `src/main/java/`.
Se conserva el crédito de AstralicaStudio y se añade MrDino como coautor.
El JAR original no incluía una licencia; esta reconstrucción no inventa una licencia de redistribución.

## Cambios y motivos

| Área | Problema observado en la referencia | Cambio |
| --- | --- | --- |
| Código recuperado | Variables/tipos artificiales de descompilación, incluidos objetos confundidos con `Vector`, `ItemDisplay` o `CallSite` | Registro, gestor, comandos y listener reconstruidos como Java compilable; geometría y modos conservados y revisados |
| Internacionalización | Mensajes, ayuda, lore y registros en inglés incrustados | 177 textos por idioma; en/es/fr/it/de; claves estables, colores, idioma de cliente opcional y comprobación de marcadores |
| Recursos | Actualización condicionada solo por versión; renovación destructiva de tipos integrados | Adición de claves ausentes en config/idiomas; tipos nuevos sin sustituir diseños existentes; copias fechadas |
| YAML y datos | La carga permisiva podía devolver datos vacíos tras un error y sobrescribirlos al guardar | Lecturas estrictas; bloqueo de guardado si falla la carga; esquema versionado; reemplazo atómico y copia anterior |
| Propiedad | Permiso de uso compartía control de todas las luces | UUID del propietario, permiso separado para gestión global y asignación de cadenas antiguas |
| Objetos | Se podían reembolsar tramos creados sin consumir un kit si se cambiaba la configuración | Registro `refundable` solo al consumir en supervivencia; cantidades divididas en pilas válidas; borrado persistido antes del reembolso |
| Bloques compartidos | Cada bombilla escribía por separado; al apagar o borrar podía interferir con otras | Máximo de las intensidades por bloque y restauración solo al quedar sin referencias |
| Luz ajena | Se aceptaban bloques LIGHT preexistentes como propios | Solo se reclama aire reemplazado por el plugin; se respeta cualquier bloque cambiado por otro actor |
| Chunks | Limpieza de luz en zonas descargadas podía cargar chunks o dejar residuos sin seguimiento | Registro persistente de bloques propios; limpieza diferida sin forzar carga y validación previa a crear geometría |
| Recarga | Cambios parciales y geometría con firma incompleta | Configuración activa anterior retenida tras rechazo; recarga de cinco catálogos; firma incluye caída y separación de bombillas |
| Rendimiento | Valores sin límite superior y potencial de demasiadas entidades | Rangos finitos, números enteros donde corresponde, límites por jugador/servidor/tramo/entidades y actualizaciones graduales |
| Texturas | URL arbitraria y base64 sin validar | Solo `textures.minecraft.net/texture/<hash>`; sin consultas de perfiles ni solicitudes HTTP del plugin |
| Eventos | Interacciones de kits sin respetar denegaciones de bloques | Respeto a `useInteractedBlock() == DENY` y a roturas/explosiones canceladas; doble mano filtrada y clics limitados |
| Migración antigua | Escrituras parciales y sugerencia de eliminar los originales | Preparación previa, rollback de archivos recién instalados, copias originales conservadas y detección de LucesNavidad instalado |
| RGB opcional | Las bombillas solo producían luz normal de Minecraft | Adaptador al render instalado de StrobeLights, con detección de host, pack y preferencia gráfica; sin registro de strobes |
| Guirnaldas | No había abeto con adornos | Tres modelos originales y cuatro tipos; cuerpo con sombreado natural y LED separado para animación |
| Modelos en clientes sin pack | Un modelo ausente produce errores visuales | Visibilidad por jugador, comprobación de carga, rechazo de colocación sin pack y ocultación al descartarlo |
| Pack compartido | Otro envío podría reemplazar los recursos del render | Pack propio aditivo; API pública de overlay de TRP cuando está disponible; sin shaders ni archivos de otros namespaces |
| Entrega automática | Sin proveedor compartido había que publicar el ZIP a mano; Nexo solo se utilizaba a través de TRP | Entrada directa de Nexo preparada en `onLoad`, generación serializada y entrega por su API; alojamiento HTTP integrado como alternativa automática |

En 2.3.1 Nexo tiene prioridad y recibe una entrada estable bajo `pack/external_packs`.
No se modifican sus ajustes ni otros packs. Las actualizaciones son atómicas, con copia
anterior cuando cambian los bytes. Una recarga no repite una generación idéntica y conserva
la necesidad de regenerar si todavía se estaba esperando a otra generación. Cerrar el plugin
invalida las tareas y respuestas pendientes. Solo se acepta como prueba de carga el UUID
del pack publicado por Nexo; la descarga de un pack ajeno no revela los modelos.

El alojamiento alternativo sirve un único ZIP inmutable desde memoria, con GET/HEAD,
SHA-1 y caché por contenido. No permite rutas arbitrarias ni escrituras y usa un número
acotado de trabajadores y solicitudes en cola. Se cierra al desactivar o cambiar a otro
proveedor. La dirección y el puerto públicos deben ser accesibles desde los clientes.

## Geometría, colores y rendimiento en 2.5

Se conservan los identificadores de los veinte tipos originales. La revisión 2.5 cubre
los 24 tipos: ocho familias de cubos con casquillo, ocho de bombillas redondeadas, cuatro
de farolillos vanilla y cuatro de abeto. Los ocho tipos que antes usaban cabezas ahora
usan bombillas pequeñas con textura blanca y tinte RGB: red,
green, blue, yellow, orange, christmas, wall-christmas y wall-yellow. Las definiciones
que conservaban las texturas integradas se migran con copia previa; se respetan las
cabezas diferentes o marcadas custom. Los kits antiguos cambian de material sin perder
cantidad ni identificador. La aproximación del cable usa
la caída y tolerancia reales, con un solo segmento para líneas rectas. El límite de
entidades cuenta esa geometría en lugar de reservar treinta y dos segmentos para cada cable.
Las cabezas y los modelos de objeto se reutilizan desde caché.

Cada módulo de abeto usa dos ItemDisplay: cuerpo de 41 elementos/138 caras y ocho LED
en hélice, de 48 caras en total. Hay abeto y adornos por ambas caras. El tamaño longitudinal
se adapta a la curva; también los contornos de abeto tienen caída (0.16 por defecto),
con 0.20 en draped. Las demás luces de pared también usan la curva de `sag` y cuelgues conectados al casquillo.
Un `sag: 0` explícito conserva un contorno recto. Los farolillos tienen cadena vanilla;
los demás cuelgues son cable fino. Las caras expuestas de los escalones de bombillas y
bolas se conservan para evitar huecos vistos desde arriba/abajo.

El apoyo opaco permite seleccionar modelos sin la decoración del lado tapado. El cuerpo
se reduce a 29 elementos/88 caras y el LED a cuatro elementos/24 caras: 112 caras frente
a 186, con las mismas dos entidades. Se comprueban cinco puntos de cada cara, sin forzar
chunks, y se actualiza una caché tras eventos cercanos o carga de entidades. La cola
respeta `updates-per-tick`; no se consulta la pared continuamente en cada animación.

La separación integrada sube de 0.75 a un bloque. Diez metros horizontales con caída 0.16
usan once módulos/22 displays. El máximo por tramo es 64 módulos y el presupuesto global
predeterminado es 4096 displays, incluyendo portadores RGB. Al reducirlo se oculta el
exceso conservando los datos. Los límites RGB bajan a 16 por jugador/128 globales,
condicionados al espacio disponible en el presupuesto compartido.

`model-pack.enabled: false` cambia globalmente a dos BlockDisplay por módulo, con hojas
de abeto/pálidas y luces de materiales vanilla. Sustituye la representación; no crea
una segunda copia por jugador. Se retira solo el ZIP gestionado de la entrada de Nexo,
con copia previa, y se solicita que actualice su pack si estaba activo. No se envía un
pack propio de XmasLights en este modo. Los colores vanilla son los del material.

Las actualizaciones de luz normal evitan recorrer/escribir luces estáticas cada fotograma,
con reconciliación periódica y actualización al cambiar estado. Las fuentes RGB solo se
calculan cuando hay espectadores con pack, cerca de cadenas visibles. Se muestrean sin
perder colores alternos, se seleccionan las más cercanas y se aplican presupuestos por
jugador y globales. Los portadores del render son privados, no persistentes y se reutilizan
al cambiar color o apagarse. No fuerzan carga de chunks y se retiran al desconectar o
desactivar la dependencia. No se accede al gestor/editor de StrobeLights ni se escriben sus datos.

Las denegaciones propias del plugin son traducibles. Los detalles de excepciones producidas
por Java, SnakeYAML o Paper se incluyen tal como los devuelve la biblioteca.

## Corrección del render RGB en 2.5

Se verificó el protocolo del JAR instalado y se observó con el depurador que el apagado
anterior enviaba tinte `0`, perdiendo la firma del portador. El nuevo apagado conserva
`0xA00005` con energía cero. La expansión 0.5 anterior podía iluminar varios bloques
alrededor del propio modelo. Ahora se emite junto a una colisión real de bloque y se
limita el radio con la fórmula efectiva del shader, distancia y separación del modelo.
Se consultan primero los candidatos cercanos, con presupuesto de sondeos compartido,
caché acotada y selección estable. Los cruces pequeños de chunks no cambian el anclaje.
Los metadatos se inicializan antes de mostrar el portador. No se crean strobes ni
se modifica StrobeLights, TRP, Nexo o sus shaders.

Las pruebas cubren colisiones de suelo/pared/losa, ausencia de impacto en aire o chunks
descargados, radio que no alcanza el modelo, atenuación, caché y trabajo acotado,
continuidad al cruzar chunks, selección estable, suelo constante mientras los LED parpadean
y seguimiento opcional de efectos conservando UUID durante apagados. La prueba visual
del shader en Minecraft sigue pendiente; las imágenes nuevas son inspección de geometría.

## Historial: prioridad de render y tramos rectos en 2.6

La prueba con depurador reprodujo cuatro LIGHT de nivel 12 activos a la vez que dos
portadores RGB de StrobeLights. El renderer compatible tiene ahora prioridad global:
se retiran solo los bloques propios y no se vuelven a crear hasta perder/desactivar
la integración. No se usa la carga de pack individual para esa decisión compartida.
La alternativa se restaura sin regenerar displays; las limpiezas pendientes en chunks
descargados conservan su registro de propiedad.

`/xl straight` admite on/off, cadena mirada, grupo/all con permisos y preferencia `new`
por sesión. El indicador recto se hereda al extender cadenas, se guarda en datos de
versión 3 y entra en la firma de geometría. Regenerar o cambiar opciones de caída no
deshace la elección del tramo. Restaurar la curva valida de nuevo el límite de entidades;
un rechazo conserva la geometría y elección previas. Los tramos ocultos no se muestran.
Se añaden ayuda, uso y respuestas a los cinco catálogos; se conservan textos personalizados.

La configuración pasa a 7 y los catálogos a 5. Solo se actualizan comentarios reconocidos
de la plantilla sobre prioridad de render; se conservan notas personales y valores.
La preparación temprana de Nexo admite el nuevo esquema. Los modelos no cambian en 2.6:
las imágenes de geometría de 2.5 siguen siendo aplicables.

## Cambios de 2.7

34 tipos: los 17 diseños disponen de variante `wall-*` con cable recto y cuelgues.
Se retira el comando `straight`; se mantienen los atributos ya guardados. Tipos v7,
configuración v8 y traducciones v6 se actualizan con respaldo y preservan personalizaciones.

RGB: el límite de separación apagaba los impactos cercanos a paredes. Se elimina,
manteniendo el impacto sobre la superficie real y un techo de radio de 3.25 bloques.
Intensidad 0.90, distancia de atenuación 12, alcance de selección 96, margen de salida 8,
histéresis 6 y 32 fuentes por jugador; 128 portadores totales y presupuesto global 4096.
El abeto recibe un mínimo visual configurable de 5/15, sin LIGHT ni nuevas entidades.
Las pruebas de 32/64 bloques cargan el chunk del jugador, como hace Paper en el servidor.

La integración opcional requiere también DecoSwap 0.1.18, cuyo código se entrega aparte.
Se guarda el tramo completo, con datos lógicos y sin emisores RGB. Se generan UUID propios
por colocación, se conserva el giro/animación/grupo y se actualiza el diario al regenerar.
Pack/hide y rollback eliminan datos lógicos y efectos; aceptar/separar conserva las luces.
Los marcadores persistentes de aceptación distinguen una retirada de una conservación
si XmasLights estaba desactivado. Datos de luces v4 con respaldo automático.

El catálogo Blender se regenera a partir de las matrices de los 34 diseños. No valida
la composición GPU ni garantiza ausencia de parpadeo en todas las configuraciones del cliente.

## Cambios de 2.8

Se reproduce el problema visual usando los shaders reales de StrobeLights 0.10.37 en
una GPU NVIDIA: señales 3x3 superpuestas, colores corruptos y fuentes perdidas con
32 luces densas a 24..64 bloques. El transporte nuevo usa canales fijos, datos XYZ
independientes y paquetes opacos validados, también sobre geometría de items existente.
La extensión se implementa en StrobeLights 0.10.38 y se inserta en el shader híbrido
de TRPServerEdition 0.4.19 sin reemplazar su código de haces. Se conserva el protocolo
v5 de las luces nativas. XmasLights comprueba la capacidad antes de enviar datos.

La intensidad predeterminada baja a 0.55, el radio a 2.25, la atenuación física a 8 y
el rango sube a 192 sin atenuación por distancia de cámara. Las fuentes estables usan
menos ganancia y caída cuadrática; se evita buscar tokens de sombra por cada fuente
estable y píxel. Los cambios de selección reutilizan entidades y canales.

17 tipos `wall-inline-*` completan el catálogo de 51, con luces centradas en el cable
y sin ramales. Los farolillos se alinean por su cuerpo luminoso. Configuración v9,
tipos v8, idiomas v7; datos de luces v4 y API DecoSwap sin cambios. Se conservan los
ajustes personales y se migran solo los valores reconocibles de 2.7 con copia previa.
El [informe GPU](STABLE_RGB.md) separa estas comprobaciones de la aceptación en Minecraft.

## Corrección de 2.8.1

El servidor de pruebas tenía StrobeLights 0.10.37 junto a XmasLights 2.8.0; el rechazo
del protocolo activaba luz blanca vanilla. La selección de render ahora depende
del host habilitado, sin LIGHT blanco durante un rechazo de versión/pack. Se
conserva el brillo del abeto y se eliminan los casquillos de los modelos inline.
Configuración v10, idiomas v8 y los mismos datos de luces v4. Consulta [el diagnóstico](RGB_FIX_2_8_1.md).

## OFF y efectos en 2.8.2

Se confirma con el depurador que CHASE cambiaba la paleta con `on=false`
(variante 2 a 3 en el paso 15) y que el RGB se mantenía fijo por defecto.
OFF ahora congela colores, entra en reposo y retira portadores RGB y LIGHT propios.
El encendido retoma el modo guardado; los intervalos oscuros de un modo ON conservan
sus portadores para evitar parpadeo por respawn. La configuración v11 habilita
`follow-effects` por defecto y migra el antiguo ajuste con comentarios originales.
Los nuevos casos cubren todos los modos, ambos renderers, pausa diurna y recuperación.
Consulta [OFF y efectos](OFF_AND_EFFECTS.md).

## Validación

La entrega se verifica con `./build.ps1` / `./mvnw.cmd clean verify` y compilación de IntelliJ.
Resultado de esta entrega (10 de noviembre de 2026): **177 pruebas, 0 fallos, 0 errores,
0 omitidas**; `clean verify` correcto con JDK 21 y reconstrucción completa de IntelliJ correcta.
Los informes se generan en `target/surefire-reports/`. Las pruebas cubren recursos,
permisos, creación y borrado, datos 2.1, actualización de tipos personalizados, rechazo de
recarga, seguridad de texturas/coordenadas, límites y superposición/limpieza de luz.
También comprueban el protocolo RGB, ausencia de acceso al registro de strobes, presupuestos,
preferencia Native/Fabulous, visibilidad de modelos, descarte de packs y proveedor compartido.
Las pruebas nuevas descargan el ZIP por HTTP real en loopback y verifican su huella,
HEAD, rutas rechazadas y cierre del socket. El contrato simulado de Nexo cubre preparación
antes de activarlo, prioridad sobre TRP, generación pendiente, espera de publicación,
recarga durante la espera, cancelación y ausencia de generaciones redundantes.
La revisión 2.4 añade comprobaciones de RGB exacto en los objetos, sustitución de kits,
migración conservadora, modo vanilla sin descarga, cambio de renderer sin duplicados,
caída de contornos, 22 displays en diez metros, tope por tramo y global, recorte por pared
opaca, restauración sin respawn, cristal, apoyo delantero, chunks sin cargar, geometría
de ambas caras, LED en hélice e icono del pack.
La paleta RGB de los ocho LED se aplica por componente de tinte, sin añadir entidades.
Las paletas personalizadas pueden tener más colores que modelos de cabeza/bloque; se
completa el ciclo de ambas listas sin índices fuera de rango al colocar o animar luces.
Se ha ejecutado la comprobación del recurso de carrier contra el JAR local de
StrobeLights 0.10.38 para 1.21.4. Se entregan por separado su JAR y su proyecto editable,
junto al parche de TRP 0.4.19 para quienes lo tengan instalado. Los 432 casos de GPU
ejecutan los GLSL reales del JAR y la fusión del shader de TRP, sin perder ni corromper
señales y sin dejar colores técnicos en la composición final. Se comprueba además que
Nexo pueda reparar los includes del shader sin alterar sus metadatos ni sus otros assets.

MockBukkit 4.35.0 tiene limitaciones en estados de farolillos/hojas persistentes, datos `Levelled` de LIGHT,
visibilidad/billboard de displays, componentes modernos de objetos, hostname de entrada y
solicitudes de packs, colisiones de bloques y descomposición de transformaciones. El servidor de pruebas
aporta sustitutos explícitos y restaura los registros modificados al terminar cada prueba;
no se introducen excepciones en producción para satisfacer el simulador. La clase API de
Nexo de los tests es un sustituto mínimo que nunca se incluye en el JAR de producción.
No se ha ejecutado Nexo real ni su ofuscador; esa aceptación continúa pendiente.

Los renders 07/08 cargan las matrices exactas de los displays exportadas por las pruebas,
la geometría/texturas reales del pack y los modelos de farolillos/cadenas leídos de un
cliente local 1.21.4. Esos recursos de Minecraft no se incluyen en el pack del plugin.
Las pruebas de red usan puertos temporales para aislarse del servidor de desarrollo.
La iluminación es una simulación y no demuestra el resultado del shader de StrobeLights.
La apariencia, colocación, coste en FPS/TPS y protección de parcelas requieren la
[aceptación en Paper con un cliente real](TESTING.md), todavía pendiente.

## Revisión 2.8.4: RGB independiente de las preferencias de TRP

El log de producción del 11 de noviembre confirma StrobeLights 0.10.38 y TRP 0.4.19,
con el adaptador conectado, pero `StrobeLightsBridge.ready` consultaba el perfil de TRP.
En una prueba aislada con el depurador, `isFabulousRgbEnabled` devolvía `false` y no se
generaban portadores; al devolver `true` se creaba uno. El perfil predeterminado de TRP
era Native. El método `stableSourceProtocol()` de StrobeLights también agregaba la
capacidad de TRP y podía rechazar su propio pack compatible.

Se retiran ambas consultas del adaptador. XmasLights valida el modelo y la extensión
estable en el pack de StrobeLights, y conserva `resourcePack().isLoaded(player)`.
TRP mantiene sus propias preferencias y su función opcional de proveedor del pack.
No se cambian sus clases, shaders, datos ni ajustes de jugadores.

Las nuevas regresiones fallaron antes de la corrección y pasan después: perfil Native,
cambios de perfil y desactivación de TRP conservan el mismo portador y contenido RGB;
la capacidad agregada de TRP no veta el pack propio de StrobeLights; un pack pendiente
sigue impidiendo el envío, incluso con el perfil Fabulous de TRP. Se conserva el rechazo
de un pack sin extensión estable y la recuperación al restaurar el pack y recargar.

Validación de 2.8.4: **189 pruebas, 0 fallos, 0 errores, 0 omitidas**, `clean verify`
con JDK 21. Las pruebas focalizadas también usan el pack extraído del JAR real de
StrobeLights distribuido. La aceptación en el servidor real requiere sustituir el JAR;
los errores de descarga `failed_download` e `invalid_url` del log siguen siendo un
problema de entrega que esta corrección del adaptador no resuelve.

## Revisión 2.8.5: distancia de chunks y cara de las guirnaldas

El depurador confirmó `range=192.0` con una fuente a X=482.5 cuyo chunk ya estaba
enviado al jugador. La selección no creaba portadores. El modo automático ahora usa
los chunks realmente enviados, tanto en `LightManager.rgbSources` como en el adaptador.
Descargar un chunk retira sus fuentes aunque otro jugador lo mantenga cargado.
El límite explícito de bloques sigue disponible. Los antiguos valores predeterminados
se migran con copia a rango 0, 128 fuentes por jugador, 512 portadores y 64 sondas.

StrobeLights 0.10.38 también limitaba posiciones a ±512 y atenuaba las fuentes según
la cámara. La prueba GPU ampliada reprodujo un error de posición de 7.5 bloques por
ese recorte. En 0.10.39 el transporte cubre ±1024 y la iluminación de integración
conserva su energía sin el fundido de alcance nativo. Además, los paquetes incluyen
los coeficientes reales de profundidad: el cliente 1.21.4 usa un plano lejano de
cuatro veces la distancia de renderizado, no un 1024 constante. Los coeficientes se
guardan en canales libres de las filas existentes y se verifican sin pérdida de su
precisión original en GPU. El ABI del item y el número de filas no cambian; los
marcadores y luces nativas conservan su formato y sus ajustes.

Para las guirnaldas se comprobó el renderer del cliente 1.21.4: aplica Y=180° a los
ItemDisplay después de su transformación. Las sondas de pared eran correctas pero
el lado conservado del modelo quedaba invertido. La rotación derecha compensa ese
giro antes de la escala en los displays de abeto y LED. Las ocho combinaciones de
orientación/orden de anclaje pasan, y la firma `garland-facing=2` reconstruye también
las guirnaldas existentes. El exportador de vistas previas incorpora ahora el mismo
giro del cliente. No cambian sus puntos, UUID lógicos ni grupos.

Validación: **206 pruebas de XmasLights y 75 de StrobeLights**, sin fallos, errores
ni omitidas, con JDK 21 y el pack real de 0.10.39. **720 casos GPU** comprueban 32/128
fuentes, FOV 15/70/110, giros y distancias hasta 768 bloques; incluyen planos lejanos
1024/1920/2048, StrobeLights solo y la fusión de producción de TRP 0.4.19. No se
pierden ni corrompen fuentes; el error máximo de posición es menor de 0.0013 bloques.
Otros **60 pases de luz opaca/transparente** mantienen RGB no nulo a esas distancias;
la composición no filtra colores de los paquetes. Son escenas OpenGL sintéticas:
la aceptación visual y de FPS/TPS en el cliente y servidor reales continúa pendiente.

Se entregan ambos JAR nuevos y sus proyectos editables. El pack de modelos no cambia:
la corrección de la cara se aplica en las transformaciones del servidor. Sí hay que
regenerar/aceptar el pack de StrobeLights 0.10.39. TRP continúa siendo opcional y
XmasLights no consulta su perfil de gráficos ni su capacidad agregada.
