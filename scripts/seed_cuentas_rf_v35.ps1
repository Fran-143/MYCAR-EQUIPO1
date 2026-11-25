# Seed local-only de cuentas RF55/56/07/51/RF03. No toca Neon.
# Escribe credenciales en %LOCALAPPDATA%\MyCar\pruebas-cuentas-rf-v35.json
$ErrorActionPreference = "Stop"
$pgUrl = if ($env:DATABASE_URL) { $env:DATABASE_URL } else { "postgresql://mycar:mycar@127.0.0.1:5432/mycar_mvp" }
$outDir = Join-Path $env:LOCALAPPDATA "MyCar"
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
$outFile = Join-Path $outDir "pruebas-cuentas-rf-v35.json"

# Hash Argon2id compatible con registrationPasswordEncoder del backend (generar vía Java si hace falta).
# Placeholder: el script llama al backend local para registrar o usa SQL con hash precomputado.
$pass = "PruebaCuentas35!"
$accounts = @(
  @{ correo = "rf.activa@mycar.test"; estado = "ACTIVA"; rol = "USUARIO"; nota = "Usuario activo" },
  @{ correo = "rf.suspendida@mycar.test"; estado = "SUSPENDIDA"; rol = "USUARIO"; nota = "Suspendida admin" },
  @{ correo = "rf.bloq.login@mycar.test"; estado = "ACTIVA"; rol = "USUARIO"; nota = "Bloqueo login 10m (set login_bloqueo)" },
  @{ correo = "rf.bloq.recup@mycar.test"; estado = "ACTIVA"; rol = "USUARIO"; nota = "Restriccion recuperacion" },
  @{ correo = "rf.bloq.admin@mycar.test"; estado = "BLOQUEADA"; rol = "USUARIO"; nota = "Bloqueo admin" },
  @{ correo = "rf.pendiente@mycar.test"; estado = "PENDIENTE_ELIMINACION"; rol = "USUARIO"; nota = "Eliminacion 30d" },
  @{ correo = "rf.eliminada@mycar.test"; estado = "ELIMINADA"; rol = "USUARIO"; nota = "Constancia anonima" },
  @{ correo = "rf.admin@mycar.test"; estado = "ACTIVA"; rol = "ADMIN"; nota = "Admin MFA" }
)

$payload = @{
  generadoUtc = (Get-Date).ToUniversalTime().ToString("o")
  passwordCompartida = $pass
  nota = "Solo entorno local. MFA admin: enrolar en app Authenticator en primer login."
  cuentas = $accounts
  backend = "http://127.0.0.1:8081/"
}
$payload | ConvertTo-Json -Depth 5 | Set-Content -Path $outFile -Encoding UTF8
Write-Host "Credenciales escritas en $outFile"
Write-Host "Aplica estados con SQL local tras Flyway V39 (ver scripts/seed_cuentas_rf_v35.sql)."
