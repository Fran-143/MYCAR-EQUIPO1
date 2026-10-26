# MyCar — Base del MVP

## Estado actual

- Android: Kotlin y Jetpack Compose.
- Backend actual: Java 21 y Spring Boot.
- PostgreSQL 17 mediante Docker.
- Base de datos de desarrollo: mycar_mvp.
- Esquema: mycar.
- Migraciones: Flyway, carpeta backend/src/main/resources/db/mvp.
- Conexión Android → backend configurada.

La base incorpora 21 tablas de negocio/soporte del MVP mediante Flyway.
V1 conserva el esquema inicial; V2 crea las tablas, V3 carga los catálogos
y V4 incorpora funciones y triggers. No ejecutar el instalador SQL manual.

El backend se mantiene en Java 21. Los endpoints y flujos Android de los
11 RF todavía deben implementarse sobre estos contratos de persistencia.

Documentación, correcciones y responsabilidades del backend:
[Base de datos MVP](docs/db/mvp/README.md).

## Requisitos

- Git.
- Docker Desktop.
- JDK 21.
- IntelliJ IDEA.
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

No subir .env a GitHub.
No sobrescribir un .env existente sin revisar sus valores.

## Iniciar PostgreSQL

Con Docker Desktop abierto:

```powershell
docker compose up -d --wait postgres
docker compose ps
```

El servicio debe aparecer healthy.

Si el volumen Docker ya existía, cambiar POSTGRES_DB no crea
automáticamente otra base. Coordinar su preparación antes de continuar.
No eliminar el volumen para resolverlo sin respaldo.

## Iniciar el backend

Desde la raíz del proyecto:

```powershell
cd backend
.\mvnw.cmd spring-boot:run
```

Mantener esa terminal abierta.

Flyway aplicará las migraciones pendientes de db/mvp.
Si se ejecuta desde IntelliJ, usar backend como directorio de trabajo.

En otra terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
```

Resultado esperado: status UP.

## Pruebas de PostgreSQL aisladas

Con Docker Desktop, JDK 21 y Python 3 instalados, desde la raíz:

```powershell
py -3 scripts/test_mvp_db.py
```

En Linux/macOS: `python3 scripts/test_mvp_db.py`.

El ejecutor crea PostgreSQL 17 con credenciales aleatorias y puerto local
temporal; prueba Flyway, integridad, transacciones, concurrencia, Spring Boot
y health. No utiliza .env ni el volumen Compose de desarrollo. Al terminar
retira exclusivamente su propio contenedor y sus datos de prueba.

## Ejecutar Android

1. Abrir android en Android Studio.
2. Sincronizar Gradle.
3. Iniciar un emulador.
4. Ejecutar en PowerShell:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" -e reverse tcp:8080 tcp:8080
```

5. Ejecutar app desde Android Studio.
6. Pulsar Comprobar conexión.

Resultado esperado: Servidor disponible.

Esta configuración corresponde al desarrollo local.
La dirección pública de producción todavía está pendiente.

## Alcance previsto del MVP

- Elsa: RF01, RF02 y RF06.
- Francesca: RF08, RF09, RF10 y RF14.
- Nayu: RF17, RF18, RF22 y RF23.
- Sebastián: soporte de planes e integración.

## Trabajo compartido

- Crear una rama por tarea.
- Integrar mediante pull request revisado.
- Coordinar cambios de configuración y dependencias.
- Coordinar el número de cada migración nueva.
- No modificar una migración que ya fue aplicada.
- No ejecutar los SQL del modelo anterior sobre mycar_mvp.
- No crear tablas manualmente que después se repetirán con Flyway.
- No subir contraseñas, tokens ni respaldos de la BD.
