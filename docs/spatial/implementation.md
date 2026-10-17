# Audio espacial 3 en 1 — implementación en validación

Esta rama aún no es una versión publicada. Base auditada: Space Music 2.3.0,
commit 641453da. La versión objetivo es 2.4.0 (versionCode 8), pendiente de validación.

## Auditoría y arquitectura

Media3/ExoPlayer usa PrecisionAudioSink, PCM Float32 y DspChain, en orden:
espacial → ecualizador → transición → normalización/limitador → salida.
Dos procesadores espaciales pertenecen a los dos reproductores del crossfade;
ambos reciben el mismo snapshot inmutable de preferencias. No se crea otro
AudioTrack, Virtualizer ni sesión de efectos nativos. Bluetooth conserva el
enrutamiento y códec negociados por Android; no se promete baja latencia física.
MP3, FLAC, AAC y OGG se decodifican antes del DSP. La cadena permite otras
profundidades PCM mediante PcmBoundary. El adaptador heredado PCM16 comparte
el motor nuevo. Mono/multicanal se reproducen sin espacialización; no se
inventa un escenario estéreo cuando la ruta no tiene dos canales.

## Modos reales

* **Espacial original**: conserva width=2.5, gain=.82, crossfeed=.2,
  lowpass=.3 y retardo de 15 ms del código anterior. Tras la transición, la
  salida es exactamente la del algoritmo heredado. Conserva también su
  headroom histórico: no se añade un limitador a este modo.
* **Audio 8D**: órbita simulada con modulación continua del nivel entre oídos,
  profundidad frontal/posterior y reverberación estéreo de baja mezcla.
  Velocidad 0–100% equivale a 40–6 segundos por vuelta, linealmente en tiempo.
  No implica separación de fuentes. La conservación de cada canal evita
  cancelar voces/graves sumando toda la mezcla a mono.
* **Espacial avanzado**: aproximación binaural paramétrica, con dos fuentes
  virtuales estéreo fijas, crossfeed contralateral con ITD fraccional de
  .15–.65 ms, diferencia de nivel y sombra de cabeza mediante filtro de
  1.6–4.6 kHz. Distancia atenúa moderadamente la señal directa; profundidad
  cambia la sombra espectral. No utiliza HRTF medidas, seguimiento de cabeza,
  Dolby, separación instrumental ni posicionamiento de objetos independientes.
  No se incorpora una biblioteca externa ni datos HRTF sin validar su licencia.

Los modos nuevos son combinaciones convexas de señales con ganancia <=1;
la reverberación tiene realimentación .35 y entrada .65 (estable), con amortiguación a 1.8 kHz de las reflexiones tempranas. Esto
previene picos añadidos sobre PCM normalizada en esas rutas. EQ y crossfade
pueden añadir ganancia posteriormente: se mantiene la protección final
existente. No hay promesa de reproducción bit-perfect con DSP activo.

## Transiciones, preferencias y diseño

Pesos de rutas y controles se suavizan con constante temporal de 30 ms
(se asientan en aproximadamente 210 ms, con ajuste final al 0.1% para evitar estancamiento Float32); no se modifica play/seek ni se
reconstruye el reproductor. Un bloque usa un snapshot inmutable. Retardos
preasignados al configurar; no hay asignaciones por frame en el motor.
En original ya asentado se omiten los cálculos de las rutas nuevas.
Con efecto desactivado, la salida vuelve exactamente a dry tras asentarse.

El toggle anterior `spatial_audio` conserva su valor. Si falta o se corrompe
`spatial_scene_v1`, se elige original; los controles nuevos se guardan por
modo en JSON y se limitan a valores finitos. Restablecer afecta solo al modo
seleccionado. El servicio conserva la exclusión existente de Atmos.

La fila de audio espacial abre un panel contextual con tres tarjetas,
perfiles y controles reales. Colores translúcidos del tema, contraste y
bordes seleccionados, compatible con claro/oscuro/AMOLED. La visualización opcional y desactivada por defecto es un esquema estático
del alcance de la órbita o de las dos fuentes virtuales. Está rotulado como
estático: no finge análisis musical ni una posición de fase en tiempo real.
Se puede ocultar y no ejecuta bucles de dibujo al estar oculta. No hay animación
nueva que ignore reducción de movimiento. No se añade blur pesado por tarjeta.

## Verificación y límites de publicación

Pruebas añadidas: valores inválidos, perfiles independientes, bypass exacto,
señales sinusoidales/ruido/impulsos/DC a 8–192 kHz, cambios repetidos de modo,
picos y continuidad, energía contralateral retardada; persistencia Android;
reproducción estéreo MP3/FLAC/AAC/OGG al cambiar entre los tres modos sin seek.
Fixtures generadas con FFmpeg: seno 440 Hz, 48 kHz, ocho segundos, canales
L y .8L. Son señales técnicas, no validación perceptual musical.

La compilación local se intentó: Gradle no pudo descargarse (Network is
unreachable). No equivale a un fallo del código ni a pruebas aprobadas.
CI debe compilar y ejecutar los tests sobre este commit. Las mediciones JVM
son diagnósticas y no sustituyen CPU/memoria/batería en Android.

Antes de una release estable quedan obligatorios: CI y emuladores, revisión
visual, benchmarks Android antes/después, firma con certificado original
5436f4210119058a215c0b0660dead0a9b1bed315f13d6f801645c71c942c5ce,
instalación como actualización y comprobación de arranque/reproducción.
El respaldo CMS del repositorio está cifrado: no se genera una clave nueva
ni se reutilizan firmas de APK antiguos para código modificado.

No se han realizado pruebas físicas ni auditivas. Pendientes: audífonos
cable/Bluetooth, cambio/desconexión de salida, llamadas, pantalla bloqueada,
sesiones largas y batería/temperatura en teléfono. Comprobar con música
propia voces, graves, fatiga y cambios de volumen en los seis perfiles.
Android 14/16 no se consideran probados por ejecutar pruebas en otras APIs.
No publicar si faltan los criterios obligatorios del encargo.
