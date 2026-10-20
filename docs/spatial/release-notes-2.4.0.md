# Space Music 2.4.0 — borrador de notas, sin publicar

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

Pruebas físicas/auditivas pendientes. Firma oficial e instalación como actualización
pendientes de restaurar los secretos del almacén original; esta candidata no está
publicada. Al publicar deben sustituirse estas notas por el resultado final de firma,
instalación y verificaciones, con enlace al APK real.

[Informe completo y resultados](verification-2.4.0.md).
