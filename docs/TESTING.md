# Pruebas 2.9.1: StrobeLights real

La entrega excluye TextDisplay, círculos y fuente radial. El ensayo de integración
instala StrobeLights 0.10.41, descarga y confirma su pack real y comprueba sus
ItemDisplay técnicos en suelo y pared. Sus payloads reales se pasan a
verify_strobe_gpu.py --runtime-sources para ejecutar los shaders propios en GPU.
Se repite con la textura preparada para OptiFine. Ver RELEASE_2_9_1.md.

La matriz verify_branches.py comprueba cada JAR de rama en todos sus parches con
mundos nuevos y cachés Paper verificadas. No demuestra por sí sola el render RGB
de packs StrobeLights de otras versiones.

La comprobación visual debe hacerse con el pack compatible y el pipeline que
StrobeLights soporte: Fabulous, y en la versión instalada sin shaderpacks externos.
No se registran escenas ni recepciones de TextDisplay como pruebas de iluminación.

## Lista anterior (referencia del renderer shader 2.8.x)

# Aceptación en un servidor Paper real

Para 2.8.6, instalar también StrobeLights 0.10.40 y aceptar el pack actualizado.
Repetir suelo/paredes en OptiFine J3, Fabulous, Shaders OFF, mipmaps 0/4 y con/sin
TRP 0.4.19. El [fallo del atlas](OPTIFINE_RGB_2_8_6.md) se reproduce automáticamente
antes de ejecutar los shaders en GPU. Revisar OFF/ON, efectos y movimiento de cámara.

Estado: pendiente. Las pruebas automatizadas usan un servidor simulado y no validan la
apariencia de los modelos ni el rendimiento de una instalación de producción.

Usa un mundo de prueba/copia, Paper 1.21.4 y Java 21. Anota el build de Paper, los plugins
instalados y la configuración al registrar resultados.

- Arranque limpio: siete archivos YAML creados en la carpeta del plugin (configuración,
  tipos y cinco catálogos), sin errores. El JAR incluye además `plugin.yml`.
- Actualización desde 2.1: grupos, efectos, cadenas, estado on/off y visibilidad conservados.
  Asignación con `/xl owner`; las luces antiguas no generan kits gratuitos.
- Los 51 tipos: abeto, cubos, bombillas y farolillos; probar paredes, suelos y techos.
  Revisar curva de cable, cuelgue unido al casquillo, colores y farolillo colgante.
  Con sag: 0 y chain: 0 explícitos debe poder conservarse un contorno recto personalizado.
- Comandos y kits en en/es/fr/it/de, idioma de cliente opcional y cliente no soportado.
  Confirmar colores, cantidades, ayuda, errores y nombres localizados.
- Editar textos, opciones y un tipo; `/xl reload`; conservar cambios tras reinicio.
  Eliminar una clave de idioma/config y comprobar que se repone sin perder otros textos.
- Introducir YAML inválido, valor fuera de rango y marcador incorrecto. Rechazar recarga
  manteniendo la configuración activa. En una copia, comprobar que datos inválidos detienen
  el arranque sin sobrescribir `lights.yml`.
- Encadenar y cancelar con ambas manos. Cambiar de kit, mundo y modo de juego; desconectar
  durante una colocación; verificar consumo/reembolso solo de kits pagados en supervivencia.
- Dos usuarios sin permisos globales: impedir cambios sobre luces ajenas. Administrador:
  grupos compartidos, mayúsculas/minúsculas, renombrado, borrado confirmado y cambio de dueño.
- Sistema de parcelas instalado: clic derecho/izquierdo denegado, rotura y explosión canceladas.
  Comprobar también un tramo que cruce el límite de una parcela; no hay integración nativa
  con un proveedor concreto de regiones.
- Luz real: superponer dos cadenas y apagar/borrar una; la otra mantiene su brillo.
  Un bloque LIGHT previo del mundo no debe modificarse. Si otro actor sustituye un bloque
  propio por un bloque sólido, no restaurar aire encima.
- Apagar/despawn, descargar chunks, borrar cadena/grupo, reiniciar y cargar la zona:
  sin entidades obsoletas ni bloques invisibles huérfanos. Revisar también mundo descargado.
- Siete efectos, noche/día, cambios progresivos y `animated-light: false`:
  la luz real estática sigue obedeciendo on/off y night-only.
- Probar los límites, recarga gradual y TPS con una cantidad representativa de luces.
  Cualquier versión posterior debe repetir esta aceptación antes de declararse compatible.

## Modelos y packs de 2.3

- Probar los cuatro tipos nuevos: clásico, nevado, RGB y colgante. En puertas, verificar
  la orientación al recorrer el contorno en ambos sentidos; en barandillas, revisar caída,
  continuidad, tamaño y uniones. Los adornos deben conservar sombras naturales y solo
  los LED deben cambiar su brillo con los efectos.
- Cliente sin pack: sin modelos ausentes visibles; colocación rechazada con aviso
  traducido. `/xl pack`, carga correcta y modelos visibles solo a ese jugador.
  Probar rechazo, fallo de descarga y descarte posterior del pack.
- Pack propio junto al de StrobeLights: aceptar ambos en distinto orden, reconectar y
  recargar. Los objetos vanilla y el render RGB deben conservar sus recursos.
- Nexo directo, con y sin TRP: primer arranque con `pack/external_packs/XmasLights-Models.zip`
  ya disponible antes de su generación. Confirmar modelos en el ZIP final, incluida la
  configuración de ofuscación utilizada, y su descarga por Nexo. Sin doble registro por TRP,
  sin listener propio y sin cambios en los demás packs o ajustes de Nexo.
- Nexo durante una generación: actualizar el plugin/recursos y recargar. Esperar a la
  generación anterior y a la publicación de la nueva. Una recarga sin cambios no debe
  regenerar; recargar mientras se espera no debe perder la actualización pendiente.
- Nexo: pack ajeno cargado durante la espera, rechazo, descarte posterior, reconexión y
  desactivación del proveedor. Con `auto-rebuild: false`, comprobar `/nexo reload pack`.
- Proveedor TRP sin Nexo: verificar ZIP final con recursos `xmaslights`; probar la descarga,
  descarte posterior y desactivación del proveedor.
- Alojamiento integrado: entrada automática y `/xl pack`, desde LAN y una conexión externa.
  Verificar puerto asignado/abierto, hostname de entrada y `public-url` tras proxy/NAT.
  Probar reinicio, cambio de puerto, puerto ocupado y URL externa; comprobar cierre del
  listener anterior y que las rutas ajenas al ZIP devuelven error.
- Comprobar URL externa, descarga con SHA-1 y el ZIP exportado.
  La instalación manual requiere confirmación administrativa mediante `assume-installed`;
  el servidor no puede detectar los packs instalados manualmente en el cliente.

## Revisión visual y rendimiento de 2.4

- Guirnaldas al aire: revisar frontal, posterior y laterales; adornos por ambas caras,
  ocho LED en hélice por módulo y caída también en contornos horizontales. Los verticales
  deben conservar la continuidad. Probar inversión de extremos y techos.
- Pared opaca completa detrás/delante: comprobar la variante recortada y la misma cantidad
  de entidades. Romper y recolocar el apoyo debe restaurar/recortar sin respawn de los
  displays. Cristal, hojas, bloques parciales y huecos deben conservar ambos lados.
- Colores: probar red, green, blue, yellow, orange, christmas y variantes de pared;
  bombillas pequeñas sin dibujos ni cabezas antiguas. Revisar RGB del catálogo y cambios
  de fase. Migrar datos y kits 2.1/2.3; conservar cantidades, UUID y cabezas personalizadas.
- `model-pack.enabled: false`: no pack propio, renderer con materiales vanilla, animación
  y caída conservadas. Alternar a true y volver: sin doble geometría ni datos perdidos.
  Con Nexo, comprobar retirada del único ZIP gestionado y conservación de otros packs.
- Medir entidades: diez metros horizontales de garland-classic usan once módulos/22
  displays con defaults; máximo 64 módulos por tramo. Límite global 4096 incluyendo RGB,
  fuentes privadas 16/jugador y 128/global. Reducir el presupuesto conserva los tramos
  ocultos; volver a mostrarlos exige capacidad libre.
- Confirmar el icono `pack.png` del ZIP y su descripción 2.8.2. En un pack combinado,
  el proveedor externo controla el icono y la identidad final de ese pack.
- Medir TPS/FPS con la carga real del servidor; las cifras de entidades/geometría no
  sustituyen un perfil de rendimiento ni una validación visual con Minecraft.

## Render RGB de StrobeLights

- Registrar versión exacta de StrobeLights y revisión de su pack. Sin el plugin activo
  o con la integración desactivada: alternativa vanilla si `light-blocks: true`.
  Con el plugin activo y una versión/pack incompatible: sin RGB, aviso y sin LIGHT blanco.
  Con host compatible pero sin pack aceptado: sin portadores para ese jugador y sin
  LIGHT de XmasLights para todos. Comprobar que el brillo del abeto se conserva.
- Con StrobeLights 0.10.39 para 1.21.4 y su pack: gráficos Fabulous, sin shaders externos.
  Si se instala TRPServerEdition, usar 0.4.19 para la extensión del shader combinado.
  Revisar color y posición en suelo, paredes y escalones, radio e intensidad. Confirmar que
  no aparecen strobes nuevos en su editor, datos persistentes ni destellos de cámara.
- Aplicar steady/blink/alternate/chase/twinkle/breathe y el resto de modos disponibles;
  comprobar on/off, cambio progresivo y night-only. `animated-light: false` afecta a
  la alternativa de bloques LIGHT; follow-effects decide si el RGB sigue esos efectos.
- TRP Native: el RGB de XmasLights debe seguir activo; cambiar el perfil o desactivar TRP
  no debe retirar sus portadores ni cambiar sus contenidos.
  Otro jugador puede usar un perfil distinto sin ver los portadores ajenos.
- Configurar 30 y 32 chunks tanto en la entrega del servidor como en el cliente,
  usar `strobelights.view-distance: 0` y comprobar 480 bloques y vistas en diagonal.
  Descargar el chunk de una fuente debe retirarla aunque otro jugador lo mantenga cargado.
- Comprobar guirnaldas en las cuatro paredes y ambos sentidos de colocación: los adornos
  y LED conservados miran hacia fuera; romper el apoyo restaura ambos lados.
- Distancia, mundo, reconexión, teletransporte y cambios de chunk: posición correcta sin
  forzar carga. Bajar presupuestos y comprobar fuentes cercanas, total de entidades y FPS.
  Desactivar StrobeLights debe retirar las fuentes técnicas; reinstalar una versión
  compatible y recargar debe permitir recuperarlas.
- Medir TPS y FPS con la carga habitual y varios jugadores. Los límites por defecto
  contienen las fuentes de XmasLights; no incluyen las creadas por StrobeLights por su cuenta.


## Aceptación específica de 2.5

- Con STEADY y sin mover al jugador, comprobar impactos pequeños sobre el suelo a 3/6/8
  bloques de altura; no debe lavarse el abeto con el color del propio proyector.
- Andar despacio entre dos luces cercanas al límite de 16 fuentes y cruzar X/Z=16;
  las fuentes retenidas no deben alternarse ni desplazarse al anclaje del jugador.
- Activar BLINK/TWINKLE/BREATHE: los LED animan; el entorno permanece estable con
  follow-effects: false y animated-light: false. Repetir con follow-effects: true
  para comprobar el parpadeo deliberado sobre bloques y el apagado sin recrear entidades.
- Colocar un suelo de losas y una pared opaca. Revisar el punto de impacto; romper
  el bloque y esperar hasta dos segundos a su reconciliación. No debe cargar chunks.
- Actualizar tipos/config 5 a 6: copia fechada, IDs/grupos conservados, cubos y cuelgues
  nuevos en tipos integrados reconocidos; respetar custom: true y valores explícitos.
- Al cambiar el ZIP, esperar la regeneración/publicación de Nexo y aceptar el pack nuevo.
  Comparar geometría con previews/07-cables-and-cubes.png y 08-all-light-types.png.


## Aceptación específica de 2.6

- Actualizar con tramos colocados y LIGHT propios guardados: StrobeLights compatible debe
  retirar esos LIGHT y conservar los modelos. Repetir tras cargar un chunk pendiente.
- Desactivar/activar StrobeLights: vuelve/desaparece la alternativa blanca, sin duplicar
  modelos ni tocar LIGHT ajenos. Repetir con integración desactivada desde config.
- Dos clientes: uno con RGB y otro sin pack o en TRP Native. La entrada/desconexión del
  segundo no puede encender luz blanca para el primero.
- Probar `/xmas give wall-warm`, `wall-christmas`, `wall-garland-rgb` y todos los
  demás `wall-*`: cuerda recta, cuelgues cortos y apoyo correcto. Los tipos sin prefijo
  mantienen caída. `straight` ya no aparece en ayuda ni autocompletado.
- Verificar un mínimo suave en el abeto (5/15), apagado y configuración a 0.
- Comparar impactos de pared muy próximos y suelo con 0.90 de intensidad, radio máximo
  3.25 y atenuación de distancia 12. Caminar y observar a 32/64 bloques; probar también
  el desvanecimiento al límite de 96+8. Revisar el GPU real con Fabulous y su pack.
- Repetir la [aceptación de DecoSwap](DECOSWAP.md), incluidos giro, dos copias, cambio
  de renderer, reinicio y restauración con XmasLights desactivado.

## Aceptación específica de 2.8

- Instalar XmasLights 2.8.2 y StrobeLights 0.10.38. Si se usa TRP, actualizarlo a 0.4.19.
  Regenerar Nexo o esperar la entrega automática y aceptar el pack nuevo.
- En Fabulous, con OptiFine sin shaderpack externo, caminar y girar ante varias líneas
  densas. Comprobar colores, ausencia de destellos grandes y continuidad a 32/64/128 bloques.
- Comprobar que la energía permanece estable al alejarse dentro del rango y que el
  radio sigue el impacto físico, sin extender color a toda la escena.
- Probar `wall-inline-warm`, `wall-inline-christmas` y los tres farolillos: el cable pasa
  por el centro y no hay ramal. Probar las 17 variantes y copiar alguna con DecoSwap.
- Comparar con el [informe GPU](STABLE_RGB.md); sus escenas sintéticas no sustituyen
  esta aceptación con el cliente, los packs y la configuración reales.

## Aceptación de OFF y efectos en 2.8.2

- Con CHASE activo, apagar el tramo y comprobar colores inmóviles, brillo apagado y
  ausencia de impactos RGB. Cambiar de modo mientras está OFF no anima el modelo.
- Encenderlo y comprobar que se aplica el modo guardado sin reconstruir sus modelos.
- Con `follow-effects: true`, comparar BLINK/BREATHE/CHASE en el modelo y el impacto.
  Las fases oscuras de BLINK conservan las entidades RGB; OFF las retira.
- Repetir con modelos vanilla y con `night-only`, pasando de noche a día y de vuelta.
- Comprobar que el abeto ON conserva su brillo y que OFF usa la iluminación natural.
- Confirmar configuración v11 y migración del antiguo `follow-effects: false` de fábrica.
  Un `false` deliberado con comentarios personalizados debe permanecer intacto.
