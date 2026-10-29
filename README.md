# MyCar

App de gestión vehicular: registro de vehículos, kilometraje, mantenimientos y
alertas preventivas. El MVP cubre 11 de los 64 requerimientos funcionales (RF).

## Estado actual

| RF | Funcionalidad | Responsable | Estado en main |
|---|---|---|---|
| RF01 | Registro de cuenta | Elsa | Backend y Android |
| RF02 | Inicio de sesión | Elsa | Backend y Android |
| RF06 | Cierre de sesión | Elsa | Backend y Android |
| RF08 | Registrar vehículo (con el cupo del plan Free) | Francesca | Backend y Android |
| RF09 | Listar vehículos | Francesca | Backend y Android |
| RF10 | Ver vehículo | Francesca | Pendiente |
| RF14 | Actualizar kilometraje | Francesca | Pendiente |
| RF17 | Registrar mantenimiento | Nayu | Backend y Android |
| RF18 | Historial de mantenimientos | Nayu | Backend y Android |
| RF22 | Alertas preventivas: programación | Nayu | En la rama `feat/mantenimientos-rf22-rf23` |
| RF23 | Alertas preventivas: sincronización | Nayu | En la rama `feat/mantenimientos-rf22-rf23` |
| RF50 | Inicio y navegación común | Francesca | Android |

Planes e integración de la BD y el backend: Sebastián (ver la sección
[Planes e integración](#planes-e-integración-sebastián)).

## Tecnología

- Android: Kotlin y Jetpack Compose.
- Backend: Java 21 y Spring Boot 3.5.
- Base de datos: PostgreSQL 17 en Docker. Base de desarrollo `mycar_mvp`, esquema `mycar`.
- Migraciones: Flyway, carpeta `backend/src/main/resources/db/mvp` (V1 a V5).

La base tiene 21 tablas de negocio y soporte. V1 conserva el esquema inicial, V2
crea las tablas, V3 carga los catálogos, V4 agrega funciones y triggers y V5 la
validación de identificadores de vehículo. No ejecutar el instalador SQL manual.

Documentación de la base de datos: [Base de datos MVP](docs/db/mvp/README.md).

## Estructura del repositorio

- `android/`: app Android.
- `backend/`: API REST (controladores, servicios, DAO).
- `docs/`: documentación por módulo (`db`, `vehiculos`, `inicio`).
- `scripts/`: pruebas con PostgreSQL desechable y auxiliares de integración local.
- `compose.yml`: PostgreSQL de desarrollo.

## Requisitos

- Git.
- Docker Desktop.
- JDK 21.
- Python 3.12 (para los scripts de `scripts/`).
- Android Studio y Android SDK.

## Descargar el proyecto

```powershell
git clone https://github.com/Fran-143/MYCAR-EQUIPO1.git
cd MYCAR-EQUIPO1
```

## Configuración local

En una instalación nueva:

```powershell
Copy-Item .env.example .env
notepad .env
```

Configurar:

- POSTGRES_DB=mycar_mvp
- POSTGRES_USER=mycar
- POSTGRES_PASSWORD con una contraseña local propia.
- POSTGRES_PORT=5432

No subir `.env` a GitHub.
No sobrescribir un `.env` existente sin revisar sus valores.

## Iniciar PostgreSQL

Con Docker Desktop abierto:

```powershell
docker compose up -d --wait postgres
docker compose ps
```

El servicio debe aparecer `healthy`.

Si el volumen Docker ya existía, cambiar POSTGRES_DB no crea automáticamente
otra base. Coordinar su preparación antes de continuar.
No eliminar el volumen para resolverlo sin respaldo.

## Probar la app como usuario

Esta es la forma de ver la aplicación funcionando de punta a punta: crear cuenta,
iniciar sesión, registrar vehículos y mantenimientos.

1. Levantar PostgreSQL (sección anterior).
2. Iniciar el backend de integración local, en una terminal que se deja abierta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\iniciar_backend_integracion_local.ps1 -VersionPrivacidad '1.0'
```

   El backend queda en el puerto 8081. La versión de privacidad `1.0` corresponde
   al documento académico local y permite crear cuentas desde la app. La clave de
   sesión se genera fuera del repositorio.

3. Comprobar que responde:

```powershell
Invoke-RestMethod http://localhost:8081/actuator/health
```

   Resultado esperado: `status UP`.

4. En Android Studio, abrir la carpeta `android`, esperar la sincronización de
   Gradle, iniciar un emulador y ejecutar la app (configuración `app`, variante debug).
   La variante debug usa `http://10.0.2.2:8081/`, que es el backend de tu equipo visto
   desde el emulador. Para otra dirección: `-Pmycar.apiBaseUrl=...` y recompilar.

5. Recorrido sugerido: crear cuenta, iniciar sesión, registrar un vehículo, intentar
   registrar un segundo vehículo (la app informa que el plan Free ya alcanzó su límite),
   abrir Mantenimientos e Historial y cerrar sesión.

Más detalle: [Integración de autenticación con vehículos y RF50](docs/inicio/INTEGRACION_ELSA_RF50.md)
y [Probar RF08 en la app](docs/vehiculos/PROBAR_RF08_EN_APP.md).

## Iniciar solo el backend (puerto 8080)

Para comprobar únicamente que el servidor arranca:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Mantener esa terminal abierta. Flyway aplicará las migraciones pendientes.
Si se ejecuta desde IntelliJ, usar `backend` como directorio de trabajo.

En otra terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado: `status UP`.

## Pruebas

Pruebas del backend (sin base de datos externa):

```powershell
cd backend
.\mvnw.cmd test
```

Las pruebas que usan PostgreSQL real se omiten en esa ejecución, a propósito. Se
corren con los scripts de `scripts/`, que crean un PostgreSQL 17 desechable con
credenciales aleatorias, no usan `.env` ni el volumen de desarrollo y al terminar
retiran su propio contenedor. Con Docker Desktop, JDK 21 y Python 3 instalados,
desde la raíz:

```powershell
python scripts/test_mvp_db.py
python scripts/test_rf08_api.py --incluir-rf09 --incluir-sesion
python scripts/test_rf01.py
python scripts/test_rf02.py
python scripts/test_rf06.py
```

- `test_mvp_db.py`: migraciones, integridad, transacciones, concurrencia, Spring Boot y health.
- `test_rf08_api.py`: registro y listado de vehículos, cupo del plan, contrato HTTP y,
  con `--incluir-sesion`, login real, identidad, aislamiento, renovación y revocación.
- `test_rf01.py`, `test_rf02.py` y `test_rf06.py`: cuenta, inicio y cierre de sesión.

En Linux o macOS usar `python3` en lugar de `python`.

## Planes e integración (Sebastián)

En el MVP solo el plan **Free** está operativo (se muestra como «Gratis»). Premium
queda para una entrega posterior.

Reglas:

- Free permite 1 vehículo registrado en total, contando activos e inactivos.
- El límite se valida solo al registrar un vehículo (RF08), no al desactivarlo.
- La suscripción Free ACTIVA la crea el backend en la misma transacción del registro
  de cuenta (RF01). La app no envía el plan y no hay trigger en la base de datos.
- Solo puede existir una suscripción ACTIVA por usuario (índice único parcial).
- El id del usuario sale siempre de la sesión validada, nunca de la petición.

API:

- `GET /api/v1/vehiculos/cupo-registro` devuelve `plan`, `limite`, `registrados` y
  `puedeRegistrar`. Es orientativa y no reserva cupo.
- `POST /api/v1/vehiculos` responde 409 `LIMITE_FREE` si ya llegó al límite y 409
  `PLAN_FREE_NO_DISPONIBLE` si no hay una suscripción Free vigente. Un reintento ya
  confirmado devuelve el mismo vehículo.

Concurrencia: al guardar, se bloquea la fila del usuario con `FOR NO KEY UPDATE` para
que dos solicitudes simultáneas no creen dos vehículos Free.

## Trabajo compartido

- Crear una rama por tarea.
- Integrar mediante pull request revisado.
- Coordinar cambios de configuración y dependencias.
- Coordinar el número de cada migración nueva.
- No modificar una migración que ya fue aplicada.
- No ejecutar los SQL del modelo anterior sobre `mycar_mvp`.
- No crear tablas manualmente que después se repetirán con Flyway.
- No subir contraseñas, tokens, registros de ejecución ni respaldos de la base de datos.
