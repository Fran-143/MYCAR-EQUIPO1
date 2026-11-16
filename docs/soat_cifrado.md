# SOAT — cifrado de campos sensibles (fase 1)

## Variables de entorno

| Variable | Descripción |
|----------|-------------|
| `MYCAR_DATOS_SENSIBLES_ENABLED` | `true` para exigir cifrado en altas/actualizaciones SOAT |
| `MYCAR_DATOS_SENSIBLES_KEY_ID` | Identificador de versión de clave (p. ej. `v1`) |
| `MYCAR_DATOS_SENSIBLES_AES_KEY` | Clave AES-256 en Base64 (32 bytes) |
| `MYCAR_DATOS_SENSIBLES_BLIND_KEY` | Clave HMAC-256 en Base64 (32 bytes), distinta de la AES |

Documentos SOAT se suben por el backend (`POST …/soat/documentos/upload`); el proxy cifra bytes antes de guardar en storage.

## Operación

- Con `enabled=false`, las consultas pueden devolver texto legado en BD; las mutaciones SOAT fallan cerrado (`CIFRADO_NO_CONFIGURADO`).
- Al arrancar con cifrado activo, `SoatCifradoBackfill` cifra filas con `crypto_key_id` nulo.

## Limitaciones

Quien tenga las claves AES y acceso a la base de datos o al bucket puede descifrar póliza, contratante y documentos. Las claves no deben vivir en el repositorio.

## Migración

1. Respaldar la base de datos.
2. Desplegar el backend con Flyway `V28__soat_cifrado_campos.sql`.
3. Configurar las cuatro variables y reiniciar para ejecutar el backfill.