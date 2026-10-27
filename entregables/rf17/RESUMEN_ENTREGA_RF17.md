# RF17 — entrega de implementación, 27/10/2026

## Origen y límites de esta entrega

Fran-143/MYCAR-EQUIPO1, rama feat/mantenimientos-alertas-rf17-rf27,
commit a5d2264f661d138445340d471c59e414da21efe2. Se trabajó con una copia fuera del repositorio.
No se hicieron commits, push, merge, reset ni cambios de rama. No se accedió al proyecto del PC.
Las rutas C:\Users\PROPIETARIO\Documents\MYCAR_REFERENCIAS_NAYU no son accesibles desde la nube.

Se utilizaron el prompt de esta entrega, el código/migraciones reales de la rama, el ZIP funcional
de Nayu y CUS_CP_MyCar_Nayu_Corregido.xlsx ya disponibles. Se consultaron main 48af167 y, solamente
como referencia, la rama de vehículos a936ab6 de Francesca. No se fusionó código compartido.
No se recibió el ZIP/código de autenticación de Elsa ni el contrato definitivo de navegación RF14.
Los contratos REST propuestos conservan su condición de PROPUESTOS.

## Estado por componente

| Componente | Estado | Alcance real |
|---|---|---|
| Núcleo backend RF17 | IMPLEMENTADO / COMPILA | Conserva el guardado, DAO, bloqueo y transacciones; añade límites de almacenamiento y guardas |
| Capa API REST | IMPLEMENTADO / COMPILA / BLOQUEADO POR CONTRATO COMPARTIDO | Controller, DTO, consultas, validación y errores; rutas deshabilitadas por defecto |
| Autenticación | BLOQUEADO POR CONTRATO COMPARTIDO | Puerto IdentidadMantenimiento, sin login nuevo ni usuario fijo |
| Idempotencia | BLOQUEADO POR CONTRATO COMPARTIDO | Se conservan interfaces/modelos previos; se prepara puerto del adaptador. No existe deduplicación real nueva |
| Android RF17 | IMPLEMENTADO / COMPILA | Formulario Compose, ViewModel, repositorio Retrofit inyectado, repuestos y estado pendiente |
| Navegación/cliente compartidos | PENDIENTE DE INTEGRACIÓN | Entrada desde Historial, Retrofit autenticado y navegación RF14 recibidos por adaptadores |
| Android → API → BD real | NO VERIFICADO / PENDIENTE DE INTEGRACIÓN | No hay sesión ni adaptador idempotente compartido disponibles |

No se declara RF17 completamente funcional. La disponibilidad de guardado no se simula.
MainActivity, HomeScreen, ApiClient, configuración compartida, RF14 y demás módulos no se modifican.
Por ello el inicio actual de Android conserva su pantalla original: el host debe insertar la entrada RF17.

## Funcionalidades implementadas o conservadas

- Vehículo propio ACTIVO, tipo ACTIVO y fecha de servicio no futura en Lima.
- Snapshot km_servicio, referencia LECTURA del mismo vehículo y coherencia con fecha/valor.
- Se conserva la validación HISTORICO_MANUAL y el bloqueo previo del vehículo dentro de la transacción.
- OTRO requiere servicio específico; el campo no se permite para otros tipos.
- Observaciones se conservan en descripcion, sin crear otra columna ni confundirlas con servicioOtro.
- Costo y precio opcionales null/0 distintos; no negativos; límite NUMERIC(12,2), sin redondeo silencioso.
- Textos con límites del esquema por caracteres Unicode; cantidades enteras positivas.
- Repuestos opcionales en lista final, duplicados de nombre permitidos, snapshots y relaciones en transacción.
- Recomendaciones opcionales posteriores/mayores al servicio. No se crea ni atiende una alerta.
- Contexto HTTP de solo lectura: vehículos autorizados, tipos, última lectura y aceite (incluido OTRO).
- Android: fecha, vehículo/tipo, lectura/manual, servicio OTRO, observaciones, costo, recomendaciones,
  edición/eliminación de repuestos, confirmación y errores. Sin taller fijo y alerta Próximamente deshabilitada.
- Borrador temporal y solicitud de resultado incierto conservada en almacenamiento privado por cuenta.
  No se hacen reintentos automáticos ni se cambia UUID/payload de una operación incierta.
- El repositorio Android recibe el Retrofit compartido; no crea otro cliente ni guarda credenciales.

## Escenarios alternativos

| EA | Implementación / integración |
|---|---|
| EA01 | Validación histórica conservada; UI lectura/manual. Ida/retorno RF14 preparados por callback, pendientes del contrato real |
| EA02 | Futuro: acción de alerta deshabilitada; backend no crea alertas |
| EA03 | Repuestos manuales agregar/editar/quitar en borrador; solo lista final al POST |
| EA04 | Validaciones de entrada, textos/importes y feedback visual; errores HTTP propuestos |
| EA05 | Propiedad/estado/tipo comprobados; recurso ajeno/inactivo no revela datos en la API |
| EA06 | Cancelar antes de guardar descarta solo borrador; ninguna llamada de escritura ni reversión de RF14 |
| EA07 | Núcleo transaccional conservado; UI congela petición incierta y ofrece reconciliación/reintento con el adaptador confirmado |

Las reglas no aprobadas de igualdad, mismo día y coherencia futura con lecturas RF14 siguen pendientes.
No se inventó una hora del servicio ni se cambió RF14 para resolverlas.

## Compilación realizada, sin suites

Backend: `mvn -DskipTests test-compile`, BUILD SUCCESS. Compiló 37 fuentes principales y 13 fuentes
de pruebas existentes. JDK 25 disponible, con release 21 del pom. No equivale a ejecución en JDK 21.
Un intento previo de package offline llegó al plugin JAR y falló por dependencias no presentes en
la caché. No se entrega ni se afirma validado un JAR ejecutable; la comprobación final de fuentes sí pasó.

Android: `gradlew :app:compileDebugKotlin`, BUILD SUCCESSFUL. Gradle 9.6.0, AGP 9.4.1,
Kotlin/configuración del proyecto y SDK 37; daemon JDK 25 según el archivo existente.
Las rutas SDK/home usadas son solo de la comprobación en nube y no se incluyen en los ZIP.

NO EJECUTADO: suites JUnit/JDBC, pruebas instrumentadas, pruebas en dispositivo, arranque del servidor,
peticiones HTTP reales y circuito completo Android → API → PostgreSQL. No se creó una nueva suite.
Las pruebas y recursos existentes se conservaron byte a byte. Los conteos históricos del commit de
origen no certifican estas modificaciones. No se usó ni modificó mycar_mvp real.

## Integraciones y documentos que faltan exactamente

1. Código/ZIP o rama de Elsa RF01/RF02/RF06 y contrato de sesión: usuario real, cuenta ACTIVA,
   expiración/revocación, autenticación HTTP y cómo proporcionar el Retrofit autenticado.
2. Aprobación común de rutas/DTO/errores e idOperacion: reserva/huella/resultado, permisos de consulta,
   reintentos y semántica de COMMIT incierto. Solo entonces implementar un bean
   RegistroIdempotenteMantenimiento con el mecanismo del equipo.
3. Francesca: contrato Android de acceso desde Historial y de RF14/retorno (ruta/callback/parámetros),
   ámbito de ViewModel y disponibilidad del cliente compartido. No basta el código RF08 de referencia.
4. Decisión aprobada sobre lecturas del mismo día/igualdad y coherencia de lecturas futuras RF14.

Ver CONTRATO_RF17_PROPUESTO.md e INTEGRACION_ANDROID_RF17.md para las interfaces y rutas concretas.

## Contenido de los ZIP

- 01_RF17_BACKEND.zip: backend/ completo, wrappers, pom, fuentes, recursos, pruebas conservadas y documentación.
- 02_RF17_API.zip: vista de revisión de la capa HTTP con rutas backend/ correctas; depende del backend,
  no es un servicio ni proyecto Maven independiente.
- 03_RF17_ANDROID.zip: android/ completo, Gradle/wrapper, recursos y feature RF17. No incluye APK.

Se excluyen .env, credenciales, .git, .idea, .gradle, .kotlin, target, build y local.properties.
Descomprimir aparte y comparar con la rama antes de integrar cambios; no sobrescribir progreso local posterior.

## Archivos Java creados/modificados

- CREADO: `backend/src/main/java/backend/mantenimiento/api/ErroresMantenimientoController.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/api/IntegracionRf17PendienteException.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/api/MantenimientoController.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/api/dto/ContratoRf17.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/dao/ContextoMantenimientoJdbcDao.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/integracion/RegistroIdempotenteMantenimiento.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/model/EstadoOperacionRegistro.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/model/MantenimientoNoDisponibleException.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/model/ResultadoRegistroMantenimiento.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/seguridad/IdentidadMantenimiento.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/seguridad/SesionMantenimientoNoValidada.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/service/LimitesAlmacenamiento.java`
- CREADO: `backend/src/main/java/backend/mantenimiento/service/MantenimientoHttpService.java`
- MODIFICADO: `backend/src/main/java/backend/mantenimiento/service/RegistroMantenimientoService.java`
- MODIFICADO: `backend/src/main/java/backend/mantenimiento/service/ValidadorRegistroMantenimiento.java`
- MODIFICADO: `backend/src/main/java/backend/mantenimiento/service/ValidadorRepuesto.java`
- MODIFICADO: `backend/src/main/java/backend/mantenimiento/service/VehiculoMantenimientoService.java`

## Archivos Kotlin creados/modificados

- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/data/ArchivoRegistroPendiente.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/data/MantenimientoApi.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/data/MantenimientoApiAdapter.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/data/RemoteMantenimientoRepository.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/domain/BorradorMantenimiento.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/domain/MantenimientoRepository.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/ui/RegistrarMantenimientoRoute.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/ui/RegistrarMantenimientoScreen.kt`
- CREADO: `android/app/src/main/java/com/mycar/app/features/mantenimiento/viewmodel/MantenimientoViewModel.kt`
