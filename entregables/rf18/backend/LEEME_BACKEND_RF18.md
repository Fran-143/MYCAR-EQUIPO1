# RF18 — Backend de historial de mantenimiento

Este ZIP es un parche para el proyecto original `C:\Users\PROPIETARIO\Documents\MYCAR-EQUIPO1-git`. No abras otro backend en Descargas. Base: main `e39c0c253511b288286c70f51179d3fa471313e8`, que ya integra RF17 (PR #6) y RF01/RF02/RF06 (PR #8).

## Instalar desde PowerShell

Extrae TODO el ZIP en `Downloads\RF18_BACKEND` y ejecuta:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_BACKEND\APLICAR_BACKEND_RF18.ps1"
```

El instalador abre o crea la rama local `feat/historial-mantenimiento-rf18`. Si la rama aún está en el main anterior y está limpia, intenta avanzar a main por fast-forward. Con cambios pendientes o una integración que necesite resolución, se detiene. Compara todos los archivos con sus hashes conocidos antes de copiar, crea un respaldo en Documents y ejecuta las 18 pruebas unitarias de RF18. No hace commit, push ni crea un PR. No modifica .env ni migraciones ni usa una conexión PostgreSQL para estas pruebas unitarias.

Si el primer acceso a GitHub falla, comprueba la conexión con `Test-NetConnection github.com -Port 443`. No borres tus cambios para repetir la instalación.

## Código incluido

`archivos/backend/src/main/java/backend/historial/` contiene modelos, DAO JDBC, servicio, controlador, errores y política de seguridad. `archivos/backend/src/test/java/backend/historial/` contiene cuatro clases de prueba.

También incluye tres ajustes pequeños a las configuraciones de seguridad de main: `LoginSecurityConfiguration`, `LogoutSecurityConfiguration` y `RegistrationSecurityConfiguration`. Cada cambio agrega `@ConditionalOnWebApplication(type = SERVLET)`. Corrige el fallo de main al cargar HttpSecurity en las pruebas sin contexto web de RF08; las políticas y el comportamiento de los endpoints web se conservan. Son cambios explícitos para revisión del equipo, incluidos en el manifiesto y respaldados por el instalador.

## Lógica implementada

- Solo SELECT sobre las tablas existentes: vehiculo, mantenimiento y tipo_mantenimiento.
- Vehículos propios ACTIVO e INACTIVO. El selector no asigna ni cambia principal.
- Consulta autorizada por sesión y propietario; no recibe idUsuario desde Android.
- Estados REALIZADO y ANULADO. Incluye tipos históricos aunque el catálogo los haya desactivado.
- Orden `fecha_servicio DESC, id_mantenimiento DESC` y contador obtenido de la misma lista/snapshot.
- Kilometraje de `mantenimiento.km_servicio`; no usa odómetro actual ni kilometraje recomendado.
- Costo NULL se conserva como null; cero sigue siendo cero. OTRO conserva servicioOtro.
- Registros heredados inválidos generan un error; no se inventan kilometrajes ni se presenta el error como una lista vacía.
- Transacción de solo lectura con aislamiento REPEATABLE_READ.
- El esquema vigente no tiene taller/id_taller: se omite esa línea y no se crean tablas ni columnas.

## API y sesión real

Rutas implementadas bajo propiedad de activación `mycar.rf18.api-habilitada=true`:

- GET `/api/v1/rf18/contexto`
- GET `/api/v1/rf18/vehiculos/{idVehiculo}/mantenimientos`

Las rutas siguen siendo una propuesta de integración del módulo: su publicación debe revisarse con el equipo. Están ausentes por defecto. Al activarlas, una cadena exclusiva de RF18 reutiliza `SessionBearerFilter` y `SessionAuthentication` de Elsa. Comprueba JWT, sesión persistida, revocación, expiración y cuenta ACTIVA. Solo permite los dos GET; otros métodos se rechazan. Las respuestas correctas incluyen Cache-Control: no-store.

Para ejecutar el servidor, conserva la configuración del equipo para PostgreSQL y las propiedades reales de RF02 (`mycar.auth.jwt-secret-base64`, `mycar.auth.access-ttl`, `mycar.auth.session-ttl`). Consulta `backend/GUIA_INTEGRACION_RF01_RF02_RF06.md` de main. No se incluyen secretos ni TTL productivos inventados. El instalador no activa la API ni cambia esta configuración.

Ejemplo de activación de rutas al iniciar, DESPUÉS de configurar RF02 en el entorno:

```powershell
Set-Location "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git\backend"
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081 --mycar.rf18.api-habilitada=true"
```

## Pruebas PostgreSQL opcionales

Con Docker Desktop abierto, después de instalar el backend:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File "$env:USERPROFILE\Downloads\RF18_BACKEND\PROBAR_POSTGRES_RF18.ps1"
```

Este script crea un contenedor PostgreSQL 17 temporal, con nombre/puerto/base/clave únicos, ejecuta las 30 pruebas RF18 (18 unitarias, 6 DAO/servicio PostgreSQL y 6 integración autenticada), y detiene/retira SOLAMENTE ese contenedor al terminar. Las variables del proceso se restauran. No utiliza .env ni los contenedores habituales del proyecto. Las pruebas generan sus fixtures en esa base desechable; no se deben ejecutar sobre una base real.

Las pruebas autenticadas usan el registro, login, JWT, cierre de sesión y servicio RF17 reales del código, con cuentas y claves SOLO de prueba. Comprueban lectura de lo guardado por RF17, propiedad, token inválido, cierre de sesión, cuenta suspendida y rechazo de escrituras. La capa HTTP se prueba mediante MockMvc, no mediante una app Android autenticada.

## Evidencia y pendientes

Ver `VERIFICACION_RF18.json`. En este entorno pasaron las 30 pruebas RF18. La regresión del backend reportó 476 casos: 384 ejecutados y 92 omitidos por condiciones de prueba, sin fallos. Las 42 pruebas de esquema se ejecutaron aparte en una base nueva; las otras 50 integraciones de Elsa requieren sus bases/variables propias. Se compiló para Java 21 y se ejecutó con JDK 25 y PostgreSQL 17.11.

El cliente Android aún debe recibir el Retrofit autenticado y la clave opaca de cuenta de la navegación compartida. Este paquete no agrega otro login. La integración HTTP autenticada de RF17 y la ruta RF14 siguen siendo trabajo separado; RF18 reutiliza la pantalla RF17 y su contrato de navegación, sin dar por terminados esos pendientes.
