$ErrorActionPreference = 'Stop'
function Get-Rf22Hash([string]$Archivo) {
    $texto = [IO.File]::ReadAllText($Archivo).Replace("`r`n","`n").TrimStart([char]0xFEFF)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($texto)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
function Aplicar-Rf22([string]$Repositorio,[string]$Componente,[string]$CarpetaRespaldos = [Environment]::GetFolderPath('MyDocuments')) {
    $repo = (Resolve-Path -LiteralPath $Repositorio).Path
    $gitRaiz = & git -C $repo rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0 -or [IO.Path]::GetFullPath($gitRaiz) -ine [IO.Path]::GetFullPath($repo)) { throw 'Indica la raiz original MYCAR-EQUIPO1-git.' }
    $rama = & git -C $repo branch --show-current
    $estadoInicial = & git -C $repo status --porcelain
    if ($rama -ne 'feat/programar-alerta-rf22') {
        if ($estadoInicial) { throw 'Hay cambios locales en otra rama. Conserva tu trabajo antes de instalar RF22.' }
        & git -C $repo show-ref --verify --quiet refs/heads/feat/programar-alerta-rf22
        if ($LASTEXITCODE -eq 0) { throw 'La rama RF22 ya existe. Abrela con git switch feat/programar-alerta-rf22 y vuelve a ejecutar el instalador.' }
        & git -C $repo switch -c feat/programar-alerta-rf22 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la rama RF22.' }
    }
    $revision = & git -C $repo rev-parse --verify --quiet '529927ac0398d09f71c6a40d0c6dbf67a8b3f621^{commit}'
    $existe = $LASTEXITCODE -eq 0
    $contiene = $false
    if ($existe) {
        & git -C $repo merge-base --is-ancestor 529927ac0398d09f71c6a40d0c6dbf67a8b3f621 HEAD
        $contiene = $LASTEXITCODE -eq 0
    }
    if (-not $contiene) {
        $pendientes = & git -C $repo status --porcelain
        if ($pendientes) { throw 'Para incorporar el acceso de Elsa primero conserva los cambios locales. No se reemplazo ningun archivo.' }
        Write-Host 'Incorporando en TU rama RF22 el main revisado con acceso RF01/RF02/RF06 (PR 11).'
        & git -C $repo fetch origin main | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo descargar main. No se copiaron archivos RF22.' }
        & git -C $repo merge --ff-only 529927ac0398d09f71c6a40d0c6dbf67a8b3f621 | Out-Host
        if ($LASTEXITCODE -ne 0) { throw 'Tu rama requiere una integracion revisada. No se hizo merge de conflictos ni se descarto trabajo.' }
    }
    $migraciones = Join-Path $repo 'backend/src/main/resources/db/mvp'
    $otraV6 = @(Get-ChildItem -LiteralPath $migraciones -File -Filter 'V6__*.sql' | Where-Object { $_.Name -ne 'V6__rf22_tiempo_sin_lectura.sql' })
    if ($otraV6.Count -gt 0) { throw 'Ya existe otra migracion V6. No se copiaron archivos RF22. Comparte el nombre con el equipo antes de continuar.' }
    $manifiesto = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO_RF22.json') -Raw | ConvertFrom-Json
    foreach ($registro in $manifiesto.archivos) {
        $origen = Join-Path (Join-Path $PSScriptRoot 'archivos') $registro.ruta
        if (-not (Test-Path -LiteralPath $origen -PathType Leaf) -or (Get-Rf22Hash $origen) -ne $registro.sha256) { throw "Paquete incompleto o alterado: $($registro.ruta)" }
        $destino = Join-Path $repo $registro.ruta
        if (Test-Path -LiteralPath $destino) {
            if (-not (Test-Path -LiteralPath $destino -PathType Leaf)) { throw "La ruta no es un archivo: $($registro.ruta)" }
            $actual = Get-Rf22Hash $destino
            if ($actual -ne $registro.sha256 -and ($null -eq $registro.sha256Base -or $actual -ne $registro.sha256Base)) {
                throw "Hay cambios diferentes a la version revisada. No se copio nada: $($registro.ruta)"
            }
        } elseif ($null -ne $registro.sha256Base) { throw "Falta un archivo del proyecto original. No se copio nada: $($registro.ruta)" }
    }
    $respaldo = Join-Path $CarpetaRespaldos ('RF22_respaldo_' + $Componente + '_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path $respaldo -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO_RF22.json') -Destination $respaldo
    foreach ($registro in $manifiesto.archivos) {
        $destino = Join-Path $repo $registro.ruta
        if (Test-Path -LiteralPath $destino -PathType Leaf) {
            $copia = Join-Path $respaldo $registro.ruta
            New-Item -ItemType Directory -Path (Split-Path -Parent $copia) -Force | Out-Null
            Copy-Item -LiteralPath $destino -Destination $copia
        }
    }
    foreach ($registro in $manifiesto.archivos) {
        $destino = Join-Path $repo $registro.ruta
        New-Item -ItemType Directory -Path (Split-Path -Parent $destino) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path (Join-Path $PSScriptRoot 'archivos') $registro.ruta) -Destination $destino -Force
    }
    Write-Host "RF22 $Componente incorporado en $repo"
    Write-Host "Respaldo fuera del proyecto: $respaldo"
    Write-Host 'No se hizo commit ni push. El main revisado solo se incorpora por avance directo si faltaba. No se modifico .env.'
    return $repo
}
function Build-Rf22Android([string]$Repositorio) {
    Push-Location (Join-Path $Repositorio 'android')
    try {
        & .\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest '-Pmycar.apiBaseUrl=http://10.0.2.2:8081/'
        if ($LASTEXITCODE -ne 0) { throw 'Fallo la compilacion o las pruebas Android. Comparte el final del resultado.' }
    } finally { Pop-Location }
}
function Abrir-Rf22([string]$Repositorio,[string]$Actividad,[string]$Dispositivo) {
    $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
    if (-not (Test-Path -LiteralPath $adb)) { throw 'No encuentro adb. Abre el proyecto con Android Studio y verifica la ubicacion del SDK.' }
    $estado = & $adb -s $Dispositivo get-state 2>$null
    if ($LASTEXITCODE -ne 0 -or $estado -ne 'device') { throw "Abre el emulador y espera que inicie. Dispositivo esperado: $Dispositivo" }
    & $adb -s $Dispositivo install -r (Join-Path $Repositorio 'android\app\build\outputs\apk\debug\app-debug.apk')
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo instalar la app.' }
    & $adb -s $Dispositivo shell am start -S -n ('com.mycar.app/' + $Actividad)
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir RF22. Comparte el resultado.' }
}
