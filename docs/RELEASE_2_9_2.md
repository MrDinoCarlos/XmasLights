# XmasLights 2.9.2 — RGB con StrobeLights actualizado y Paper 26.3

Actualización de compatibilidad: **usar StrobeLights 0.10.44 en 26.3**. El contrato
se incorporó en 0.10.43, pero el cliente eliminaba su modelo puntual al hornearlo.
0.10.44 corrige el modelo y verifica los colores en Minecraft vanilla real;
XmasLights 2.9.2 sigue siendo la misma versión compatible.

El aviso que pedía StrobeLights 0.10.38 aparecía incluso con 0.10.42 porque el
bridge aceptaba solo 0.10.38..0.10.41. La versión nueva valida el contrato real
del pack de la versión de Minecraft, el item model y su tinte. Una futura versión
con la misma ABI no necesita modificar una lista; un pack sin ABI sigue rechazado.
El antiguo ajuste `allow-untested-version` queda obsoleto, sin evitar la validación.
Los avisos de incompatibilidad indican ahora el requisito correcto para 26.3.

Además hay que instalar **StrobeLights 0.10.44**: conserva los 128 slots de
integración introducidos en 0.10.43 y corrige el horneado del modelo. Conservar
0.10.42 y actualizar solo
XmasLights no resuelve la iluminación. TRP no es necesario y su perfil gráfico
no interviene en la negociación. No se crean strobes, círculos ni TextDisplay;
se mantienen los ItemDisplay técnicos y los shaders de StrobeLights.

## Pruebas

Pasan 232 pruebas Maven, sin fallos ni omisiones. Incluyen un pack real de
StrobeLights 0.10.43 para 1.21.4, aceptación de una versión futura con la misma
ABI, rechazo del pack sin contrato y conservación de la política de LIGHT.
Se generan los cuatro JAR por familia con sus descriptores y packs de modelos.
No se ha repetido la matriz completa de doce servidores de la entrega 2.9.1.

La prueba aislada arranca Paper 26.3 con ambos JAR finales, conecta un cliente
de protocolo, descarga el ZIP de StrobeLights y verifica su SHA-1. Recibe siete
carriers, con fuentes sobre suelo y pared; no recibe TextDisplay. El servidor
confirma que XmasLights usa el renderer RGB de StrobeLights 0.10.43. Evidencia:

```text
.tools/xmas-strobe263-292-01043-verified/server.log
.tools/xmas-strobe263-292-01043-verified/player-passed.txt
.tools/xmas-strobe263-292-01043-verified/client-packets.json
.tools/xmas-strobe263-292-01043-verified/client-transport.json
.tools/xmas-strobe263-292-01043-verified/strobe-runtime-sources.json
```

El bot disponible usa protocolo 1.21.4; ViaVersion/ViaBackwards 5.12.2 traducen
su conexión al servidor 26.3 solo en este ensayo local. No son dependencias de
XmasLights. La descarga/confirmación del pack no demuestra ejecución gráfica
en ese bot. StrobeLights ejecuta aparte 38 comprobaciones GPU con sus GLSL 26.3,
incluidos todos los bancos, los colores RGB y fuentes nativas e integración
simultáneas; también pasan sus 583 pruebas Java y 22 variantes GLSL.
No se ha realizado una sesión visual completa de Minecraft 26.3, OptiFine o Iris.

Ejemplo de repetición, usando una caché de Paper con EULA previamente aceptada:

```text
python tools/verify_player_sources.py --work-name <carpeta-nueva> --minecraft 26.3 --client-version 1.21.4 --test-plugin <ViaVersion.jar> --test-plugin <ViaBackwards.jar> --strobe-jar <StrobeLights-0.10.43+mc.v.26.3.X.jar>
```

## Actualización

Parar Paper, sustituir los dos JAR de la familia 26.3.X y arrancar conservando
las carpetas de datos. No dejar dos versiones del mismo plugin en `plugins/`.
Aceptar el pack nuevo de StrobeLights al entrar. No hay que borrar las cadenas,
los diseños ni la configuración, ni activar una excepción de versión.
