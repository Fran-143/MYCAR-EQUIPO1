# Adaptaciones y correcciones técnicas

Se conserva el alcance aprobado de los once RF. Las fuentes congeladas no se
editan; el diccionario original está incorporado con su hash. Ningún cambio se
aplicó a la BD del equipo.

| Cambio | Motivo y evidencia | Implementación |
|---|---|---|
| Separar V2/V3/V4 y conservar V1 | El proyecto ya tiene V1 aplicada y Flyway en db/mvp. | Tablas, catálogos y funciones en migraciones nuevas. Sin CREATE DATABASE ni segunda creación de esquema. |
| Preflight admite flyway_schema_history | La comprobación candidata rechazaba cualquier tabla, incluida la creada por Flyway. | V2 excluye solo esa tabla técnica y detecta además vistas/relaciones de negocio. La prueba de esquema ocupado conserva la fila preexistente. |
| Quitar BEGIN/COMMIT de migraciones | Flyway controla su transacción. El instalador todo-en-uno no se ejecutó. | No se quitaron los BEGIN internos de PL/pgSQL; únicamente las transacciones externas. |
| Crear funciones una sola vez | Un CREATE OR REPLACE podría reemplazar una función ajena preexistente. | V4 usa CREATE FUNCTION. Cualquier conflicto inesperado falla, sin reemplazar ni borrar. |
| Diagnóstico cuenta solo 21 tablas de negocio | El smoke candidato contaba también la tabla de Flyway. | Se excluye history y se mantiene el diagnóstico en src/test/resources. |
| Corregir edad 18–85 | El smoke ampliado rechazaba la expectativa: la comparación de intervalos admitía a un usuario a un día de cumplir 18; PostgreSQL compara meses de intervalos con equivalencias de duración. | tr_usuario_edad usa EXTRACT(YEAR FROM age(fechaLocal, nacimiento)), es decir años cumplidos; límites 18/85 y rechazo 86 probados. |
| Retirar límite 1886 | Diccionario y reglas de Francesca dejan pendiente un mínimo especial; el SQL candidato lo inventaba. | Año entero positivo y trigger de año no futuro. Se prueba que 1800 no es rechazado por una regla inexistente. |
| Revalidar tipo ACTIVO | Los triggers candidatos solo buscaban el código, aunque el diccionario exige tipo activo al registrar. | Mantenimiento y alerta verifican ACTIVO en INSERT, además de las FK. No se invalidan registros históricos por desactivar el catálogo después. |
| Fecha base de RF22 vigente | La candidata solo comprobaba objetivo futuro; una base del día anterior podía pasar. | Al insertar, tiempo/ambos exige la fecha local actual. El backend debe reconstruir resumen y pedir reconfirmación al cambiar las bases. |
| Normalización OTRO coherente | lower(btrim()) no cumple NFKC/full casefold/colapso de blancos y podía rechazar una referencia legítima o encontrar otro último servicio. | Helper Unicode 15.0.0 fijo, sin extensión ni tablas adicionales; se usa en las consultas de referencia. Texto visible intacto. Firma SHA-256 sigue siendo responsabilidad del backend. |
| Reinicio del contador de login sin borrar evidencia | Las reglas de Elsa reinician tras login correcto y al expirar bloqueo; la función candidata no registraba esa frontera. | Columna técnica nullable usuario.login_fallos_desde_utc, función reiniciar_fallos_login y filtro de la ventana móvil. El historial de intentos no se elimina. El backend llama al helper tras verificar password. |
| Evitar interbloqueo al reservar operación antes del cupo | La carrera real produjo SQLSTATE 40P01: dos INSERT de operación adquirían KEY SHARE en usuario y ambos intentaban convertir a FOR UPDATE. | El contrato y la prueba usan FOR NO KEY UPDATE en usuario; los helpers SQL de login también usan ese bloqueo para cambios no clave. No cambia el orden idempotencia → cupo ni el límite Free. |
| Configuración externa para pruebas aisladas | El import literal de ../.env impedía arrancar la prueba Spring sin leer o crear credenciales locales en el checkout. | MYCAR_ENV_FILE parametriza el import, manteniendo ../.env como valor por defecto. Pruebas usan fuente opcional aislada y datasource por entorno. Sin secretos versionados. |

## Reglas que no se han cambiado

- Free es el único plan operativo, con un vehículo total incluyendo inactivos.
- Un reintento confirmado se resuelve antes de volver a evaluar el cupo.
- RF01 inicializa desde Spring en una transacción; no se añadió trigger de alta.
- Una sesión VIGENTE por usuario/instalación, sin revocar otras instalaciones.
- RF14 no sincroniza alertas ni habilita corrección RF16.
- RF17 conserva km del servicio, permite repuestos opcionales y no crea alertas.
- RF22 produce alerta manual e historial inicial, sin aviso interno.
- RF23 es comando separado del GET, usa umbral 10 % exacto, evidencia VENCIDA y
  30 días calendario. No se inventan transiciones intermedias ni procesos autónomos.

## Límite de la garantía

Las pruebas verifican la persistencia y los protocolos SQL sobre PostgreSQL.
El patrón Free/versiones/idempotencia del test es una simulación del futuro
servicio transaccional, no una API ya implementada. La BD por sí sola no puede
validar una contraseña ni acreditar que un idUsuario procede de una sesión HTTP.
