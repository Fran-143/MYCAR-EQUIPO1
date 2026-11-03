# Bloque disponible sin autenticación de Elsa — RF08 y RF09

Base: main e65c90d (PR #7 integrado). Fecha: 27/10/2026.

## Entregado

RF08: Guardar se habilita solo con formulario local válido y sin operación en curso.
Los campos editados muestran errores de formato. Las secciones con errores no se pueden cerrar mientras
persistan esos errores. El cambio de motor/combustible/aceite elimina errores de los campos afectados.
Los validadores existentes siguen siendo la fuente de reglas, y el backend vuelve a validar.

RF09: DTO, consulta SQL de solo lectura, servicio y GET protegido de listado propio. Incluye contadores,
estados, búsqueda por placa/marca/modelo y marca + modelo, principal primero y kilometraje vigente.
No se añade ninguna tabla ni migración. API detallada en API_RF09_PROPUESTA.md.

Android: repositorio Retrofit, ViewModel, listado con estados de carga/error/sesión/vacío/sin coincidencias,
filtros y búsqueda. VehiculosRoute conecta listado → comprobación de cupo → registro → listado/Inicio.
La comprobación de cupo no reserva y el POST revalida dentro de su transacción.
Se comprueban pendientes antes del cupo para recuperar operaciones ya confirmadas o inciertas.
El cambio de clave de cuenta/ciclo cancela el alcance de navegación; la limpieza completa de RF06 sigue pendiente.

DEBUG: entrada directa al formulario, Volver/Cancelar → Mis vehículos de prueba → menú → conexión.
Mostrar ejemplo permite probar tarjeta, filtro activo/inactivo, contadores, búsqueda y cupo.
Estos datos no son registros reales, no se guardan en PostgreSQL y no se mezclan con el repositorio autenticado.
RELEASE conserva la entrada principal anterior; requiere la integración real de sesión/navegación.

Configuración de red DEBUG: includeSubdomains=false explícito en los dos dominios locales existentes.
No añade destinos, permisos ni permite subdominios. MainActivity queda no exportada solo en DEBUG,
pues el launcher de esa variante es RegistroVehiculoDemoActivity; release conserva su declaración original.

## Integración real pendiente

Elsa debe aportar su rama: adaptador IdentidadVehiculos, cadena de seguridad, Retrofit autenticado,
identificador estable de cuenta y contrato de renovación/logout. El host debe invocar VehiculosRoute con:

- claveCuenta: servidor + cuenta + ciclo de sesión, sin credenciales.
- registroRepository: RemoteRegistroVehiculoRepository del cliente autenticado.
- listadoRepository: RemoteListadoVehiculosRepository del mismo cliente autenticado.
- pendientes: ArchivoSolicitudRegistroPendiente de ese servidor/cuenta.
- onIrInicio y onIniciarSesion conectados al host real.
- onVerDetalle abre RF10 (`feat/detalle-vehiculo-rf10`); ver `BLOQUE_RF10.md` y `API_RF10.md`.

RF06: acordar y conectar limpieza local, SavedStateHandle/ViewModel y trabajos pendientes.
No se implementa logout ni se modifica su política sin el contrato. Un 401 sigue conservando el pendiente
para comprobar el resultado al reautenticar la misma cuenta. Logout no revierte un COMMIT del servidor.

Falta aceptación con sesión real, servidor y emulador. Las pruebas de lógica/PG/MVC no sustituyen ese recorrido.
No se implementan fotos personalizadas, TIVE, SOAT, pagos ni actualización de kilometraje RF14.

## Comprobaciones para el equipo

Desde backend:

```powershell
py -3 ..\scripts\test_rf08_api.py --incluir-rf09
```

Usa PostgreSQL 17 desechable. No utiliza .env, Compose ni datos locales. Elimina solo su contenedor.
Ejecuta BD, servicios RF08/RF09, consultas, MVC, contexto y rechazo HTTP anónimo de las cinco rutas.

Desde android:

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug :app:processReleaseMainManifest
```

Con un emulador disponible, para ejecutar además las tres pruebas de interfaz:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

El informe de distribución distingue entre pruebas ejecutadas y pruebas de interfaz solo compiladas.
