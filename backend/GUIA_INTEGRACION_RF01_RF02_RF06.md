# Guía de integración del backend RF01, RF02 y RF06

Fecha: 27 de octubre de 2026. Entrega de Elsa para Francesca.
Rama: `codex/backend-rf01-rf02-rf06`.
Commit base del worktree: `e65c90de70461774ad8ffc82093f7d6dad50a80a`.

Esta guía describe el código presente en la rama de trabajo y la verificación aislada de RF01/RF02/RF06 sobre la nueva base con V5. No certifica la integración autenticada RF02–RF08 ni una aprobación de toda la suite RF08. No se modifican contratos, documentos históricos ni migraciones.

## 1. Estado real

| Módulo | Implementado en backend | Pendiente o límite |
|---|---|---|
| RF01 | Validación, hash Argon2id, registro e inicialización atómica de cuatro filas | Versión legal aprobada y configuración del entorno. No crea sesión automática |
| RF02 | Login, restricción 3/10/10, sesiones por instalación, JWT, renovación con rotación, validación contra PostgreSQL y corrección temporal de emisión | Preparación de Inicio, cliente Android e integración RF08. El contrato REST continúa identificado como provisional |
| RF06 | Revocación individual, confirmación tras commit e idempotencia restringida | Diálogo, limpieza local, navegación y avisos Android; no está completo de extremo a extremo |
| RF08 | Código de Francesca heredado de la base, incluido su puerto | No hay adaptador de identidad ni cadena de seguridad específica conectada con RF02 |

RF01 crea en una transacción: usuario ACTIVA/USUARIO; suscripción al plan obtenido por código FREE y activo, con estado ACTIVA y sin fin; preferencias mantenimiento/soat/kilometraje en true y permiso NO_SOLICITADO; configuración global KM PAUSADO. No crea vehículos ni el recordatorio individual de cada vehículo.

## 2. Endpoints existentes

En el entorno local configurado, `server.port=8080`. El host y su disponibilidad no fueron comprobados en esta fase.

| Método | Ruta | Credencial/entrada | Éxito |
|---|---|---|---|
| POST | /api/v1/auth/registro | JSON de cuenta y consentimiento | 201 |
| POST | /api/v1/auth/login | JSON correo, contrasena, idInstalacion | 200 |
| POST | /api/v1/auth/renovar | JSON idInstalacion, refreshToken | 200 |
| GET | /api/v1/auth/sesion | Authorization: Bearer de acceso | 200 |
| POST | /api/v1/auth/logout | Authorization: Bearer de acceso, sin cuerpo | 204 |
| GET | /actuator/health | Sin autenticación | 200 si health está UP |

Registro, login y renovación declaran consumo y producción JSON. Sesión produce JSON. Logout no define DTO de entrada ni devuelve cuerpo. No existe un endpoint implementado de Inicio, Perfil, cancelación de logout o listado de sesiones en estos módulos.

## 3. Solicitudes y respuestas anonimizadas

Los bloques son JSON válido y utilizan los campos reales de los records. Nombre, correo, UUID, IDs y fechas son ficticios; la contraseña es una fixture didáctica, no una credencial real. Los marcadores de versión legal y access token deben sustituirse: no son una política aprobada ni un JWT utilizable. El refresh de 43 letras A ilustra únicamente el formato admitido; no pertenece a una sesión.

Las fechas ilustran una respuesta posible, no establecen TTL productivos. Para ejecutar el flujo deben utilizarse la versión aprobada del entorno y las credenciales realmente devueltas por el servidor.

### 3.1 Registro

```http
POST /api/v1/auth/registro
Content-Type: application/json
```

```json
{
  "nombre": "Ana",
  "apellido": "Perez",
  "correo": "persona@example.com",
  "fechaNacimiento": "1995-01-01",
  "contrasena": "EjemploSolo1!",
  "confirmacionContrasena": "EjemploSolo1!",
  "consentimientoPrivacidad": {
    "aceptado": true,
    "version": "<VERSION_LEGAL_VIGENTE>"
  }
}
```

201:

```json
{
  "resultado": "CUENTA_CREADA",
  "destino": "INICIO_SESION"
}
```

El ID interno creado no se expone en esta respuesta. La versión aceptada debe coincidir exactamente con la propiedad externa vigente. Sin una versión configurada válida, el registro devuelve 503 y no crea datos.

Validaciones principales: edad entre 18 y 85 años cumplidos en America/Lima; nombres de 2–100 puntos de código con el formato admitido; correo normalizado mediante recorte y minúsculas; contraseña de 8–64 puntos de código con mayúscula, minúscula, dígito y especial, sin espacios, y confirmación exacta. No se recorta ni transforma la contraseña. No hay validación semántica de nombres ni reglas especiales de Gmail.

### 3.2 Login

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "correo": "persona@example.com",
  "contrasena": "EjemploSolo1!",
  "idInstalacion": "11111111-2222-4333-8444-555555555555"
}
```

200, estructura real de Tokens:

```json
{
  "estado": "AUTENTICADO",
  "idSesion": 123,
  "accessToken": "<ACCESS_TOKEN_DEVUELTO_POR_LOGIN>",
  "accessTokenExpiraUtc": "2026-10-27T15:05:00Z",
  "refreshToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "sesionExpiraUtc": "2026-10-28T15:00:00Z"
}
```

Las respuestas exitosas de login y renovación incluyen `Cache-Control: no-store` y `Pragma: no-cache`. No crean cookie de autenticación ni JSESSIONID. El JSON no incluye idUsuario.

### 3.3 Renovación

```http
POST /api/v1/auth/renovar
Content-Type: application/json
```

```json
{
  "idInstalacion": "11111111-2222-4333-8444-555555555555",
  "refreshToken": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```

En ejecución debe enviarse el refresh actual recibido del servidor, con la misma instalación. La renovación no requiere access token en Authorization. El refresh ficticio mostrado no autoriza una renovación real.

200 tiene exactamente la misma estructura Tokens del login: estado, idSesion, accessToken, accessTokenExpiraUtc, refreshToken y sesionExpiraUtc. Cambian las credenciales, se conserva idSesion y no se extiende sesionExpiraUtc.

### 3.4 Consulta de sesión

```http
GET /api/v1/auth/sesion
Authorization: Bearer <ACCESS_TOKEN>
```

200:

```json
{
  "vigente": true,
  "idSesion": 123,
  "sesionExpiraUtc": "2026-10-28T15:00:00Z"
}
```

Incluye `Cache-Control: no-store`. No devuelve idUsuario ni emite credenciales. La sesión rechazada produce un error, no un 200 con vigente=false.

### 3.5 Logout

```http
POST /api/v1/auth/logout
Authorization: Bearer <ACCESS_TOKEN>
```

Sin cuerpo. Éxito: HTTP 204, sin JSON, con `Cache-Control: no-store`. No enviar idSesion, idUsuario o idInstalacion como selección libre del objetivo.

## 4. Bearer e identificación de instalación

El mecanismo de acceso es la cabecera `Authorization: Bearer <ACCESS_TOKEN>`, con un espacio entre esquema y token. El filtro admite el esquema sin distinguir mayúsculas/minúsculas. No utiliza un refresh como Bearer, tokens en query ni cookies de autenticación.

El backend exige un UUID idInstalacion aportado por el cliente; no lo genera para Android. Responsabilidad pendiente del cliente: generar un UUID por instalación mediante una API estándar, por ejemplo `java.util.UUID.randomUUID().toString()`, conservarlo en almacenamiento privado y reutilizarlo en login y renovación para esa instalación. Esa generación y persistencia Android no están implementadas por esta entrega; no generar otro UUID en cada petición.

No confundir idInstalacion (UUID), idSesion (long) e idUsuario (long interno). El UUID no es una contraseña ni acredita titularidad. El backend contrasta la instalación con el JWT firmado o el refresh y la sesión persistida. El almacenamiento de credenciales y su limpieza pertenecen al ciclo de sesión del cliente.

## 5. Login, renovación y cierre

### Login

1. Valida entrada y configuración JWT.
2. Bloquea usuario por correo normalizado y consulta hora PostgreSQL para el bloqueo temporal.
3. Si hay bloqueo vigente, rechaza sin comprobar contraseña ni extender el plazo.
4. Verifica la contraseña con el encoder Argon2id de RF01.
5. Contraseña errónea: registra fallos solo para cuenta ACTIVA; confirma el fallo antes del 401. Correo inexistente usa comprobación con hash ficticio y devuelve el mismo error público.
6. Contraseña correcta: exige cuenta ACTIVA antes de reiniciar fallos o crear credenciales.
7. Reinicia fallos sin borrar historial. Obtiene una nueva hora para reemplazar la sesión de esa instalación y calcular su vencimiento absoluto.
8. Crea sesión con refresh almacenado como hash. Después de insertarla consulta otra hora PostgreSQL para emitir el JWT.
9. Confirma la transacción antes de devolver éxito. Un fallo de emisión revierte creación, reemplazo y reinicio.

La política existente registra tres fallos en una ventana de diez minutos y aplica diez minutos de bloqueo. Solo ACTIVA acumula nuevos fallos. Un bloqueo de login no suspende la cuenta ni invalida por sí solo sus sesiones y renovaciones vigentes.

El reemplazo afecta únicamente las sesiones VIGENTE del mismo usuario/instalación: revoca si aún no vencieron o marca EXPIRADA si ya vencieron. Otras instalaciones permanecen independientes.

### Renovación

El refresh tiene 32 bytes aleatorios, representados como 43 caracteres Base64 URL sin padding. Solo se persiste su SHA-256 hexadecimal; no el original.

Renovación obtiene primero los bloqueos usuario → sesión, después lee la hora, comprueba vigencia/cuenta/instalación/hash actual y rota el refresh atómicamente. El refresh anterior deja de autorizar; una carrera no puede consumirlo dos veces. Los access tokens anteriores no se revocan por esa rotación y pueden seguir vigentes hasta su propio vencimiento.

Se conserva el vencimiento absoluto de sesión. Un acceso puede vencer mientras la sesión todavía permite renovar. Una sesión revocada o vencida no permite renovar.

### Logout

CloseSession reutiliza la verificación JWT de RF02, bloquea usuario → sesión y revalida cuenta ACTIVA, titularidad, instalación y vencimientos. Revoca solo esa fila y responde después del commit.

Repetir con la misma prueba JWT aún válida sobre esa sesión ya REVOCADA devuelve 204 sin cambiar su fecha de revocación. Esa excepción no autoriza acceso a vehículos u otros recursos.

Un access vencido no se renueva automáticamente para cerrar remotamente. Sin respuesta no se puede afirmar si el servidor hizo commit. El orden aprobado de limpieza y navegación local está descrito en RF06.md y sigue pendiente de Android; esta API no borra el almacenamiento del dispositivo.

## 6. Validación y límites temporales

SessionCredentials utiliza Nimbus con HS256; exige emisor `mycar-backend`, audiencia `mycar-session`, IDs positivos, instalación UUID, iat/exp presentes e iat < exp. El validador temporal se configura sin tolerancia adicional.

SessionAuthentication.validateAccess consulta PostgreSQL en cada petición protegida por SessionBearerFilter. Exige cuenta ACTIVA, sesión VIGENTE, revocada_utc nula y expira_utc futura; usuario e instalación deben coincidir con el JWT. Rechaza acceso vencido y acceso que exceda el límite absoluto persistido. Un JWT válido criptográficamente no basta.

La emisión calcula el mínimo entre instante reciente + accessTTL y vencimiento de sesión; trunca exp e iat a segundos. Rechaza si no queda un exp representable posterior al instante recibido. El truncamiento puede reducir vigencia en menos de un segundo y el token puede vencer durante commit/transporte: no se garantiza un TTL completo desde la recepción.

La corrección temporal ya incorporada evita reutilizar el instante previo a Argon2id para sesión y JWT. No cambió TTL, tolerancia, bloqueo ni contrato. La renovación ya leía hora después de ambos bloqueos y quedó intacta.

## 7. HTTP y formatos de error

RF01, RF02 y RF06 usan esta forma; erroresPorCampo es un objeto de nombres de campo a mensajes, no una lista:

```json
{
  "codigo": "SESION_INVALIDA",
  "mensaje": "Sesión no válida.",
  "erroresPorCampo": {}
}
```

| HTTP | Código | Origen/condición implementada |
|---|---|---|
| 400 | VALIDACION | Registro o autenticación: entrada inválida, JSON/fecha/UUID inválidos; renovación con formato inválido |
| 409 | CORREO_NO_DISPONIBLE | RF01: correo ocupado, sin revelar estado |
| 503 | REGISTRO_NO_DISPONIBLE | RF01: versión legal ausente/inválida, FREE no disponible o persistencia |
| 500 | ERROR_INTERNO | Error inesperado sanitizado |
| 401 | CREDENCIALES_INVALIDAS | Login: correo inexistente, contraseña incorrecta o bloqueo vigente |
| 403 | CUENTA_NO_HABILITADA | Login: contraseña correcta y cuenta no ACTIVA, sin bloqueo vigente |
| 401 | SESION_INVALIDA | Acceso/renovación/logout: prueba inválida, vencida, no autorizada, revocada salvo excepción de logout, cuenta no ACTIVA o identidad inconsistente |
| 503 | AUTENTICACION_NO_DISPONIBLE | Configuración JWT ausente/inválida o persistencia de autenticación no disponible |

Ejemplo de 401 de login:

```json
{
  "codigo": "CREDENCIALES_INVALIDAS",
  "mensaje": "Correo o contraseña incorrectos.",
  "erroresPorCampo": {}
}
```

Ejemplo de 403 de login, igual para SUSPENDIDA y PENDIENTE_ELIMINACION:

```json
{
  "codigo": "CUENTA_NO_HABILITADA",
  "mensaje": "La cuenta no está habilitada para iniciar sesión.",
  "erroresPorCampo": {}
}
```

Los errores de autenticación incluyen Cache-Control: no-store. Los handlers no devuelven valores rechazados, hashes, credenciales, SQL ni stacktraces. Los métodos/rutas denegados por Spring pueden recibir 403 fuera de estos handlers; no se garantiza allí este mismo JSON.

El advice de cada controlador tiene alcance limitado. RF08 mantiene otro contrato: `{codigo,mensaje,campos}`, incluido 401 SESION_NO_VALIDA desde su puerto y 403 CUENTA_NO_AUTORIZADA de dominio. No se unificaron ni cambiaron esos contratos.

## 8. Cadenas Spring Security existentes

| Orden | Coincidencia | Regla |
|---|---|---|
| 1 | /api/v1/auth/registro | Solo POST permitido |
| 2 | /api/v1/auth/login y /renovar | Solo POST permitido |
| 3 | /api/v1/auth/sesion | GET autenticado, con SessionBearerFilter |
| 4 | /api/v1/auth/logout | POST permitido hasta el caso de uso, que exige y valida la prueba JWT |
| 100 | Resto | Health público; resto denegado |

El permitAll de logout no elimina la prueba obligatoria de CloseSession. Las cadenas específicas deshabilitan CSRF, request cache, Basic, form login y logout automático, y usan STATELESS. El respaldo no deshabilita CSRF explícitamente. No hay autenticación por cookie; STATELESS no elimina las sesiones de negocio persistidas.

No existe cadena específica RF08. Sus rutas caen en el respaldo y permanecen denegadas; presentar un Bearer válido no añade el filtro de la cadena de sesión a otras rutas. No habilitar permitAll para resolverlo.

## 9. Dependencias y configuración

POM: Java 21 y Spring Boot 3.5.16. Se mantienen Web, Data JPA (también permite usar JdbcTemplate), Validation, Security, Actuator, PostgreSQL, Flyway y Spring Boot Test existentes.

Adiciones de Elsa:

| Dependencia | Versión |
|---|---|
| org.springframework.security:spring-security-oauth2-jose | Gestionada por el parent Spring Boot |
| org.bouncycastle:bcprov-jdk18on | 1.86 |

RF01 comparte su registrationPasswordEncoder con RF02: Argon2id, sal 16 bytes, hash 32 bytes, paralelismo 1, memoria 19456 KiB y dos iteraciones. No hay otro algoritmo de hash de contraseñas en RF08.

### Propiedades exactas consumidas por código

| Propiedad | Requisito |
|---|---|
| mycar.registro.privacidad.version-vigente | Versión legal externa, no vacía, máximo 40 puntos de código; igualdad exacta con consentimiento |
| mycar.auth.jwt-secret-base64 | Base64 estándar; al menos 32 bytes decodificados |
| mycar.auth.access-ttl | Duration ISO-8601 positiva, segundos completos, mínimo un segundo |
| mycar.auth.session-ttl | Duration ISO-8601 positiva, segundos completos; no menor que access-ttl |

No se incluyen valores secretos ni duraciones productivas predeterminadas. La configuración JWT se inicializa al utilizarla; ausente o inválida produce 503 en operaciones que la requieren. La clave debe mantenerse externamente entre reinicios/instancias que compartan sesiones; RF08 no necesita recibirla.

Estas propiedades deben suministrarse mediante configuración externa de Spring con sus nombres exactos. No se incorporan aliases personalizados de entorno para ellas en application.properties.

### Variables explícitas de application.properties

| Variable | Uso/default existente |
|---|---|
| MYCAR_ENV_FILE | spring.config.import; por defecto file:../.env[.properties] |
| POSTGRES_PORT | Puerto PostgreSQL; default 5432 |
| POSTGRES_DB | Nombre de BD; default mycar_mvp |
| POSTGRES_USER | Usuario; default mycar |
| POSTGRES_PASSWORD | Contraseña; sin default |

La importación predeterminada del archivo externo no está marcada optional. Si se usa otra ubicación, configurarla con MYCAR_ENV_FILE; no publicar el archivo ni sus valores. No se leyó su contenido para redactar esta guía.

Propiedades existentes relevantes: spring.datasource.url/username/password, spring.flyway.enabled=true, spring.flyway.locations=classpath:db/mvp, spring.flyway.default-schema=mycar, spring.jpa.hibernate.ddl-auto=validate y server.port=8080. No se modifica ninguna en esta entrega.

## 10. PostgreSQL y migraciones V1–V5

El entorno aislado existente utiliza PostgreSQL 17. La compatibilidad con otras versiones no se verificó aquí. El acceso a la BD requiere el esquema/catálogos y funciones de las migraciones, no solo tablas creadas manualmente.

| Migración | Contenido relevante/autoría de esta entrega |
|---|---|
| V1__esquema_mvp.sql | Esquema mycar existente; no creada ni modificada por esta entrega |
| V2__tablas_mvp.sql | Tablas, relaciones e índices, incluidos usuario/sesion/suscripcion; existente |
| V3__catalogos_mvp.sql | FREE y catálogos; existente |
| V4__funciones_y_triggers_mvp.sql | Funciones de fallos/reinicio y triggers; existente |
| V5__validacion_identificadores_vehiculo.sql | Restricciones de VIN, número de motor y serie; aporte RF08 de Francesca, heredado de la nueva base |

RF01/RF02/RF06 se desarrollaron y verificaron originalmente con V1–V4, sin cambiarlas. La base e65c90d ya contiene V5. Flyway descubre las migraciones disponibles de db/mvp; no está limitado a V4 por las descripciones históricas de los scripts.

Francesca informó que V5 está aplicada en su entorno. Esa aplicación no se consultó ni certificó aquí. Iniciar Spring contra una BD puede ejecutar migraciones: no se ha iniciado contra la base compartida durante esta preparación. No ejecutar SQL de V5 manualmente para esta entrega ni editar checksums/versiones.

## 11. Pruebas y estado de la evidencia

Requisitos para los ejecutores: JDK 21 con JAVA_HOME adecuado, Python 3, Docker Desktop con motor Linux operativo y herramientas del repositorio; Maven Wrapper puede requerir acceso a sus dependencias. No necesitan el .env compartido.

Desde la raíz del worktree, PowerShell:

```powershell
py -3 scripts/test_rf01.py
py -3 scripts/test_rf02.py
py -3 scripts/test_rf06.py
```

En un sistema con python3 en vez de py -3, usar python3 con el mismo script.

Las invocaciones anteriores requieren suministrar MYCAR_ENV_FILE únicamente al proceso de pruebas; sin ello, la importación predeterminada puede volver a exigir .env. No es necesario crear un archivo sintético. La siguiente invocación conjunta proporciona el entorno temporal y ejecuta las tres suites desde la raíz del worktree:

```powershell
py -3 -c "import os, subprocess, sys; e=os.environ.copy(); e['MYCAR_ENV_FILE']='optional:file:./target/rf01-rf02-rf06-sin-env.properties'; subprocess.run([sys.executable, '-B', 'scripts/test_rf06.py'], env=e, check=True)"
```

La ruta opcional se resuelve desde backend, directorio de trabajo de Maven. Antes de ejecutar, comprobar que backend/target/rf01-rf02-rf06-sin-env.properties no exista. JAVA_HOME debe apuntar a JDK 21. Para ejecutar por separado RF01 o RF02 con el mismo aislamiento de configuración, sustituir únicamente el nombre de script en la invocación anterior.

| Ejecutor | Suites seleccionadas |
|---|---|
| test_rf01.py | RF01 |
| test_rf02.py | RF02 + regresión RF01 |
| test_rf06.py | RF06 + regresiones RF01 y RF02 |

Crean contenedores PostgreSQL 17 propios con bases nuevas, puertos temporales de loopback, credenciales aleatorias y limpieza controlada por nombre/etiqueta. Los tests de integración exigen variables/URLs de esas bases desechables. No usan Compose ni el volumen compartido.

Variables exactas de pruebas que los ejecutores suministran:

- MYCAR_RF01_TEST_URL, MYCAR_RF01_TEST_USER, MYCAR_RF01_TEST_PASSWORD.
- MYCAR_RF02_TEST_URL, MYCAR_RF02_TEST_USER, MYCAR_RF02_TEST_PASSWORD, MYCAR_RF02_TEST_SIGNING_KEY.
- MYCAR_RF06_TEST_URL, MYCAR_RF06_TEST_USER, MYCAR_RF06_TEST_PASSWORD, MYCAR_RF06_TEST_SIGNING_KEY.

Son variables del entorno de pruebas, no configuración productiva; no compartir sus valores.

Resultado final de la fase 4C, repetido en este worktree sobre e65c90d el 27 de octubre de 2026, después de corregir el tiempo de emisión:

| Suite | Ejecutadas | Aprobadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|---:|
| RF01 | 45 | 45 | 0 | 0 | 0 |
| RF02 | 71 | 71 | 0 | 0 | 0 |
| RF06 | 31 | 31 | 0 | 0 | 0 |
| Total | 147 | 147 | 0 | 0 | 0 |

Las 147 pruebas anteriores del repositorio original se reprodujeron sobre e65c90d: 147 aprobadas, cero fallos, cero errores y cero omitidas; BUILD SUCCESS y salida 0 del ejecutor. Se utilizó Java 21 y un contenedor PostgreSQL 17 con tres bases nuevas; Flyway aplicó V1–V5. El contenedor temporal fue retirado. La comparación SHA-256 y los estados Git confirmaron que no cambiaron fuentes, pruebas, ejecutores, configuración, migraciones, RF08, Android, repositorio original ni respaldo. No se utilizó la base compartida. La evidencia de esta repetición quedó en backend/target/phase4c-20261027-160035 y no se incluye en Git.

La ejecución definió exclusivamente para el proceso de pruebas y sus subprocesos MYCAR_ENV_FILE=optional:file:./target/rf01-rf02-rf06-sin-env.properties. Se comprobó que esa ruta no existiera y no se creó el archivo. No se creó, copió ni leyó el .env original. El cambio de entorno no modificó configuración productiva ni ejecutores.

La fase 4B había detectado 61 errores de contexto RF01/RF02/RF06 por la importación obligatoria de .env; esta repetición los resolvió sin cambiar archivos. Los 31 errores de contexto RF08 por HttpSecurity en pruebas con WebEnvironment.NONE no fueron corregidos ni repetidos en la fase 4C. Siguen pendientes de coordinación con Francesca. Las 147 aprobadas no certifican RF08 ni el adaptador/cadena todavía ausentes.

Los ejecutores generan informes y logs en backend/target. No forman parte de esta entrega. Algunos contextos históricos pueden escribir una contraseña de desarrollo de Spring en logs; compartir solo resultados sanitizados. Los ejecutores seleccionan clases explícitas: una futura clase de integración RF02–RF08 necesitará una invocación que la incluya.

## 12. Discrepancias históricas y pendientes

- RF02.md describe una barrera NonActiveAccountDecisionPending y ausencia de política 403. El código actual ya verifica contraseña antes de rechazar cuenta no ACTIVA, conforme a EC-RF02-01; devuelve 403 CUENTA_NO_HABILITADA y cuenta fallos solo en ACTIVA.
- RF02.md/RF06.md conservan resultados anteriores de 47 pruebas RF02 y totales 92/123. EC-RF02-01 registra una etapa posterior de 136; la corrección temporal llevó el último resultado a 147.
- Algunos metadatos de scripts aún dicen que login no ACTIVA está pendiente y que solo se aplican V1–V4. Son descripciones históricas, no ramas vigentes ni filtros de Flyway. No se editaron esos archivos.
- El documento inicial de integración RF08 también describe fases previas. Para saber qué está disponible se consultaron los controladores y seguridad actuales.
- Siguen pendientes Inicio, Android RF01/RF02/RF06, configuración legal real, TTL/clave de despliegue y la integración autenticada de RF08.
- El almacenamiento RF08 de solicitudes pendientes y borradores debe conciliarse con la limpieza de datos sensibles de RF06. Esa decisión no se implementa aquí.
- La identidad local de cuenta para separar almacenamiento Android requiere acuerdo: las respuestas actuales de login y sesión no exponen idUsuario como campo.

Estos pendientes no se presentan como implementados ni se resuelven cambiando contratos en esta guía.

## 13. Archivos que Francesca debe consultar

Las rutas de esta tabla son relativas a la raíz del repositorio.

| Responsabilidad | Archivos/clases |
|---|---|
| Registro HTTP | backend/src/main/java/backend/registro/web/RegistrationRequest.java, RegistrationController.java, RegistrationErrorHandler.java |
| Registro atómico y validaciones | backend/src/main/java/backend/registro/RegisterAccount.java, RegistrationValidator.java, RegistrationPrivacyPolicy.java, RegistrationConfiguration.java; persistence/JdbcRegistrationStore.java |
| DTO y endpoints RF02 | backend/src/main/java/backend/autenticacion/web/AuthenticationController.java |
| Principal y casos de uso | backend/src/main/java/backend/autenticacion/SessionAuthentication.java |
| JWT y refresh | backend/src/main/java/backend/autenticacion/SessionCredentials.java |
| Persistencia y vigencia | backend/src/main/java/backend/autenticacion/AuthenticationStore.java y persistence/JdbcAuthenticationStore.java |
| Filtro y errores | backend/src/main/java/backend/autenticacion/web/SessionBearerFilter.java, AuthenticationErrors.java, AuthenticationErrorHandler.java; backend/autenticacion/AuthenticationFailure.java bajo src/main/java |
| Cadenas | backend/src/main/java/backend/autenticacion/web/LoginSecurityConfiguration.java; backend/src/main/java/backend/registro/web/RegistrationSecurityConfiguration.java; backend/src/main/java/backend/cierresesion/web/LogoutSecurityConfiguration.java |
| Cierre transaccional | backend/src/main/java/backend/cierresesion/CloseSession.java, SessionRevocationStore.java, persistence/JdbcSessionRevocationStore.java y web/LogoutController.java |
| Puerto de Francesca | backend/src/main/java/backend/vehiculos/seguridad/IdentidadVehiculos.java y SesionVehiculosNoValidada.java |
| Consumo del puerto RF08 | backend/src/main/java/backend/vehiculos/controller/VehiculosController.java y ErroresVehiculosController.java |
| Configuración/dependencias | backend/src/main/resources/application.properties y backend/pom.xml |
| Esquema | backend/src/main/resources/db/mvp/V1–V5 |
| Reglas y evidencia histórica | backend/EC-RF02-01.md, backend/RF01.md, backend/RF02.md y backend/RF06.md |
| Pruebas | backend/src/test/java/backend/registro, autenticacion y cierresesion; scripts/test_rf01.py, test_rf02.py y test_rf06.py |

El puerto existente mantiene la firma `long exigirUsuarioAutenticado(Authentication autenticacion)`. Un adaptador futuro puede consumir el principal tipado producido por el filtro, comprobar autenticación/tipo y devolver userId. La validación JWT y persistida pertenece al filtro/caso de uso RF02. Falta aportar un único bean y la cadena específica para RF08; no hace falta que Francesca reciba la clave JWT ni copie esa lógica.

La aceptación completa requiere pruebas con sesión real y coordinación de planes/cupo con Sebastián. Esta guía no implementa el adaptador, no modifica RF08 y no publica la rama.
