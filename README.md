# MyCar

App de gestión vehicular: registro de vehículos, kilometraje, mantenimientos y
alertas preventivas. El MVP cubre 11 de los 65 requerimientos funcionales (RF).

## Estado actual (verificado en `main`)

| RF | Funcionalidad | Responsable | Estado en `main` |
|---|---|---|---|
| RF01 | Registro de cuenta | Elsa | Backend y Android |
| RF02 | Inicio de sesión | Elsa | Backend y Android |
| RF06 | Cierre de sesión | Elsa | Backend y Android |
| RF08 | Registrar vehículo (cupo plan Free) | Francesca | Backend y Android |
| RF09 | Listar vehículos | Francesca | Backend y Android |
| RF10 | Detalle de vehículo | Francesca | Backend y Android |
| RF14 | Actualizar kilometraje | Francesca | Backend y Android |
| RF17 | Registrar mantenimiento | Nayu | Backend y Android |
| RF18 | Historial de mantenimientos | Nayu | Backend y Android |
| RF22 | Programar alerta preventiva | Nayu | Backend y Android |
| RF23 | Consultar / sincronizar alertas | Nayu | Backend y Android |
| RF50 | Inicio y navegación común | Francesca | Android |

Los otros RF del catálogo RF01–RF65 permanecen documentados y pendientes de desarrollo.

Planes e integración de la BD y el backend: Sebastián (ver
[Planes e integración](#planes-e-integración-sebastián)).

## Tecnología

- Android: Kotlin y Jetpack Compose.
- Backend: Java 21 y Spring Boot 3.5.
- Base de datos: PostgreSQL 17 en Docker. Base de desarrollo `mycar_mvp`, esquema `mycar`.
- Migraciones: Flyway en `backend/src/main/resources/db/mvp` (**V1 a V9**). V6 RF22 POR_TIEMPO sin lectura; V7 placa dual; V8 OTP esquema; V9 RF22 `origen_calculo` dual.

Documentación de la base: [docs/db/mvp/README.md](docs/db/mvp/README.md).  
Guía RF22/RF23: [docs/vehiculos/GUIA_RF22_RF23.md](docs/vehiculos/GUIA_RF22_RF23.md).

## Estructura del repositorio

- `android/`: app Android.
- `backend/`: API REST (controladores, servicios, DAO).
- `docs/`: documentación por módulo (`db`, `vehiculos`, `inicio`, `requisitos`).
- `scripts/`: pruebas con PostgreSQL desechable y auxiliares de integración local.
- `compose.yml`: PostgreSQL de desarrollo.

## Actualizar el proyecto (compañeras del equipo)

Si ya tienen el repo clonado y **no les aparecen alertas RF22/RF23**, estaban en `main` antiguo.
Ahora RF10, RF14, RF22 y RF23 están en **`main`**. En PowerShell, desde la carpeta del proyecto:

```powershell
cd RUTA\MYCAR-EQUIPO1
git fetch origin
git checkout main
git pull origin main
```

Comprobar que llegaron las alertas:

```powershell
Test-Path .\android\app\src\main\java\com\mycar\app\features\alertas
Test-Path .\backend\src\main\java\backend\alertas
Test-Path .\backend\src\main\resources\db\mvp\V6__rf22_tiempo_sin_lectura.sql
```

Los tres deben devolver `True`.

Si tienen cambios locales sin subir y `git pull` falla:

```powershell
git stash push -u -m "backup-local"
git pull origin main
git stash pop
```

Clon nuevo:

```powershell
git clone https://github.com/Fran-143/MYCAR-EQUIPO1.git
cd MYCAR-EQUIPO1
git checkout main
git pull origin main
```

## Requisitos

- Git.
- Docker Desktop.
- JDK 21.
- Python 3.12 (para los scripts de `scripts/`).
- Android Studio y Android SDK.

## Configuración local

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

```powershell
docker compose up -d --wait postgres
docker compose ps
```

El servicio debe aparecer `healthy`.

## Probar la app como usuario

1. Levantar PostgreSQL.
2. Iniciar el backend de integración local (terminal abierta):

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\iniciar_backend_integracion_local.ps1 -VersionPrivacidad '1.0'
```

   Puerto **8081**. Health: `Invoke-RestMethod http://localhost:8081/actuator/health` → `status UP`.

3. En Android Studio abrir `android`, emulador, run debug (`http://10.0.2.2:8081/`).

4. Recorrido: crear cuenta, login, registrar vehículo, detalle, actualizar km, mantenimientos, **programar alerta (RF22)** y **Mis alertas (RF23)**, cerrar sesión.

Más detalle: [Integración Elsa RF50](docs/inicio/INTEGRACION_ELSA_RF50.md), [Probar RF08](docs/vehiculos/PROBAR_RF08_EN_APP.md), [Guía RF22/RF23](docs/vehiculos/GUIA_RF22_RF23.md).

## Iniciar solo el backend (puerto 8080)

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Flyway aplicará migraciones pendientes (hasta V9).

## Pruebas

```powershell
cd backend
.\mvnw.cmd test
```

Pruebas con PostgreSQL desechable (Docker + Python), desde la raíz:

```powershell
python scripts/test_mvp_db.py
python scripts/test_rf08_api.py --incluir-rf09 --incluir-sesion
python scripts/test_rf01.py
python scripts/test_rf02.py
python scripts/test_rf06.py
```

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
  `PLAN_FREE_NO_DISPONIBLE` si no hay una suscripción Free vigente.

## Trabajo compartido

- Crear una rama por tarea.
- Integrar mediante pull request revisado.
- Coordinar el número de cada migración nueva.
- No modificar una migración que ya fue aplicada.
- No subir contraseñas, tokens, registros de ejecución ni respaldos de la base de datos.
