# Space Music

Reproductor Android de música local derivado de [Star Music](https://github.com/nexlibre/Star-Music), basado en [BitChord](https://github.com/kushagrasinghx/BitChord).

Conserva el motor de audio, temas, Liquid Glass, biblioteca por canciones/artistas/álbumes, listas locales persistentes, ecualizador y ajustes de reproducción. La pantalla inicial muestra archivos del dispositivo y búsqueda local. No requiere cuentas. La reproducción y las letras son locales; el cliente de música bloquea las solicitudes de red. Un cliente separado consulta y descarga únicamente actualizaciones de GitHub.

Identificador independiente: `org.nexlibre.spacemusic`. Puede instalarse junto a Star Music.

## Descarga e instalación

**[Descargar Space Music 2.3.0 estable — APK completo firmado](https://github.com/nexlibre/Space-Music/releases/download/v2.3.0/Space-Music-2.3.0.apk)**

APK universal optimizado para Android 8.0 o posterior, con firma propia de publicación. Incluye arm64-v8a, armeabi-v7a y x86_64.

Pruebas aprobadas en Android 12, 13 y 15: orden independiente por lista, persistencia, reproducción local, arrastre, veinte arranques en frío y actualización desde 2.2.0 estable sin desinstalar. Conserva firma y datos. [Resultados y límites](docs/verification-2.3.0.md). Pendiente validación en teléfonos físicos.

Si tienes instalada la versión de prueba con firma de desarrollo, debes desinstalarla antes de instalar esta. La desinstalación borra sus datos locales.

## Interfaz compacta

Minirreproductor con el mismo Liquid Glass que la navegación, sin degradado opaco de fondo. Las listas se desplazan detrás de las barras y el margen final se ajusta a su altura real. Se conserva el modo sin desenfoque.

El pie de Ajustes identifica a Space Music y enlaza a este repositorio. Las atribuciones se reúnen en «Licencias y código abierto», con GPL-3.0 disponible sin conexión. Se eliminan los enlaces promocionales heredados a Discord y al sitio de BitChord. La licencia y los avisos del código original se conservan.

## Biblioteca y navegación

Barra inferior flotante Liquid Glass con **Canciones, Álbumes, Artistas y Listas**, sin pestañas superiores duplicadas. El minirreproductor aparece encima de la barra. Mantiene búsqueda, ordenación y vistas de biblioteca. Liquid Glass es el valor predeterminado cuando no hay una preferencia de apariencia guardada.

## Listas compactas y actualizaciones

Cabecera propia de dos filas, búsqueda al tocar la lupa y controles Reproducir, Añadir, Ordenar y Opciones. Cada lista conserva su orden independiente: manual, título A–Z/Z–A, artista o incorporaciones recientes. El orden de Canciones sigue siendo independiente. En Opciones están el orden por arrastre, renombrar y eliminar la lista. El menú de cada canción permite quitarla; se han eliminado las flechas de subir y bajar.

Desde esta versión, un aviso informa de nuevas versiones estables y permite descargar e instalar el APK. Android solicita permiso para instalar desde esta fuente cuando sea necesario. El actualizador comprueba el paquete, una versión superior y la firma antes de ofrecer la instalación. Las novedades de la versión instalada aparecen una vez; las notas de una actualización descargada se conservan para mostrarlas sin conexión.

## Letras locales y ambiente

Lee letras incrustadas compatibles, también texto sin sincronización. En el menú de la canción puedes importar un archivo LRC/TXT sin modificar el audio. El modo ambiente se activa en Ajustes y usa colores de la carátula; respeta pausa, segundo plano y reducción de animaciones. No se consultan servidores para obtener letras.

La matriz de pruebas de esta versión comienza en Android 12; se conserva minSdk 26 para instalación.

## Firma y actualizaciones

La firma de Space Music es independiente de Star Music. Su respaldo cifrado se conserva en .signing/space-release-key.cms; el material de recuperación permanece privado. Las futuras versiones deben reutilizar el mismo almacén y alias space-music, mediante SPACE_KEYSTORE_B64 y SPACE_KEYSTORE_PASSWORD. La automatización rechaza crear una nueva identidad si ya existe la clave respaldada.

## Licencia y créditos

GPL-3.0: se conserva LICENSE y el historial original. Los nombres de paquetes internos se mantienen para conservar compatibilidad del código. Funciones remotas heredadas permanecen en el código compartido, pero sus solicitudes de red siguen bloqueadas y no tienen accesos desde la navegación de Space Music. La conexión del actualizador es independiente.


### Audio espacial 3 en 1

Espacial original, Audio 8D y Espacial avanzado paramétrico, con perfiles y
ajustes independientes. Recomendado con audífonos; no separa instrumentos ni
utiliza seguimiento de cabeza. [Arquitectura, controles, pruebas y límites](docs/spatial/implementation.md).
Esta función está en validación; el APK estable enlazado arriba corresponde a 2.3.0.


La candidata **2.4.0** aprobó compilación y pruebas automatizadas en emuladores
Android 12, 13, 14, 15 y 16. Aún no está publicada: falta restaurar la firma original
y validar el APK oficial como actualización. Las pruebas físicas y auditivas siguen pendientes.
[Informe y capturas](docs/spatial/verification-2.4.0.md) ·
[Notas preparadas de la candidata](docs/spatial/release-notes-2.4.0.md).

## Space Music Spatial 1.0.0 — edición paralela de prueba

**[Descargar APK Spatial 1.0.0](https://github.com/nexlibre/Space-Music/releases/download/spatial-v1.0.0/Space-Music-Spatial-1.0.0.apk)** ·
[Release, firma y SHA-256](https://github.com/nexlibre/Space-Music/releases/tag/spatial-v1.0.0)

Este canal publica el APK cuando pasan las comprobaciones automatizadas de la ejecución.
Es una instalación independiente: paquete `org.nexlibre.spacemusic.spatial`, versión
1.0.0 y firma persistente nueva. No reemplaza ni actualiza Space Music 2.3.0;
pueden coexistir. No importa sus preferencias ni su base de datos de listas.
Descarga el APK, permite la instalación desde el navegador cuando Android lo solicite
y abre **Space Music Spatial**. No hace falta desinstalar la aplicación anterior.

Ofrece Espacial original, Audio 8D y Espacial avanzado paramétrico, con perfiles
y controles guardados por modo. Original conserva el algoritmo anterior.
Audio 8D simula una órbita de 40 a 6 segundos por vuelta. Avanzado utiliza
retardos interaurales, diferencias de nivel y filtrado espectral; no usa HRTF
medidas ni separa voces o instrumentos. Recomendado con audífonos. Solo
espacializa señales estéreo; mono y multicanal conservan reproducción sin ese DSP.

La publicación exige pruebas JVM, lint, compilación optimizada, matriz de
emuladores Android 12–16, verificación de firma/alineación/paquete y una prueba
adicional de instalación y reproducción del APK con la firma definitiva en Android 13.
La documentación técnica y las mediciones anteriores están en
[arquitectura](docs/spatial/implementation.md) e
[informe de la candidata](docs/spatial/verification-2.4.0.md); sus referencias a
la firma original de 2.4.0 corresponden al canal original, no a esta edición paralela.
Quedan pendientes dispositivos reales, escucha con audífonos, cambios Bluetooth
y sesiones largas de batería/temperatura. El diagrama espacial es estático.
