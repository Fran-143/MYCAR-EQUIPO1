# Reaplica adb reverse cada 2s para que el APK en 127.0.0.1:8081 no se quede sin ruta.
$ErrorActionPreference = 'SilentlyContinue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path $adb)) { Write-Error "adb no encontrado"; exit 1 }
Write-Host "Watchdog adb reverse 8081 (cada 2s). Deja este proceso abierto."
while ($true) {
    $ok = & $adb devices 2>$null | Select-String "`tdevice$"
    if ($ok) {
        $list = & $adb reverse --list 2>$null
        if ("$list" -notmatch 'tcp:8081') {
            & $adb reverse tcp:8081 tcp:8081 | Out-Null
            Write-Host "$(Get-Date -Format HH:mm:ss) reverse restaurado"
        } else {
            & $adb reverse tcp:8081 tcp:8081 | Out-Null
        }
    }
    Start-Sleep -Seconds 2
}
