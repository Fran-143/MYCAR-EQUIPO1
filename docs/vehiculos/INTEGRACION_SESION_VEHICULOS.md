> Actualización: la integración vigente con el login de Elsa (PR #11) se describe en
> [INTEGRACION_ELSA_RF50.md](../inicio/INTEGRACION_ELSA_RF50.md). Las referencias al login temporal de esta guía son históricas.

# Integración RF02/RF06 → RF08/RF09

Base revisada: main 032aef221e796fad68cc2772fe98b7a6b9333ba9, después del PR #9.
Fecha: 27/10/2026. Entrega de código para revisión en feat/integracion-sesion-vehiculos.

## Comportamiento

Se reutilizan íntegramente el login, la renovación, el filtro SessionBearerFilter y el cierre remoto de Elsa.
La nueva cadena de vehículos (orden 10) se aplica al prefijo /api/v1/vehiculos; autentica GET/POST existentes y
rechaza métodos/rutas ajenos. No cambia las cadenas de auth, la de respaldo ni mantenimiento.
El adaptador IdentidadSesionVehiculos solo admite SessionAuthentication.Principal autenticado.
No interpreta Authentication.getName(), cabeceras de usuario, parámetros ni un JWT en el controlador.
La comprobación de firma, cuenta, instalación, sesión, expiración y revocación sigue en RF02 y PostgreSQL.

Se propone una única consulta auxiliar nueva, sin modificar las respuestas existentes de Elsa:

    GET /api/v1/vehiculos/identidad
    Authorization: Bearer <accessToken>
    200 {"idUsuario": <propietario validado por RF02>}
    Cache-Control: no-store

No acepta un selector de usuario. Su ID sirve para separar archivos locales por servidor/cuenta, nunca para
autorizar una petición del backend. El ID no es un secreto ni una credencial. Contrato para revisar en este PR.
Las cinco rutas anteriores de vehículos conservan sus contratos. Un 401 SESION_INVALIDA del filtro y un
401 SESION_NO_VALIDA del módulo conducen al mismo tratamiento de sesión en Android.

## Android

SesionVehiculosActivity aporta un host mínimo de integración con correo/contraseña reales. No sustituye
la entrega visual definitiva de RF01/RF02/Perfil de Elsa. Se abre desde Inicio y desde el menú DEBUG mediante
«Vehículos · sesión real». Se preservan la demostración anterior y el acceso a mantenimiento de Nayu.
La demostración sigue sin guardar datos. La entrada real consulta y escribe a través de Retrofit y Spring.

El UUID de instalación se crea una vez y vive en noBackupFilesDir, separado de credenciales y borradores.
Access y refresh permanecen exclusivamente en memoria del ViewModel: no se guardan en SharedPreferences,
Bundle, SavedStateHandle, logs, archivos ni backup. Tras muerte del proceso se exige login de nuevo.
Un login primero verifica el propietario mediante /vehiculos/identidad y después abre sus repositorios y archivo.
Se conserva la contraseña únicamente durante el intento, no se recorta y no se guarda en rememberSaveable.
FLAG_SECURE evita capturas y miniaturas del Activity de acceso/vehículos.

El interceptor agrega Bearer solo a peticiones del mismo esquema/host/puerto. No sigue redirecciones ni
habilita reintentos de transporte. Renueva antes de enviar si el acceso está a menos de diez segundos de vencer;
una exclusión mutua evita consumir el refresh dos veces en peticiones paralelas. Se exige conservar idSesion y
vencimiento absoluto. Una respuesta perdida de renovación exige login de nuevo, sin reutilizar el refresh.
No se reenvía automáticamente un POST que devuelve 401. RF08 conserva su UUID y solicitud persistida.
La incertidumbre de commit sigue resolviéndose mediante consulta de operación y reenvío idempotente.

## Caducidad frente a cierre explícito

- Ante 401, se invalidan credenciales/clientes/ViewModels de ese ciclo y se vuelve al acceso. La solicitud
  pendiente permanece privada en disco y solo se recupera después de autenticar la misma cuenta/servidor.
- Un nuevo ciclo tiene otra clave y otros ViewModels, incluso si pertenece al mismo usuario. Los callbacks de
  un ciclo anterior no pueden cerrar el nuevo ni escribir en su almacén. Los datos de formulario de este host
  usan SavedStateHandle solo en memoria; no se registran en el Bundle de la Activity.
- El cierre explícito requiere diálogo. Cancelar o tocar fuera no altera sesión ni envía POST.
- Al confirmar, se obtiene una prueba efímera sin renovar, se eliminan credenciales, se cancelan clientes y
  ViewModels, se borra el archivo RF08 de esa cuenta y se muestra Bienvenida antes de esperar red.
- Luego se intenta POST /auth/logout una sola vez. 204 confirma cierre remoto. Error, falta de red o 401
  solo confirman el cierre local: la UI no afirma una revocación que no observó.
- Si falla la eliminación física del archivo, el acceso permanece cerrado y no se permite otra cuenta hasta
  reintentar la limpieza. No se oculta ese error ni se almacena una cola de revocaciones.
- El guardián PendientesSesionVehiculos serializa guardar/cerrar y rechaza escrituras tardías. La eliminación
  RF06 no necesita deserializar un archivo incompleto. No se eliminan datos del servidor.

Un POST ya recibido por el servidor podría confirmar después del cierre local. Al entrar otra vez se consulta
el listado antes de abrir un registro nuevo: el borrado de la solicitud local no revierte el vehículo guardado.

La limpieza implementada abarca los datos de este host y de RF08/RF09. No declara una limpieza global del
módulo Android de Elsa/Nayu todavía no conectado. La integración de sus almacenes requiere sus callbacks.
No se presenta RF06 completo desde Perfil ni RF02 completo (Inicio, registro visual, etc.).

## Preparar desarrollo local

El build DEBUG actual usa http://10.0.2.2:8081/. Este bloque conserva ese valor existente: el emulador Android
Studio llega por él al puerto 8081 del PC. El backend anterior usaba 8080 por defecto. No confundir puerto
PostgreSQL 5432, backend 8081 y adb 5037. No hace falta adb reverse para 10.0.2.2.

Desde la raíz, con Docker Desktop encendido y .env local ya configurado:

    docker compose up -d --wait postgres
    .\scripts\iniciar_backend_integracion_local.ps1

El auxiliar ejecuta el backend en 8081, genera una clave JWT de desarrollo una única vez fuera de Git en
%LOCALAPPDATA%\MyCar\desarrollo-local\autenticacion.properties y la conserva entre arranques.
No imprime ni copia la clave al repositorio. PT5M y P1D son duraciones técnicas exclusivamente locales,
no una aprobación de TTL productivos. Para usar otras duraciones/despliegue debe decidirlas el equipo.
El auxiliar no modifica application.properties, .env, tablas ni migraciones. Spring/Flyway opera como antes.

Para crear una cuenta nueva a través de RF01 hace falta la versión legal efectivamente aprobada y la política
leída por la persona. Pedir ese valor a Elsa; no inventar una versión para sortear el requisito. Con ese valor:

    .\scripts\iniciar_backend_integracion_local.ps1 -VersionPrivacidad "<VERSION_APROBADA>"

En otra terminal, opcionalmente:

    .\scripts\crear_cuenta_integracion_local.ps1 -VersionPrivacidad "<VERSION_APROBADA>"

Este segundo auxiliar crea datos reales de cuenta mediante el endpoint RF01; no es una semilla ni inserta SQL.
Pregunta los datos y las contraseñas sin mostrarlas, y requiere consentimiento explícito. No genera credenciales
compartidas ni imprime tokens. Para una cuenta ya existente no hace falta ejecutar el auxiliar de registro.
Los secretos/configuración de producción no se incluyen ni se deben enviar al chat o al equipo.

## Verificación repetible

Desde backend, Docker Desktop operativo y JDK 21:

    py -3 ..\scripts\test_rf08_api.py --incluir-rf09 --incluir-sesion

Contenedor PostgreSQL 17 desechable, sin .env local ni base compartida. Ejecuta las restricciones V1–V5,
los servicios/validaciones/contratos previos y las nuevas pruebas de sesión real. Limpia solo su contenedor.
El nuevo flag está desactivado por defecto para conservar las invocaciones anteriores.

Regresión adicional RF01/RF02/RF06, desde raíz, aislando la importación de .env solo en el proceso:

    $envAnteriorMyCar = $env:MYCAR_ENV_FILE
    try {
        $env:MYCAR_ENV_FILE = 'optional:file:./target/auth-regresion-sin-env.properties'
        py -3 scripts/test_rf06.py
        if ($LASTEXITCODE -ne 0) { throw 'Regresión de autenticación fallida.' }
    } finally { $env:MYCAR_ENV_FILE = $envAnteriorMyCar }

Desde android:

    .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
    .\gradlew.bat :app:connectedDebugAndroidTest

La segunda orden necesita un emulador encendido y conectado. Incluye una nueva prueba con almacenamiento
Android real: aislamiento entre dos cuentas y cierre que no afecta la otra, sin usar datos de producción.

## Recorrido manual de aceptación

1. Backend en 8081, health UP y cuenta real disponible. Abrir app DEBUG, salir de la demostración al menú
   y pulsar «Vehículos · sesión real». Alternativa: Inicio → Mis vehículos · iniciar sesión.
2. Credenciales incorrectas: error público, sin listado. Correctas: sesión activa → Mis vehículos.
3. Cuenta sin vehículos: lista vacía real; agregar datos válidos y guardar; comprobar vehículo y kilometraje
   en PostgreSQL/listado. Free solo permite uno; no crear otra suscripción desde RF08.
4. Volver, buscar y filtrar. Rotar durante formulario; no duplicar envío. Un cierre del proceso exige login.
5. Si se pierde red durante POST, conservar operación y recuperar al entrar de nuevo en la misma cuenta.
6. Cerrar sesión → Cancelar y tocar fuera: conservar sesión. Confirmar: Bienvenida inmediata y archivo
   pendiente borrado. Atrás no debe devolver formulario ni datos de la cuenta cerrada.
7. Cerrar sin red: aviso local sin afirmar cierre remoto. Login de otra cuenta: no ver vehículos ni borradores
   anteriores. Recuperar conectividad no debe recrear un archivo eliminado.
8. Esperar vencimiento de acceso: siguiente consulta renueva en memoria. Una sesión revocada/expirada
   exige login. No hacer pruebas de manipulación manual sobre datos reales; la suite las verifica aislada.

## Alcance pendiente

No hay cambios de BD, catálogos, migraciones, contrato de Free, RF10/RF14 ni lógica de mantenimiento.
No se implementa aquí el registro visual RF01, Perfil/RF06 definitivo, API de mantenimiento autenticada,
SOAT, TIVE, imágenes ni despliegue público. RELEASE sigue usando la URL de marcador previa; no es un APK
listo para clientes hasta configurar el backend HTTPS, la política legal y las decisiones de despliegue.
La UI real, el modo offline y la prueba instrumentada se deben ejecutar también en el emulador del equipo.
