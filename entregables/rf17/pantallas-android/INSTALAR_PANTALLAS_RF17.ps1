& {
    $ErrorActionPreference = "Stop"
    $rf17Android = Join-Path $env:USERPROFILE "Documents\MYCAR-EQUIPO1-git\android"
    $rf17Adb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path (Join-Path $rf17Android "gradlew.bat"))) { throw "No encuentro tu Android original." }
    if (-not (Test-Path $rf17Adb)) { throw "No encuentro adb en la ubicacion habitual." }
    $rf17Archivos = Get-Content -LiteralPath (Join-Path $PSScriptRoot "MANIFIESTO_PANTALLAS.json") -Raw -Encoding UTF8 | ConvertFrom-Json
    function Get-Rf17Hash([string]$rf17Ruta) {
        $rf17Texto = [IO.File]::ReadAllText($rf17Ruta).Replace("`r`n", "`n")
        $rf17Sha = [System.Security.Cryptography.SHA256]::Create()
        try {
            $rf17Bytes = [System.Text.Encoding]::UTF8.GetBytes($rf17Texto)
            return ([BitConverter]::ToString($rf17Sha.ComputeHash($rf17Bytes))).Replace("-", "")
        } finally { $rf17Sha.Dispose() }
    }
    foreach ($rf17Archivo in $rf17Archivos) {
        $rf17Origen = Join-Path (Join-Path $PSScriptRoot "archivos") $rf17Archivo.Ruta
        if (-not (Test-Path $rf17Origen)) { throw "Falta un archivo del paquete. Extrae el ZIP completo." }
        if ((Get-Rf17Hash $rf17Origen) -ne $rf17Archivo.Nuevo) { throw "El paquete tiene un archivo alterado." }
        $rf17Destino = Join-Path $rf17Android $rf17Archivo.Ruta
        if (Test-Path $rf17Destino) {
            $rf17Hash = Get-Rf17Hash $rf17Destino
            if ($rf17Hash -ne $rf17Archivo.Nuevo -and $rf17Hash -notin @($rf17Archivo.Anteriores)) {
                throw "Hay cambios distintos a los revisados en $rf17Destino. No se reemplazo ningun archivo. Enviame este mensaje."
            }
        } elseif ($rf17Archivo.Accion -eq "Reemplazar completo") {
            throw "Falta un archivo original: $rf17Destino. No se reemplazo ningun archivo."
        }
    }
    $rf17Respaldo = Join-Path $env:USERPROFILE ("Documents\RF17_respaldo_pantallas_" + [guid]::NewGuid().ToString("N").Substring(0,8))
    New-Item -ItemType Directory -Path $rf17Respaldo | Out-Null
    foreach ($rf17Archivo in $rf17Archivos) {
        $rf17Origen = Join-Path (Join-Path $PSScriptRoot "archivos") $rf17Archivo.Ruta
        $rf17Destino = Join-Path $rf17Android $rf17Archivo.Ruta
        if (Test-Path $rf17Destino) {
            $rf17Copia = Join-Path $rf17Respaldo $rf17Archivo.Ruta
            New-Item -ItemType Directory -Path (Split-Path $rf17Copia -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $rf17Destino -Destination $rf17Copia
        }
        New-Item -ItemType Directory -Path (Split-Path $rf17Destino -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $rf17Origen -Destination $rf17Destino -Force
    }
    Write-Host "Formulario RF17 conectado a la app normal. Respaldo: $rf17Respaldo"
    Set-Location $rf17Android
    .\gradlew.bat :app:assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Fallo la compilacion. Enviame la salida." }
    & $rf17Adb -s emulator-5554 install -r ".\app\build\outputs\apk\debug\app-debug.apk"
    if ($LASTEXITCODE -ne 0) { throw "Fallo la instalacion. Enviame la salida." }
    & $rf17Adb -s emulator-5554 shell am start -S -n "com.mycar.app/.MainActivity"
    if ($LASTEXITCODE -ne 0) { throw "No se pudo abrir la app en el emulador." }
    Write-Host "En la bienvenida pulsa Registrar mantenimiento."
    Write-Host "El formulario aparece sin datos de ejemplo. Sus controles requieren la sesion real pendiente de integrar."
}
