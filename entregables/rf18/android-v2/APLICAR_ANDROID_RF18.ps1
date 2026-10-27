param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
. (Join-Path $PSScriptRoot 'RF18_COMUN.ps1')
Preparar-RF18 $Repositorio
Aplicar-RF18 $Repositorio $PSScriptRoot 'android'
Push-Location (Join-Path $Repositorio 'android')
try {
    & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw 'Fallo la comprobacion Android. Conserva el resultado y el respaldo.' }
    $rf18Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (Test-Path -LiteralPath $rf18Adb) {
        $dispositivos = & $rf18Adb devices
        if ($LASTEXITCODE -eq 0 -and ($dispositivos -match '^emulator-5554\s+device\s*$')) {
            & $rf18Adb -s emulator-5554 install -r '.\app\build\outputs\apk\debug\app-debug.apk'
            if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app. No se borraron sus datos.' }
            & $rf18Adb -s emulator-5554 shell am start -S -n 'com.mycar.app/.features.historial.ui.HistorialEntrada'
            if ($LASTEXITCODE -ne 0) { throw 'La app se instalo, pero no se pudo abrir automaticamente.' }
            Write-Host 'En la bienvenida pulsa Historial de mantenimientos.'
        } else {
            Write-Host 'APK compilado. Abre tu emulador y ejecuta ABRIR_ANDROID_RF18.ps1 para instalarlo.'
        }
    } else {
        Write-Host 'APK compilado. Abre el proyecto original en Android Studio para ejecutarlo con tu SDK.'
    }
} finally { Pop-Location }
Write-Host 'La pantalla normal usa datos reales cuando recibe la sesion y los repositorios autenticados.'
Write-Host 'Para ver tarjetas de ejemplo, abre HistorialMantenimientoPreview.kt en modo interactivo.'
Write-Host 'No se hicieron commits ni push.'
