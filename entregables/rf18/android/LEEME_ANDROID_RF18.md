# RF18 — Android: frontend de Historial

Parche para el proyecto original `C:\Users\PROPIETARIO\Documents\MYCAR-EQUIPO1-git\android`. No abras el ZIP extraído como otro proyecto. El backend se entrega en `01_RF18_BACKEND.zip`, por separado.

## Aplicar y abrir la app normal

Extrae TODO este ZIP en `Downloads\RF18_ANDROID` y ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_ANDROID\APLICAR_ANDROID_RF18.ps1"
```

El instalador comprueba la rama/base y los hashes, hace un respaldo fuera del repositorio, copia los archivos, compila la app y el APK de pruebas y ejecuta las pruebas unitarias. Si `emulator-5554` está abierto, instala y abre la app. En la bienvenida pulsa **Historial de mantenimientos**. Si el emulador estaba cerrado, ábrelo y ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_ANDROID\ABRIR_ANDROID_RF18.ps1"
```

## Ver el diseño interactivo directamente en el emulador

Después de aplicar el paquete, con el emulador abierto:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_ANDROID\VER_DISENO_RF18.ps1"
```

Se abre una Activity EXCLUSIVA de debug con la MISMA pantalla real y un controlador de ejemplos. Lleva el rótulo visible **DISEÑO RF18 · DATOS DE EJEMPLO · Sin conexión ni guardado real**. Puedes cambiar vehículo y comprobar costo vacío/cero, estados y botones. Registrar muestra un aviso de navegación, no guarda. Esta Activity no llama APIs, no lee la cuenta ni el almacenamiento privado y no se incluye en release. Se lanza solo con este script; la navegación normal siempre abre el historial real.

Para regresar a la app normal, ejecuta `ABRIR_ANDROID_RF18.ps1`.

También hay ocho Preview interactivos en Android Studio: Ctrl+Shift+N, `HistorialMantenimientoPreview.kt`, Split/Design y modo interactivo. Incluyen historial, vacío, inactivo, sin principal, sin vehículos, carga, error/reintento y sesión requerida.

## Diseño y comportamiento

- Encabezado Historial, Volver y Registrar.
- Selector de vehículos propios con placa y estado ACTIVO/INACTIVO. Principal seleccionado cuando existe; sin principal espera elección.
- Mantenimientos activo; Kilometraje y Talleres deshabilitados con Próximamente.
- Contador por vehículo, orden más reciente primero y tarjetas con tipo/servicio OTRO, estado, fecha, km histórico y costo.
- Costo NULL: No registrado. Cero: S/ 0.00. No convierte importes a Float/Double.
- Sin vehículos, sin selección, sin mantenimientos, sesión requerida y error de consulta son estados diferentes.
- Error no muestra tarjetas viejas ni contador cero falso. Reintento manual.
- Una respuesta atrasada no puede sustituir la selección actual. Vehículo no autorizado conserva el último contexto válido y muestra un aviso.
- Registrar solo para vehículo activo y cuando el host proporciona el repositorio de RF17 autenticado. Abre RF17 con ese vehículo. Volver/cancelar o finalizar un guardado confirmado regresa al historial y recarga la consulta.
- Se aísla el ViewModel de cada pantalla; RF17 conserva el SavedStateHandle del host y su archivo privado de solicitud incierta. La selección del historial se conserva en el host al navegar.
- Filtros, otro orden, exportación y detalle RF19 pertenecen a otros RF; no se activan en RF18.

## Archivos y conexión con backend

Frontend: `archivos/android/app/src/main/java/com/mycar/app/features/historial/ui/HistorialMantenimientoScreen.kt`.

Estado/lógica: domain, viewmodel y data dentro de features/historial. El adaptador Retrofit acepta la instancia autenticada compartida; no crea otro ApiClient ni otro almacén de tokens.

`FlujoHistorialMantenimiento` recibe:

- `historialRepository`: creado con `HistorialApiAdapter.desde(retrofitCompartidoAutenticado)`.
- `mantenimientoRepository`: el adaptador RF17 existente, sobre la misma sesión/Retrofit.
- `claveCuenta`: clave opaca y estable para aislar la cuenta/borrador; no es un idUsuario enviado al backend.
- Callbacks reales de inicio de sesión y RF14 cuando estén conectados por el equipo.

El servidor resuelve la identidad desde el token; Android no elige el usuario de la consulta. El host debe retirar el ámbito al cerrar/cambiar de cuenta y notificar la sesión expirada. La pantalla limpia los datos al recibir 401.

**En main todavía no existe el cliente/navegación Android del login de Elsa. Por eso MainActivity abre la pantalla normal en estado “Inicia sesión” hasta que se inyecten esas dependencias reales.** Esto no impide ver el diseño mediante el script debug o los Preview. El servidor RF18 sí tiene integración con la sesión de Elsa y pruebas autenticadas.

La política HTTP autenticada de RF17 continúa siendo un pendiente separado. El enlace visual a su formulario no demuestra un guardado Android → API → PostgreSQL.

## Archivos compartidos que cambia el parche

- MainActivity: acceso al flujo Historial.
- HomeScreen: botón Historial de mantenimientos; conserva el callback anterior de RF17 para otros hosts.
- ResultadoMantenimientoScreen de RF17: etiqueta genérica Volver, porque ahora el retorno puede ser al historial.
- Manifest de debug: conserva permisos/red existentes y añade solamente la Activity de diseño de debug.

No modifica Gradle, dependencias, configuración de red compartida, tokens, backend ni módulos de vehículos.

## Verificación

Pasaron las 20 pruebas nuevas de lógica/repositorio RF18 y las 47 anteriores: 67 unitarias Android en total. Compilaron Debug, Kotlin Release y APK de pruebas. Se definen ocho pruebas de interfaz, incluidas navegación RF18 → RF17 → RF18 y ausencia de consultas sin sesión; se compilaron, pero no se ejecutaron en un emulador de este entorno.

Para ejecutarlas en tu emulador:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_ANDROID\PROBAR_PANTALLAS_RF18.ps1"
```

El script exige `OK (8 tests)`. Usa fixtures de interfaz sin backend ni guardado. Ver `VERIFICACION_RF18.json`. Los instaladores PowerShell están preparados para Windows y quedan para ejecución en la PC de Nayu; la compilación y las pruebas indicadas se hicieron en este entorno.
