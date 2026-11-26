# Ensayo V24 en Postgres Docker local (NO Neon).
# Uso: desde la raiz del repo, con contenedor mycarequipo1-postgres-1 saludable.
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$sqlV24 = Join-Path $raiz "backend\src\main\resources\db\mvp\V24__dispositivo_push_outbox.sql"
if (!(Test-Path $sqlV24)) { throw "Falta V24: $sqlV24" }

$container = "mycarequipo1-postgres-1"
$db = if ($env:POSTGRES_DB) { $env:POSTGRES_DB } else { "mycar_mvp" }
$user = if ($env:POSTGRES_USER) { $env:POSTGRES_USER } else { "mycar" }

Write-Host "=== Pre-chequeo deps en Docker ($db) ==="
docker exec $container psql -U $user -d $db -v ON_ERROR_STOP=1 -c @"
SELECT version, description, success
FROM mycar.flyway_schema_history
WHERE version IS NOT NULL
ORDER BY installed_rank;
"@

docker exec $container psql -U $user -d $db -v ON_ERROR_STOP=1 -c @"
SELECT
  to_regclass('mycar.usuario') AS usuario,
  to_regclass('mycar.alerta') AS alerta,
  to_regclass('mycar.notificacion') AS notificacion,
  to_regclass('mycar.dispositivo_push') AS dispositivo_push,
  to_regclass('mycar.outbox_push') AS outbox_push;
"@

$ya = docker exec $container psql -U $user -d $db -tAc "SELECT to_regclass('mycar.dispositivo_push') IS NOT NULL;"
if ($ya.Trim() -eq "t") {
    Write-Host "V24 ya aplicada en Docker local; re-ensayo DROP + CREATE en transaccion de prueba."
    docker exec $container psql -U $user -d $db -v ON_ERROR_STOP=1 -c @"
BEGIN;
DROP TABLE IF EXISTS mycar.outbox_push;
DROP TABLE IF EXISTS mycar.dispositivo_push;
COMMIT;
"@
}

Write-Host "=== Aplicando V24 en Docker (ensayo) ==="
Get-Content -LiteralPath $sqlV24 -Raw | docker exec -i $container psql -U $user -d $db -v ON_ERROR_STOP=1

Write-Host "=== Verificacion post-V24 ==="
docker exec $container psql -U $user -d $db -v ON_ERROR_STOP=1 -c @"
SELECT c.relname, c.relkind
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'mycar' AND c.relname IN ('dispositivo_push', 'outbox_push')
ORDER BY 1;

SELECT conname, confrelid::regclass AS ref
FROM pg_constraint
WHERE conrelid IN ('mycar.dispositivo_push'::regclass, 'mycar.outbox_push'::regclass)
  AND contype = 'f'
ORDER BY 1;

INSERT INTO mycar.dispositivo_push (id_usuario, id_instalacion, proveedor, token)
SELECT u.id_usuario, '643bf983-8f74-41bb-9488-1fbaaf49065b'::uuid, 'FCM', 'ensayo-token-local'
FROM mycar.usuario u
ORDER BY u.id_usuario
LIMIT 1;

DELETE FROM mycar.dispositivo_push WHERE token = 'ensayo-token-local';
SELECT 'ensayo_ok' AS resultado;
"@

Write-Host "Ensayo Docker V24 OK."
