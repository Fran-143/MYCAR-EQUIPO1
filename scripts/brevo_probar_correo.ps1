# Un solo correo de prueba vía RF03 (recuperación) usando el backend Live.
# No imprime el OTP (en prod codigoDesarrollo=false). Verifica llegada en tu bandeja / Brevo.
# Uso: tras activar MYCAR_VERIFICACION_MAIL_MODO=brevo + API key + FROM en Render.
param(
    [string]$BaseUrl = "https://mycar-backend-1dxi.onrender.com",
    [string]$Correo = ""
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

function Read-Input([string]$title, [string]$label, [bool]$secret = $false) {
    $f = New-Object System.Windows.Forms.Form
    $f.Text = $title; $f.Width = 460; $f.Height = 170; $f.StartPosition = "CenterScreen"; $f.TopMost = $true
    $l = New-Object System.Windows.Forms.Label; $l.Text = $label; $l.Left = 12; $l.Top = 16; $l.Width = 420
    $t = New-Object System.Windows.Forms.TextBox; $t.Left = 12; $t.Top = 48; $t.Width = 420
    if ($secret) { $t.UseSystemPasswordChar = $true }
    $ok = New-Object System.Windows.Forms.Button; $ok.Text = "OK"; $ok.Left = 250; $ok.Top = 90; $ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
    $c = New-Object System.Windows.Forms.Button; $c.Text = "Cancelar"; $c.Left = 340; $c.Top = 90; $c.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
    $f.Controls.AddRange(@($l, $t, $ok, $c)); $f.AcceptButton = $ok; $f.CancelButton = $c
    if ($f.ShowDialog() -ne "OK") { throw "Cancelado" }
    return $t.Text.Trim()
}

$base = $BaseUrl.TrimEnd("/")
if ([string]::IsNullOrWhiteSpace($Correo)) {
    $Correo = Read-Input "MyCar Brevo" "Correo de una cuenta ACTIVA en MyCar (recibirá 1 OTP):"
}
if ([string]::IsNullOrWhiteSpace($Correo)) { throw "Correo vacío" }

Write-Host "Health..."
$h = Invoke-RestMethod -Uri "$base/actuator/health" -TimeoutSec 120
Write-Host "health=$($h.status)"

$body = @{ correo = $Correo } | ConvertTo-Json
$sw = [Diagnostics.Stopwatch]::StartNew()
try {
    $r = Invoke-WebRequest -Uri "$base/api/v1/auth/recuperacion/emitir" -Method POST `
        -ContentType "application/json" -Body $body -UseBasicParsing -TimeoutSec 90
    $json = $r.Content | ConvertFrom-Json
    Write-Host ("emitir HTTP={0} ms={1}" -f $r.StatusCode, $sw.ElapsedMilliseconds)
    Write-Host ("resultado={0}" -f $json.resultado)
    if ($json.PSObject.Properties.Name -contains "codigoDesarrollo" -and $json.codigoDesarrollo) {
        Write-Host "WARN: la API devolvió codigoDesarrollo (no debería en Render/brevo)." -ForegroundColor Yellow
    } else {
        Write-Host "OK: sin codigoDesarrollo en la respuesta (correcto en producción)."
    }
    Write-Host ""
    Write-Host "Revisa ahora:" -ForegroundColor Green
    Write-Host "  1) Bandeja de entrada (y spam) de $Correo"
    Write-Host "  2) Brevo → Transactional → Logs / Emails"
    Write-Host "Asunto esperado: MyCar — código de verificación"
} catch {
    $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
    $msg = $_.ErrorDetails.Message
    if (-not $msg -and $_.Exception.Response) {
        $reader = [IO.StreamReader]::new($_.Exception.Response.GetResponseStream())
        $msg = $reader.ReadToEnd()
    }
    Write-Host ("FAIL http={0} body={1}" -f $code, $msg) -ForegroundColor Red
    exit 1
}
