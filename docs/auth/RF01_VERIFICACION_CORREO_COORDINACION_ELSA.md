# RF01 — Verificación de correo: coordinación Francesca → Elsa

## Veredicto

**Aprobado** el modelo de cuatro tablas + `usuario.correo_verificado_utc` nullable.

| Pieza | Decisión |
|-------|----------|
| `control_verificacion` | OK — límites/bloqueos que **no** se reinician al pedir otro código, cambiar correo o abrir otro proceso |
| `operacion_verificacion` | OK — proceso **antes** de existir `usuario`; no guarda formulario ni contraseña |
| `emision_codigo` | OK — código **hasheado**, destinatario, vigencia, consumo, estado de envío |
| `intento_verificacion` | OK — idempotencia si el celular pierde la respuesta |
| `correo_verificado_utc` | OK — NULL; **sin backfill**; cuentas actuales siguen usables en RF02 |

No mezclar con `operacion_idempotente` (exige `id_usuario`; RF08/RF14/RF22).

## Migración obligatoria

- En el árbol local **V7** es placa; **V8** Nayu (`V8__rf01_verificacion_correo.sql`) se **conserva**.
- Elsa/Francesca: **`V10__rf01_verificacion_aislada.sql`** (mismo cuerpo que el ZIP V7, renumerado; reemplaza tablas RF01 de V8).
- **No editar** V1–V9. No activar `db/rf01-verificacion` en paralelo (duplicaría DDL).

Dos lógicas distintas en archivos distintos: V8 Nayu (`proposito`) y V10 Elsa (`finalidad`/BYTEA). No eliminar V8.

Prueba de esquema (BD desechable): `V8VerificacionCorreoSchemaTest` con `-Dmycar.db.tests=true` y `MYCAR_TEST_JDBC_URL` → `mycar_mvp_test_*`.

## Reutilización

Tras V10 queda el juego Elsa (`finalidad='REGISTRO'`). RF03 / RF53 / RF07 exigirán migración posterior; **no** crear otro juego de tablas por RF.

## Qué sigue siendo tuyo (Elsa)

- Endpoints emitir / confirmar código / completar registro.
- Android del flujo OTP.
- Proveedor de correo.
- Alinear Excel/EA de RF01 al OTP.
- Tests en BD temporal (como Codex).

## Qué no hacer aún

- Exigir `correo_verificado_utc IS NOT NULL` en login (rompe abuelos).
- Aplicar V8 a la BD personal compartida del equipo sin avisar.
- Mezclar este PR con RF13/planes.

## Cuentas existentes

`correo_verificado_utc IS NULL` = registrado antes del OTP; puede iniciar sesión. Solo altas nuevas tras el flujo OTP deben setear `correo_verificado_utc = clock_timestamp()`.

## Revisión de restricciones (Francesca)

- Códigos: solo `codigo_hash` / `autorizacion_hash` hex 64; sin plaintext.
- `intento_verificacion.id_intento` = clave de idempotencia del cliente.
- `control_verificacion` se clave por `(proposito, clave_control)` — no por correo — para que cambiar destinatario no reinicie límites.
- Respuestas HTTP deben seguir siendo genéricas (no filtrar si el correo ya existe); eso es regla de servicio, no de CHECK.
- FKs: emisión → operación → control; intento → operación (y emisión opcional); `id_usuario_resultado` solo en `COMPLETADA`.
