# Android RF08 — verificación

Fecha: 27 de octubre de 2026 (Lima).

## Resultado

- Compilación Android con Gradle 9.6.0, AGP 9.4.1 y plataforma Android SDK 37.0: **BUILD SUCCESSFUL**.
- Tareas ejecutadas: `:app:testDebugUnitTest :app:assembleDebug`.
- APK debug generado correctamente. No se distribuye el APK: Francesca lo compilará con sus propios archivos en Windows.
- **47 pruebas Android sin fallos, errores ni omisiones**: 18 de validación + 22 de coordinación/recuperación + 6 del adaptador/JSON + 1 ya existente.
- Comprobación adicional de los 46 casos nuevos en JVM con Kotlin 2.2.10: todos pasaron.
- Se comprobó que los fuentes dentro del paquete coinciden byte por byte con los que compilaron.
- El paquete incluye 17 archivos nuevos y no contiene credenciales, configuración local, cambios de Gradle/manifiestos/MainActivity ni migraciones.

## Entorno y ajuste temporal

Se partió de la rama real feat/vehiculos-mvp, commit af69fca, en una copia de trabajo separada. El proyecto solicita un daemon JDK25; su proveedor Foojay devolvió 403 en esta red. Para ejecutar la verificación se retiró el criterio de daemon **solo en la copia temporal** y se utilizó un JDK21 completo y el almacén de certificados de este entorno. El resto de las versiones y configuración de compilación del proyecto se conservaron, incluido Java target 11.

Ninguno de esos ajustes ambientales forma parte del paquete. No se modificó ni se recomienda modificar el JDK25 configurado en la computadora de Francesca. La compilación que ella ejecutará será con su configuración habitual de Android Studio.

SDK y cachés de herramientas se instalaron en /tmp, fuera del repositorio. No se publicó ningún archivo de proxy, certificados, licencia, caché o claves de firma de depuración.

## Qué acreditan las pruebas

Validación de obligatorios, placa/año/km, VIN estricto, serie, motor, opciones OTRO/ELÉCTRICO, nulabilidad y límites Unicode. Gestión de cupo, doble toque, persistencia antes de POST, respuesta perdida simulada, nueva instancia, reenvío con clave/contenido idénticos, 404 incierto, recuperación antes de consultar cupo, errores definitivos/técnicos, sesión/cuenta, error de almacenamiento y cancelación. Adaptador Retrofit sin idUsuario y JSON de solicitud recuperable.

El coordinador usa un almacén de prueba en memoria; no se ejecutaron pruebas instrumentadas del archivo AtomicFile sobre un emulador. Sí se compiló su implementación Android en el APK. No se realizó una prueba visual manual en emulador ni un login real.

## Integración pendiente

MainActivity conserva Inicio. RegistroVehiculoRoute está preparado para que el host aporte Retrofit autenticado, almacenamiento por cuenta y navegación. El diseño puede verse mediante RegistroVehiculoPreview, exclusivamente en src/debug y sin solicitudes de red ni altas simuladas.

Para cerrar RF08 desde el celular faltan la integración con la sesión de Elsa, confirmar la frontera Free con Sebastián, enlazar la Route a la navegación y ejecutar aceptación con una cuenta real. RF09 todavía no está implementado; el callback Ver mis vehículos debe conectarse cuando exista.

El paquete se publica únicamente en una rama de descarga separada para que la usuaria incorpore los archivos personalmente. No se hace commit, push ni merge de estos fuentes sobre su rama de desarrollo.
