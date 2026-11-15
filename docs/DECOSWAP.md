# Integración con DecoSwap

Instala `XmasLights-2.8.0.jar` y `DecoSwap-0.1.18-paper-1.21.4-1.21.11.jar`
en Paper 1.21.4 con Java 21. Sustituye sus JAR anteriores, conservando las carpetas
de datos. XmasLights declara DecoSwap como dependencia opcional; se carga después
para consultar su diario al arrancar. Ambos plugins funcionan por separado.

## Uso

1. Coloca las luces normalmente, por ejemplo con `/xmas give wall-warm`.
2. Selecciona en DecoSwap una parte visible del tramo y guarda la decoración.
   Se añade el tramo completo. Las partes deben estar cargadas y pertenecer al
   jugador, o este debe tener `xmaslights.manage-all`.
3. Usa las operaciones habituales de DecoSwap para guardar, empaquetar, ocultar,
   colocar con giro y restaurar. Puedes incluir bloques de apoyo en la selección.

Una selección parcial incluye el tramo al que pertenece, sin arrastrar otros tramos
de su cadena. Para copiar varios tramos conectados, selecciona una parte de cada uno.
Los tramos incluidos conservan su relación de cadena dentro de cada copia.
No se admite una selección que exceda el límite de entidades de DecoSwap.

Los modelos colocados con versiones anteriores de XmasLights se actualizan una vez
antes de construir la selección, asignando roles estables a sus piezas. El tramo
conserva su identidad lógica; así el guardado admite posteriores regeneraciones.

Las copias conservan el identificador de tipo, paleta definida en `types.yml`, modo,
grupo, encendido, caída, variante de pared y orientación. Se generan UUID distintos
para cada colocación y se asigna el propietario que la coloca. No se devuelven kits
al romper copias. Los tipos personalizados deben existir en el destino; no se importan
configuraciones arbitrarias desde una decoración.

Los bloques LIGHT propios y los portadores `xmaslights:rgb_carrier` son efectos
temporales. Se excluyen del contenido permanente. En una copia se aplica el renderer
actual: pack o vanilla; RGB solo si StrobeLights es compatible y está habilitado.
No se recupera la luz blanca cuando StrobeLights proporciona el render.

Las decoraciones anteriores que solo guardaban displays deben guardarse otra vez.
Su colocación se rechaza con un mensaje antes de modificar el mundo. La retirada de
una decoración antigua sigue disponible para poder limpiar sus entidades.

## Persistencia y recuperación

El estado lógico viaja junto a cada pieza, en `_xmaslights.state` (protocolo 1,
máximo 16 KiB por pieza). Antes de colocar se valida la integridad del conjunto,
los roles únicos, tipos, coordenadas, altura, cantidad de tramos y presupuesto global.
La preparación reserva presupuesto de displays y reduce los portadores RGB antes de
crear las piezas; no añade un controlador invisible por tramo.

El diario de DecoSwap conserva las entidades nuevas cuando XmasLights las regenera.
Los identificadores locales negativos identifican piezas añadidas por el renderer;
las ubicaciones quedan registradas para cargar sus chunks al restaurar.
La animación y el tinte actual no provocan cambios falsos: la comparación usa el
estado lógico y la posición de cada pieza.

Pack/hide y restore/rollback eliminan también el registro de luces y sus efectos.
Una colocación fallida elimina sus registros parciales y conserva el mecanismo de
recuperación de DecoSwap para bloques y transacciones.
Aceptar una recuperación o separar una decoración conserva las luces como contenido
permanente. `transactions/retained/<UUID>` distingue esa decisión si XmasLights estaba
desactivado. Si DecoSwap está ausente al arrancar, se conservan los datos de luces;
no se adivina si una transacción fue restaurada o aceptada.

El formato de `lights.yml` pasa a versión 4, con respaldo antes de migrar.
Los datos ya guardados de `straight` siguen siendo compatibles aunque se retire el comando.

## Código y verificación

`DecoSwapSupport` expone solo tipos Bukkit/JDK. DecoSwap usa una integración aislada
por reflexión; no se incluyen clases de un plugin dentro del JAR del otro.
El código de DecoSwap se mantiene en su proyecto Gradle y se entrega con su licencia.
El parche adjunto recoge únicamente los cambios de esta integración respecto a la
copia local que ya existía antes de comenzar el trabajo.

Las pruebas de XmasLights cubren selección completa/permisos, dos copias independientes,
giro de paredes y anclas, animación/grupo, guardado y recarga, retirada sin huérfanos,
modo vanilla, datos incompletos/duplicados y exclusión de luz ajena. DecoSwap verifica
el protocolo público, los límites, comparación lógica y diario tras regenerar/reiniciar.
También se compila su matriz de API. Esto no sustituye la aceptación en un servidor
real con ambos JAR y un cliente con el pack RGB.

En Paper real: guardar dos tramos con grupo y animación, empaquetarlos, colocar dos
copias con giro, recargar ambos plugins y restaurar una. La otra copia y el original
deben seguir funcionando. Repetir con modo vanilla y con XmasLights desactivado
durante restore y durante aceptar/separar; al activarlo de nuevo no deben reaparecer
luces retiradas ni desaparecer las aceptadas.

Las variantes `wall-inline-*` también conservan tipo, orientación, paleta y efectos en las copias. No requieren actualizar DecoSwap 0.1.18.
