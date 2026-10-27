# RF17: pantallas Android

Esta entrega corresponde al bloque de frontend. RF17 completo sigue pendiente de integración.

## Diagnóstico comprobado

Revisé tu Android_RF17_revision_20261027_134005.zip, no el disco de tu PC.
Ya existían el formulario básico, ViewModel, validadores y adaptador HTTP de RF17.
MainActivity abría solo HomeScreen y no tenía una entrada al formulario.
Tus capturas posteriores mostraron «Servidor disponible» después de corregir la URL y el permiso de red local de Android 17.
El main remoto consultado sigue en f4fd10758cfbb970798141a550e9715114b6018a y no contiene implementación de login de Elsa.
No verifiqué el estado administrativo del PR #6. La rama remota de progreso continúa en a5d2264; tu commit 10d7943 quedó local según las salidas que enviaste.

## Qué construimos

1. Formulario real con vehículo, servicio, origen/lectura/km, costo, observaciones, repuestos y próxima recomendación.
2. Diálogo de agregar/editar repuesto con sus errores de validación.
3. Resultado del registro con fecha, km, costo y cantidad de repuestos.
4. Estados de carga, guardado, errores, sesión requerida, cancelación y resultado incierto.
5. Entrada desde Inicio. Volver pide confirmación antes de descartar un borrador.
6. Diez Preview interactivos separados del código de producción; no incluyen una Activity de demostración ni un login ficticio.
7. Cuatro pruebas de interfaz en src/androidTest. No prueban HTTP ni PostgreSQL.

## Incorporar y abrir en el emulador

La carpeta comprobada de tu proyecto es:
`C:\Users\PROPIETARIO\Documents\MYCAR-EQUIPO1-git\android`
La ruta C:\Proyectos\MyCarEquipo1 de las indicaciones de tu amiga no corresponde a la copia que estás usando.

1. Extrae RF17_PANTALLAS_ANDROID.zip en Descargas, carpeta RF17_PANTALLAS_ANDROID.
2. Deja encendido el emulador emulator-5554.
3. Ejecuta en PowerShell, desde cualquier carpeta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF17_PANTALLAS_ANDROID\INSTALAR_PANTALLAS_RF17.ps1"
```

El instalador valida los archivos, respalda los anteriores fuera del repositorio, copia solo los 11 archivos indicados y compila/instala con la clave de tu PC. Si encuentra cambios desconocidos se detiene antes de reemplazar archivos.

Resultado esperado: Inicio muestra Registrar mantenimiento. El botón abre el formulario con cinco secciones y acciones de guardar/cancelar. Sin sesión real aparece el aviso para iniciar sesión y los controles quedan bloqueados. Se puede recorrer el formulario desplazándolo; ver el diseño no significa que haya un registro guardado.

Se mantienen tu comprobación de conexión, URL 10.0.2.2:8081, permiso de red local, Gradle y manifiestos. No se modifican Docker, el backend, .env ni migraciones. No se ejecutan comandos Git.

## Probar el diseño con interacción

Abre RegistrarMantenimientoPreview.kt en Android Studio con Ctrl+Shift+N y selecciona Split o Design. El módulo app debe usar su variante debug.
Las pantallas que renderiza están en src/main. Solo los datos y control visual del Preview están en src/debug.

- 01 Formulario interactivo: activa el modo interactivo de Compose Preview. Cambia tipo, origen y campos. Selecciona HISTORICO_MANUAL para escribir km.
- 02 Servicio Otro: aparece Servicio específico. Al elegir otro tipo, ese campo se limpia.
- 03 Agregar repuesto: confirma vacío para ver el error de nombre; prueba cantidad 0 y precio negativo; corrige a cantidad 1 y precio 35.00. Agrega, edita y quita el repuesto desde el formulario.
- 04 Validaciones: muestra fecha futura, km no positivo, costo negativo y recomendación inválida.
- 05 Carga y 06 Guardando: indicadores y controles bloqueados.
- 07 Confirmación visual: resumen con datos de ejemplo. No significa persistencia real.
- 08 Resultado incierto: guardado bloqueado y verificación. En Preview solo cambian mensajes, sin red.
- 09 Cancelación: Seguir editando conserva el borrador; Descartar regresa dentro de la simulación visual.
- 10 Sesión requerida: reproduce el estado actual de la app normal.

En 01, Guardar reutiliza el validador local y muestra un resultado de ejemplo. El encabezado siempre indica que no hay guardado real. Este código de ejemplo no forma parte de release.

## Ejecutar las cuatro pruebas

Con emulator-5554 encendido:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF17_PANTALLAS_ANDROID\PROBAR_PANTALLAS_RF17.ps1"
```

Resultado esperado: `OK (4 tests)`. Se dirigen solo a ese emulador, no a otros móviles conectados.
Cubren el bloqueo sin sesión, selección de OTRO y edición del campo, resultado incierto con verificación, y Volver con confirmación y conservación del borrador.

## Archivos completos

Todas las rutas siguientes parten de tu carpeta android. Cada archivo está completo en archivos/, con la misma estructura. Puedes copiarlo manualmente en lugar de ejecutar el instalador.

- Reemplazar completo: `app\src\main\java\com\mycar\app\MainActivity.kt`
- Reemplazar completo: `app\src\main\java\com\mycar\app\features\inicio\HomeScreen.kt`
- Reemplazar completo: `app\src\main\java\com\mycar\app\features\mantenimiento\viewmodel\MantenimientoViewModel.kt`
- Reemplazar completo: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\RegistrarMantenimientoScreen.kt`
- Crear: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\AccionesMantenimiento.kt`
- Crear: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\EntradaRegistroMantenimiento.kt`
- Crear: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\ComponentesMantenimiento.kt`
- Crear: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\RepuestoMantenimientoDialog.kt`
- Crear: `app\src\main\java\com\mycar\app\features\mantenimiento\ui\ResultadoMantenimientoScreen.kt`
- Crear: `app\src\debug\java\com\mycar\app\features\mantenimiento\ui\RegistrarMantenimientoPreview.kt`
- Crear: `app\src\androidTest\java\com\mycar\app\features\mantenimiento\ui\RegistroMantenimientoPantallasTest.kt`

## Qué comprobé realmente

- assembleDebug y compileReleaseKotlin compilaron correctamente.
- assembleDebugAndroidTest compiló y empaquetó las cuatro pruebas.
- Los datos de ejemplo quedaron solo en src/debug y src/androidTest.
- No ejecuté las pruebas instrumentadas: este entorno no tenía un emulador. No afirmo que hayan pasado.
- No ejecuté JUnit del backend, pruebas PostgreSQL ni integración HTTP en este bloque.
- No ejecuté visualmente esta actualización en un móvil. La instalación permite que tú lo compruebes en tu emulador.

## Pendiente para completar RF17

La sesión real de Elsa y el repositorio construido con el Retrofit autenticado deben llegar a EntradaRegistroMantenimiento. El adaptador de identidad del backend y la implementación idempotente siguen pendientes; no se sustituyen por datos ficticios. También debe revisarse la coherencia histórica completa indicada por tu amiga, especialmente lecturas posteriores a una referencia y anteriores al servicio. La navegación RF14 depende del contrato de Francesca.

Mensaje para Elsa:
«Ya tengo las pantallas de RF17 y la comunicación básica con el servidor. ¿Me compartes la rama y el contrato del login: cómo obtener la identidad validada en Spring y cómo recibir el Retrofit autenticado y una clave opaca de cuenta en Android? Necesito integrar la sesión real para cargar vehículos y guardar mantenimientos.»

Mensaje para Francesca:
«Para completar RF17 necesito la ruta o callback de RF14 para el vehículo seleccionado y saber cómo regresa la lectura confirmada. El mantenimiento debe conservar su propio km histórico al volver.»
