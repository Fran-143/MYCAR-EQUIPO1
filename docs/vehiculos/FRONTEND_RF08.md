# RF08 — Diseño del registro de vehículo

Base revisada: main f4fd107 (PR #5 integrada). Actualización: 27/10/2026.

## Cambios

- Cabecera «Agregar vehículo», tonos azules y tarjetas con bordes redondeados.
- Tres secciones desplegables: Información general; Identificación adicional (opcional); Motor y mantenimiento.
- Identificación adicional comienza contraída. Sus datos se conservan al cerrar la sección.
- Las secciones con errores se abren cuando se publican errores de validación o del servidor.
- Cancelar y Guardar vehículo permanecen al pie, fuera del área desplazable. Se deshabilitan durante una operación.
- Atrás del sistema también queda bloqueado mientras el ViewModel está ocupado, igual que el botón visible.
- Rótulos, ejemplos, ayudas de formato, unidad km, selectores anclados y controles de al menos 48 dp.
- Campos de «Otro» condicionales; combustible, aceite y número de motor no aplican a eléctricos. Estas reglas ya existían.
- Tema claro/oscuro local a esta pantalla; no cambia el tema de los módulos de las compañeras.
- Preview DEBUG: pantalla normal, pequeña, letra grande, modo oscuro y formulario con ejemplos.

Los valores de los catálogos reales proceden de la API. Los ejemplos del Preview no se usan en ejecución.
El formulario utiliza las mismas validaciones y el mismo coordinador de guardado de main.
Las validaciones se ejecutan al pulsar Guardar; el teclado numérico por sí solo no valida entradas.
No se convierte automáticamente una entrada inválida en un dato diferente.

## Diferencias necesarias con la imagen de referencia

- La placa conserva el contrato A1B234: sin guiones ni espacios. El ejemplo ABC-123 de la imagen no cumple la regla actualmente aprobada.
- El año de fabricación es un entero (ej. 2020), no una fecha completa. No se añade un selector de fecha.
- Se conserva una ilustración. No hay un botón «Agregar imagen»: todavía no existe un contrato para enviar y persistir la foto desde RF08. Añadirlo requiere acordar con el equipo su almacenamiento, endpoint y límites.
- Se mantiene la selección de usos que devuelva el backend; no se inventa un catálogo «Particular / Taxi» combinado.
- Los controles tienen tamaños accesibles y el contenido se desplaza; no se reduce todo para que quepa en una sola pantalla.

## Cómo verlo sin esperar a autenticación

1. Abre el proyecto android en Android Studio y espera la sincronización de Gradle.
2. Abre app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt.
3. Selecciona Split o Design y pulsa Build & Refresh si hace falta.
4. Prueba RegistroVehiculoPreview en modo interactivo. Desplázate por el formulario y pulsa Guardar para ver los errores.
5. Cambia a RegistroVehiculoConDatosPreview para ver un formulario con datos de ejemplo.
6. Selecciona Eléctrico para revisar los campos que desaparecen. Selecciona Otro para mostrar la especificación.
7. Comprueba el formato de año, placa, VIN, serie y número de motor al Guardar.
8. Revisa los Previews de 320 dp, letra grande y modo oscuro en tu IDE.

El Preview permite revisar diseño y validaciones locales. No inicia sesión ni registra vehículos.
MainActivity continúa con la pantalla actual de conexión. La navegación real y el repositorio autenticado
se conectarán cuando Elsa entregue la autenticación; no se añade un usuario ficticio para saltarla.

## Archivos

- android/app/src/main/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoScreen.kt
- android/app/src/debug/java/com/mycar/app/features/vehiculos/ui/RegistroVehiculoPreview.kt
- android/app/src/main/res/drawable/ic_rf08_volver.xml
- android/app/src/main/res/drawable/ic_rf08_arriba.xml
- android/app/src/main/res/drawable/ic_rf08_abajo.xml
- android/app/src/main/res/drawable/ic_rf08_informacion.xml
- android/app/src/main/res/drawable/ic_rf08_identificacion.xml
- android/app/src/main/res/drawable/ic_rf08_motor.xml
- docs/vehiculos/FRONTEND_RF08.md

No requiere migraciones, cambios del backend ni dependencias nuevas.
