# Ensayo aislado: frecuencias push hibridas. NO toca Neon ni FCM real.
$ErrorActionPreference = "Stop"
$raiz = Split-Path -Parent $PSScriptRoot
$report = Join-Path $env:TEMP "mycar_rf23_recordatorios.txt"
Remove-Item $report -Force -ErrorAction SilentlyContinue
function Log([string]$s) { Add-Content -LiteralPath $report -Value $s; Write-Host $s }

$pgName = "mycar-rf23-rec"
$pgPort = 55433
$pgPass = "rf23_rec_local_only"
$cronSecret = "rf23-rec-cron-secret-local"
$jwtSecret = [Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
$baseUrl = "http://127.0.0.1:18081"
$jdbc = "jdbc:postgresql://127.0.0.1:${pgPort}/mycar_rf23_rec"

Log "=== Recordatorios push aislados $(Get-Date -Format o) ==="

cmd /c "docker rm -f $pgName >nul 2>&1"
$run = cmd /c "docker run -d --name $pgName -e POSTGRES_USER=mycar -e POSTGRES_PASSWORD=$pgPass -e POSTGRES_DB=mycar_rf23_rec -p ${pgPort}:5432 postgres:16-alpine"
if (-not $run) { throw "docker run fallo" }

$ready = $false
for ($i = 0; $i -lt 90; $i++) {
  try {
    $probe = cmd /c "docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc `"SELECT 1`" 2>nul"
    if (($probe | Out-String).Trim() -eq "1") { $ready = $true; break }
  } catch {}
  Start-Sleep 1
}
if (-not $ready) { throw "Postgres no listo" }
Log "postgres_ready"

$migDir = Join-Path $raiz "backend\src\main\resources\db\mvp"
Get-ChildItem $migDir -Filter "V*.sql" | Sort-Object {
  if ($_.Name -match '^V(\d+)__') { [int]$Matches[1] } else { 9999 }
} | ForEach-Object {
  Log "migrate $($_.Name)"
  Get-Content -LiteralPath $_.FullName -Raw | docker exec -i $pgName psql -U mycar -d mycar_rf23_rec -v ON_ERROR_STOP=1 | Out-Null
  if ($LASTEXITCODE -ne 0) { throw "Fallo $($_.Name)" }
}
Log "migrations_ok_incl_V25"

$seed = @'
SET search_path TO mycar, public;
INSERT INTO mycar.usuario(correo,password_hash,nombre,apellido,fecha_nacimiento,consentimiento_privacidad_fecha_utc,consentimiento_privacidad_version,estado_cuenta)
VALUES ('rec@example.invalid', repeat('x',60), 'Rec', 'User', DATE '1990-01-15', clock_timestamp(), '1.0', 'ACTIVA');
INSERT INTO mycar.preferencia_notificacion(id_usuario, mantenimiento_on, permiso_dispositivo)
SELECT id_usuario, TRUE, 'CONCEDIDO' FROM mycar.usuario WHERE correo='rec@example.invalid';
INSERT INTO mycar.dispositivo_push(id_usuario, id_instalacion, proveedor, token, activo)
SELECT id_usuario, '22222222-2222-4222-8222-222222222222', 'FCM', 'token-fake-rec', TRUE
FROM mycar.usuario WHERE correo='rec@example.invalid';
INSERT INTO mycar.vehiculo(id_usuario,placa,marca,modelo,uso_vehiculo_codigo,anio_fabricacion,color,id_tipo_motor,id_combustible)
SELECT u.id_usuario,'R1C001','M','M','PARTICULAR',2020,'Azul',
  (SELECT id_tipo_motor FROM mycar.tipo_motor WHERE codigo='COMBUSTION'),
  (SELECT id_combustible FROM mycar.combustible WHERE codigo='GASOLINA')
FROM mycar.usuario u WHERE u.correo='rec@example.invalid';
INSERT INTO mycar.operacion_idempotente(id_operacion,id_usuario,tipo_operacion,hash_solicitud)
SELECT 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', id_usuario, 'REGISTRAR_VEHICULO', repeat('2',64)
FROM mycar.usuario WHERE correo='rec@example.invalid';
INSERT INTO mycar.lectura_kilometraje(id_vehiculo, valor_km, origen, id_operacion)
SELECT v.id_vehiculo, 20000, 'INICIAL', 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb'
FROM mycar.vehiculo v JOIN mycar.usuario u ON u.id_usuario=v.id_usuario WHERE u.correo='rec@example.invalid';
UPDATE mycar.operacion_idempotente SET estado='CONFIRMADA',
  respuesta_json=jsonb_build_object('ok', true)
WHERE id_operacion='bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';

BEGIN;
SET LOCAL session_replication_role = replica;

-- PROXIMA
INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,origen_calculo,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia)
SELECT v.id_vehiculo, tm.id_tipo_mantenimiento, 'POR_TIEMPO', 'DESDE_HOY', 'MESES', 6,
       ((now() AT TIME ZONE 'America/Lima')::date - 150),
       ((now() AT TIME ZONE 'America/Lima')::date + 15),
       'PROXIMA', repeat('a',64)
FROM mycar.vehiculo v
JOIN mycar.usuario u ON u.id_usuario=v.id_usuario
JOIN mycar.tipo_mantenimiento tm ON tm.codigo='CAMBIO_ACEITE'
WHERE u.correo='rec@example.invalid';

-- PENDIENTE bootstrap
INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,origen_calculo,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia)
SELECT v.id_vehiculo, tm.id_tipo_mantenimiento, 'POR_TIEMPO', 'DESDE_HOY', 'MESES', 6,
       ((now() AT TIME ZONE 'America/Lima')::date - 180),
       (now() AT TIME ZONE 'America/Lima')::date,
       'PENDIENTE', repeat('b',64)
FROM mycar.vehiculo v
JOIN mycar.usuario u ON u.id_usuario=v.id_usuario
JOIN mycar.tipo_mantenimiento tm ON tm.codigo='MANTENIMIENTO_GENERAL'
WHERE u.correo='rec@example.invalid';

-- VENCIDA A
INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,origen_calculo,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia,fecha_vencida_verificable)
SELECT v.id_vehiculo, tm.id_tipo_mantenimiento, 'POR_TIEMPO', 'DESDE_HOY', 'MESES', 6,
       ((now() AT TIME ZONE 'America/Lima')::date - 200),
       ((now() AT TIME ZONE 'America/Lima')::date - 20),
       'VENCIDA', repeat('c',64),
       ((now() AT TIME ZONE 'America/Lima')::date - 5)
FROM mycar.vehiculo v
JOIN mycar.usuario u ON u.id_usuario=v.id_usuario
JOIN mycar.tipo_mantenimiento tm ON tm.codigo='CAMBIO_FILTROS'
WHERE u.correo='rec@example.invalid';

-- VENCIDA B (resumen)
INSERT INTO mycar.alerta(id_vehiculo,id_tipo_mantenimiento,criterio,origen_calculo,unidad_tiempo,cantidad_tiempo,fecha_base,fecha_objetivo,estado,firma_equivalencia,fecha_vencida_verificable)
SELECT v.id_vehiculo, tm.id_tipo_mantenimiento, 'POR_TIEMPO', 'DESDE_HOY', 'MESES', 6,
       ((now() AT TIME ZONE 'America/Lima')::date - 210),
       ((now() AT TIME ZONE 'America/Lima')::date - 25),
       'VENCIDA', repeat('d',64),
       ((now() AT TIME ZONE 'America/Lima')::date - 10)
FROM mycar.vehiculo v
JOIN mycar.usuario u ON u.id_usuario=v.id_usuario
JOIN mycar.tipo_mantenimiento tm ON tm.codigo='REVISION_FRENOS'
WHERE u.correo='rec@example.invalid';

INSERT INTO mycar.historial_alerta(id_alerta,estado_nuevo,motivo,version_destino)
SELECT id_alerta, estado, 'INICIAL_RF22', 0 FROM mycar.alerta;

-- Fila legacy PENDIENTE (simula outbox pre-V25 ya migrada a LEGACY:*)
INSERT INTO mycar.outbox_push(
  id_usuario, id_alerta, categoria, titulo, cuerpo, destino, estado, intentos, clave_idempotencia)
SELECT u.id_usuario, NULL, 'MANTENIMIENTO', 'legacy', 'legacy-pendiente', 'BANDEJA',
       'PENDIENTE', 0, 'LEGACY:ensayo-rec'
FROM mycar.usuario u WHERE u.correo='rec@example.invalid';

COMMIT;
SELECT count(*) AS alertas FROM mycar.alerta;
SELECT count(*) AS outbox_legacy FROM mycar.outbox_push WHERE clave_idempotencia LIKE 'LEGACY:%';
'@

$seed | docker exec -i $pgName psql -U mycar -d mycar_rf23_rec -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw "seed fallo" }
Log "seed_ok"

$logOut = Join-Path $env:TEMP "mycar_rf23_rec_backend.out.log"
$logErr = Join-Path $env:TEMP "mycar_rf23_rec_backend.err.log"
Remove-Item $logOut,$logErr -Force -ErrorAction SilentlyContinue
$backendDir = Join-Path $raiz "backend"
$argLine = "-q -Dmaven.test.skip=true spring-boot:run `"-Dspring-boot.run.arguments=--server.port=18081 --mycar.rf23.evaluacion-diaria-habilitada=false --mycar.fcm.habilitado=false --mycar.fcm.worker-habilitado=false --mycar.fcm.simular-envio=true --mycar.fcm.forzar-ventana=true --mycar.verificacion.mail.modo=desarrollo --mycar.cron.secret=$cronSecret --spring.flyway.enabled=false --spring.datasource.url=$jdbc --spring.datasource.username=mycar --spring.datasource.password=$pgPass --mycar.auth.jwt-secret-base64=$jwtSecret --mycar.auth.access-ttl=PT2H --mycar.auth.session-ttl=P1D --spring.mail.host=127.0.0.1 --spring.mail.port=1025`""
$proc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c","cd /d `"$backendDir`" && mvn.cmd $argLine" -PassThru -RedirectStandardOutput $logOut -RedirectStandardError $logErr -WindowStyle Hidden
Log "backend_pid=$($proc.Id)"

$up = $false
for ($i = 0; $i -lt 120; $i++) {
  try {
    $h = Invoke-RestMethod "$baseUrl/actuator/health" -TimeoutSec 2
    if ($h.status -eq "UP") { $up = $true; break }
  } catch {}
  if ($proc.HasExited) { break }
  Start-Sleep 2
}
if (-not $up) {
  Get-Content $logOut,$logErr -Tail 100 -ErrorAction SilentlyContinue | ForEach-Object { Log $_ }
  throw "backend no arranco"
}
Log "backend_up"

function PostCron([string]$path, [string]$body = "{}") {
  return Invoke-RestMethod -Method POST -Uri "$baseUrl$path" -Headers @{
    "X-MyCar-Cron-Secret" = $cronSecret
    "Content-Type" = "application/json"
  } -Body $body -TimeoutSec 90
}

$eval1 = PostCron "/api/v1/interno/alertas/evaluar-programadas" '{"modo":"COMPLETO"}'
Log "eval1=$($eval1 | ConvertTo-Json -Compress)"
$outAfterEval = (docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc "SELECT count(*) FROM mycar.outbox_push WHERE clave_idempotencia NOT LIKE 'LEGACY:%'").Trim()
Log "outbox_nuevos_after_eval=$outAfterEval"
$legacyPend = (docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc "SELECT count(*) FROM mycar.outbox_push WHERE clave_idempotencia='LEGACY:ensayo-rec' AND estado='PENDIENTE'").Trim()
Log "legacy_pendiente_antes=$legacyPend"

$push1 = PostCron "/api/v1/interno/push/despachar-recordatorios" "{}"
Log "push1=$($push1 | ConvertTo-Json -Compress)"

$stats1 = (docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc @"
SELECT
  (SELECT count(*) FROM mycar.outbox_push)::text || '|' ||
  (SELECT count(*) FROM mycar.outbox_push WHERE estado='ENVIADO')::text || '|' ||
  (SELECT count(*) FROM mycar.alerta_push_control WHERE fecha_ultimo_envio_lima = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date)::text || '|' ||
  (SELECT count(*) FROM mycar.outbox_push WHERE clave_idempotencia LIKE '%:VENCIDAS:%')::text || '|' ||
  (SELECT count(*) FROM mycar.outbox_push_alerta)::text || '|' ||
  (SELECT estado FROM mycar.outbox_push WHERE clave_idempotencia='LEGACY:ensayo-rec')
"@).Trim()
Log "stats1_outbox|enviados|ctrl_hoy|resumen|vinc|legacy_estado=$stats1"

$push2 = PostCron "/api/v1/interno/push/despachar-recordatorios" "{}"
Log "push2=$($push2 | ConvertTo-Json -Compress)"

# Simula dia siguiente: rebobina control y libera claves del dia (en prod la fecha Lima cambia la clave).
docker exec $pgName psql -U mycar -d mycar_rf23_rec -v ON_ERROR_STOP=1 -c @"
UPDATE mycar.alerta_push_control c
SET fecha_ultimo_envio_lima = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date - 3
FROM mycar.alerta a WHERE a.id_alerta=c.id_alerta AND a.estado='PENDIENTE';
UPDATE mycar.alerta_push_control c
SET fecha_ultimo_envio_lima = (CURRENT_TIMESTAMP AT TIME ZONE 'America/Lima')::date - 1
FROM mycar.alerta a WHERE a.id_alerta=c.id_alerta AND a.estado='VENCIDA';
DELETE FROM mycar.outbox_push
 WHERE clave_idempotencia LIKE '%:VENCIDAS:%'
    OR clave_idempotencia LIKE '%:RECORDATORIO';
"@ | Out-Null

$push3 = PostCron "/api/v1/interno/push/despachar-recordatorios" "{}"
Log "push3=$($push3 | ConvertTo-Json -Compress)"
$outTotal = (docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc "SELECT count(*) FROM mycar.outbox_push").Trim()
Log "outbox_total=$outTotal"
$claves3 = (docker exec $pgName psql -U mycar -d mycar_rf23_rec -tAc "SELECT string_agg(clave_idempotencia, ',') FROM mycar.outbox_push").Trim()
Log "claves=$claves3"

$pass = $true
function Assert([bool]$cond, [string]$msg) {
  if ($cond) { Log "PASS $msg" } else { Log "FAIL $msg"; $script:pass = $false }
}

Assert ([int]$outAfterEval -eq 0) "eval_no_encola_push"
Assert ([int]$legacyPend -eq 1) "legacy_pendiente_conservada"
Assert ($push1.encolados -ge 3) "bootstrap_encola_ge3"
Assert ($push1.enviados -ge 3) "bootstrap_simula_envio"
Assert ($push2.encolados -eq 0) "mismo_dia_sin_duplicados"
Assert ($push3.encolados -ge 2) "pendiente_3d_y_vencida_diaria"
$parts = $stats1 -split '\|'
Assert ([int]$parts[4] -ge 2) "resumen_vincula_alertas"
Assert ($parts[5] -eq "ENVIADO") "legacy_drenada_a_enviado"
# Control solo tras ENVIADO: al menos tantas filas de control como alertas del resumen+individuales
Assert ([int]$parts[2] -ge 3) "control_tras_envio"

try {
  $w = Invoke-RestMethod "https://mycar-alertas-cron.mycargrupo1.workers.dev/" -TimeoutSec 20
  Assert ($w.evalModoVars -eq "SIMULAR") "worker_prod_SIMULAR"
} catch { Log "WARN worker_check $_" }

try { Stop-Process -Id $proc.Id -Force -ErrorAction SilentlyContinue } catch {}
Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue |
  Where-Object { $_.CommandLine -match '18081' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
cmd /c "docker rm -f $pgName >nul 2>&1"

if ($pass) { Log "RESULTADO=PASS"; exit 0 } else { Log "RESULTADO=FAIL"; exit 1 }
