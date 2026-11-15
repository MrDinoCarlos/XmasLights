# XmasLights 2.9.1: render exclusivo de StrobeLights

Se elimina el renderer portátil de 2.9.0: método updateDecal, uso de TextDisplay,
fuente radial, máscara y selector de renderer. La configuración 16 retira la clave
antigua incluso si tenía `portable`; no puede volver a activar esa ruta.

El adaptador vuelve a generar solo ItemDisplay con `strobelights:carrier/rgb` y el
contrato `slots3x3_depth_v1` validado en el pack instalado de StrobeLights. Las
fuentes se colocan en los impactos calculados, pero es el shader 3D real el que
reconstruye e ilumina la geometría visible; no se coloca geometría de color allí.
No se crean strobes persistentes ni entradas en su editor.

Se conserva getSentChunkKeys: en Paper 1.21.4 se reprodujo isChunkSent=false con
el mismo chunk presente en el conjunto real de Moonrise. Esa discrepancia producía
sources=0. La instantánea se toma una vez por jugador y actualización.

## Pruebas del render real

El ensayo de integración instala el JAR real de StrobeLights 0.10.41 en Paper
1.21.4. Un cliente de protocolo descarga el pack de su servidor HTTP, verifica su
SHA-1 y confirma su carga. StrobeLights registra SUCCESSFULLY_LOADED. La escena
crea dos tramos con impactos sobre suelo y pared. Se exige:

- Fuentes e ItemDisplay propios de StrobeLights en ambos impactos.
- Cero TextDisplay.
- Recepción de esos ItemDisplay y sus metadatos por el cliente conectado.
- Registro de los payloads RGB y posiciones realmente producidos por Paper.

Los payloads y posiciones capturados se reproducen en los GLSL extraídos del mismo
JAR de StrobeLights usando una NVIDIA RTX 3060. Se ejecutan filtro, centros, seis
pasadas de agregación, iluminación opaca/translúcida y composición Fabulous. La
prueba se repite con la textura redimensionada por el proceso de atlas de OptiFine.
Los informes GPU y los logs de servidor/cliente se incluyen en el ZIP de pruebas.

Esto valida el pipeline real en escenas OpenGL controladas, no una captura de un
cliente Minecraft. La instalación del usuario debe usar Fabulous y el pack real.
El StrobeLights 0.10.41 documenta shaderpacks externos desactivados. No se declara
compatibilidad universal con shaderpacks que sustituyan ese pipeline ni se ofrecen
planos pintados como alternativa.

## Ramas, configuración y retirada

Se conservan cuatro JAR: 1.21.X, 26.1.X, 26.2.X y 26.3.X. Cada JAR incluye los packs
decorativos de sus parches y selecciona el adecuado al arrancar. La compatibilidad
de API/diseños/ciclo de vida se prueba en las doce versiones de Paper, usando el
mismo JAR en todos los parches de una rama. El RGB de otras ramas necesita el pack
compatible de StrobeLights; el ensayo gráfico concreto de arriba no se extrapola.

Predeterminados: animated-light=true, view-distance-chunks=21 y max-entities=8192.
Los valores y las colocaciones se conservan al pasar de configuración 15 a 16.
Al detener el servidor se retiran las entidades temporales y los LIGHT propios;
la actualización elimina los círculos anteriores sin borrar lights.yml.
