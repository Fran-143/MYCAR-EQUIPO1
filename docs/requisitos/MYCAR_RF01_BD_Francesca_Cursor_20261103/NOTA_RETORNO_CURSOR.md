# Nota de retorno Cursor — persistencia RF01 (paquete Francesca 2026-11-03)

Fecha de revisión: 2026-11-04 (America/Bogota).  
Base Git revisada: rama `main`, commit `84641c7` (solo V1–V6 en `origin/main`).

**Git y PostgreSQL son estados distintos.**

---

## Corrección de coordinación (Nayu + Elsa)

Hay **dos lógicas distintas**; **no se elimina** el archivo de Nayu:

| Versión | Archivo | Lógica |
|---|---|---|
| **V8** | `backend/src/main/resources/db/mvp/V8__rf01_verificacion_correo.sql` | Nayu: `proposito`, BIGINT, hashes hex |
| **V10** | `backend/src/main/resources/db/mvp/V10__rf01_verificacion_aislada.sql` | Elsa/Francesca: ZIP V7 renumerado; UUID/BYTEA, `finalidad` REGISTRO |

V9 sigue siendo RF22 (`origen_calculo`).

Mismas *nombres* de tabla → V10 hace `DROP` de las 4 tablas RF01 de V8 y crea el esquema Elsa. El archivo V8 **permanece** en Git/historial Flyway.

Location activa: solo `classpath:db/mvp`. **No** activar `db/rf01-verificacion`.

### Para la autora (Elsa)

- Archivo local `db/rf01-verificacion/V7__…`: **sustituir** por el canónico `db/mvp/V10__rf01_verificacion_aislada.sql` (mismo cuerpo DDL tras cabecera/DROP); **retirar** la ruta aislada para no duplicar.
- No borrar V8 Nayu del repo.
- `VerificationPostgresTest`: una location `db/mvp`; conteo **10** migraciones (V1–V10) si la cadena incluye placa+Nayu+RF22+Elsa.

### Backend

- Java de referencia Francesca → esquema **V10**.
- V8 Nayu sigue siendo contrato intermedio / historial; OTP productivo Elsa alinea a V10.

### Pruebas

- Desechable: cadena V1–V10 + validate; test `V8VerificacionCorreoSchemaTest.v10DejaEsquemaFrancescaTrasV8Nayu`.
- `mycar_mvp` local: tiene V8 Nayu + V9 aplicados; **V10 aún no**. Aplicar V10 solo con plan explícito (reemplaza tablas RF01). Actualizar Git ≠ aplicar V10.
