# Space Music 2.4.0 — candidata de audio espacial

## Novedades

Audio espacial 3 en 1: Original conserva su algoritmo; Audio 8D incorpora órbita
simulada y reverberación; Avanzado añade un modelo binaural paramétrico con retardos,
sombra de cabeza y distancia. Perfiles y ajustes independientes, transiciones suaves,
restablecimiento por modo y panel contextual con superficies del tema y esquema estático opcional.

## Estabilidad comprobada

Regresión JVM, lint y compilación de APK optimizado aprobados. En emuladores Android
12, 13, 14, 15 y 16 aprobaron las pruebas de persistencia, reproducción, cambios de modo,
presupuesto de CPU e interfaz. Se evita reproducir colas antiguas de retardo al reactivar
y se conserva el bypass exacto tras desactivar. Se añadieron migración/corrupción de
preferencias, interacción EQ/normalización y etiquetas accesibles.

Lint mantiene advertencias y una línea base heredada. Las mediciones son de emulador;
no se anuncia una mejora porcentual de batería o rendimiento en teléfonos reales.

## Limitaciones y publicación

Avanzado no utiliza HRTF medida, separación de instrumentos ni seguimiento de cabeza.
Solo se espacializa estéreo; Atmos conserva su suspensión y mono/multicanal siguen por
la ruta compatible. Se recomiendan audífonos. El esquema no es análisis de audio en vivo.

Pruebas físicas/auditivas pendientes: esta es una **versión preliminar**, no una
validación perceptual ni de batería en equipos reales. El flujo exige firma oficial,
certificado idéntico al de 2.3.0 e instalación como actualización conservando listas y
preferencias en Android 12–16 antes de crear esta publicación. Se adjuntan firma,
SHA-256 y registros de verificación. No se sustituye el APK estable 2.3.0.

[Informe completo](https://github.com/nexlibre/Space-Music/blob/space-spatial-3-in-1/docs/spatial/verification-2.4.0.md).
