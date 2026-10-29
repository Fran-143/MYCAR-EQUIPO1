[CmdletBinding()]
param(
    [string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git",
    [string]$Respaldos = [Environment]::GetFolderPath('MyDocuments')
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $Repositorio).Path
$meta = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO_ACTUALIZACION.json') -Raw | ConvertFrom-Json
function Git-Rf22([string[]]$Argumentos) {
    $salida = & git -C $repo @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "Git se detuvo: $($Argumentos -join ' '). No fuerces ni descartes archivos; comparte el resultado." }
    return $salida
}
function Hash-Rf22([string]$Archivo) {
    $texto = [IO.File]::ReadAllText($Archivo).Replace("`r`n","`n").TrimStart([char]0xFEFF)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($texto)))).Replace('-','').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$raiz = Git-Rf22 -Argumentos @('rev-parse','--show-toplevel')
if ([IO.Path]::GetFullPath($raiz) -ine [IO.Path]::GetFullPath($repo)) { throw 'Indica la raiz original MYCAR-EQUIPO1-git.' }
$rama = Git-Rf22 -Argumentos @('branch','--show-current')
if ($rama -ne 'feat/programar-alerta-rf22') { throw "Debes estar en feat/programar-alerta-rf22. Rama actual: $rama" }
$merge = & git -C $repo rev-parse --verify --quiet MERGE_HEAD
if ($LASTEXITCODE -eq 0) { throw 'Ya hay una fusion pendiente. Comparte git status antes de continuar.' }
if ((Git-Rf22 -Argumentos @('write-tree')) -eq $meta.arbolFinal -and -not (Git-Rf22 -Argumentos @('status','--porcelain'))) {
    Write-Host 'Esta actualizacion ya esta aplicada. Ejecuta COMPROBAR_RF22_ACTUALIZADO.ps1.'
    return
}
$head = Git-Rf22 -Argumentos @('rev-parse','HEAD')
if ($head -ne $meta.baseOriginal) { throw "Este actualizador requiere el estado revisado sobre 529927a. Tu HEAD es $head. Comparte git status y git log -3 --oneline." }
Git-Rf22 -Argumentos @('var','GIT_AUTHOR_IDENT') | Out-Null
Git-Rf22 -Argumentos @('var','GIT_COMMITTER_IDENT') | Out-Null
# Comprobar paquete completo y archivos originales ANTES de modificar el repositorio.
foreach ($registro in $meta.resoluciones) {
    $origen = Join-Path (Join-Path $PSScriptRoot 'archivos') $registro.ruta
    if (-not (Test-Path -LiteralPath $origen -PathType Leaf) -or (Hash-Rf22 $origen) -ne $registro.sha256) {
        throw "Paquete incompleto o alterado: $($registro.ruta)"
    }
}
$permitidos = @{}
foreach ($registro in $meta.entrada) {
    $permitidos[$registro.ruta] = $true
    $archivo = Join-Path $repo $registro.ruta
    if (-not (Test-Path -LiteralPath $archivo -PathType Leaf) -or (Hash-Rf22 $archivo) -ne $registro.sha256) {
        throw "Tu archivo difiere del RF22 que revisamos. No se modifico nada: $($registro.ruta)"
    }
}
$cambios = @((Git-Rf22 -Argumentos @('diff','--name-only','HEAD','--'))) + @((Git-Rf22 -Argumentos @('ls-files','--others','--exclude-standard')))
foreach ($ruta in $cambios) {
    if ($ruta -and -not $permitidos.ContainsKey($ruta)) { throw "Hay otro cambio fuera del RF22 revisado: $ruta. No se modifico nada." }
}
$revision = & git -C $repo rev-parse --verify --quiet ($meta.mainRevisado + '^{commit}')
if ($LASTEXITCODE -ne 0) {
    Git-Rf22 -Argumentos @('fetch','origin','main') | Out-Host
    Git-Rf22 -Argumentos @('rev-parse','--verify',($meta.mainRevisado + '^{commit}')) | Out-Null
}
$marca = (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0,8)
$respaldo = Join-Path $Respaldos ('RF22_antes_actualizar_main_' + $marca)
New-Item -ItemType Directory -Path $respaldo -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO_ACTUALIZACION.json') -Destination $respaldo
foreach ($registro in $meta.entrada) {
    $destino = Join-Path $respaldo $registro.ruta
    New-Item -ItemType Directory -Path (Split-Path -Parent $destino) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $repo $registro.ruta) -Destination $destino
}
$head | Set-Content -LiteralPath (Join-Path $respaldo 'HEAD_antes.txt') -Encoding UTF8
Git-Rf22 -Argumentos @('status','--short','--branch') | Set-Content -LiteralPath (Join-Path $respaldo 'ESTADO_antes.txt') -Encoding UTF8
$rutas = @($meta.entrada | ForEach-Object { $_.ruta })
Git-Rf22 -Argumentos (@('add','--') + $rutas) | Out-Host
Git-Rf22 -Argumentos @('diff','--cached','--check') | Out-Host
if ((Git-Rf22 -Argumentos @('write-tree')) -ne $meta.arbolRf22Original) {
    throw "El arbol preparado difiere del revisado. No se fusiono main; hay respaldo en $respaldo. Comparte el resultado."
}
Git-Rf22 -Argumentos @('commit','-m','feat(rf22): guardar backend y pantallas antes de actualizar main') | Out-Host
$ramaRespaldo = 'respaldo/rf22-antes-main-' + $marca
Git-Rf22 -Argumentos @('branch',$ramaRespaldo) | Out-Host
Write-Host "RF22 guardado en un commit local. Rama de respaldo: $ramaRespaldo"
Write-Host 'Combinando con main 8eaeef3. Se esperan seis conflictos conocidos que este paquete resuelve.'
& git -C $repo merge --no-commit --no-ff $meta.mainRevisado
$codigoMerge = $LASTEXITCODE
if ($codigoMerge -ne 0 -and $codigoMerge -ne 1) { throw 'Git no pudo iniciar la integracion. Tu RF22 ya tiene commit y respaldo. Comparte el resultado.' }
$pendientes = @(Git-Rf22 -Argumentos @('diff','--name-only','--diff-filter=U'))
$esperados = @($meta.conflictos)
$conflictosActuales = ($pendientes | Sort-Object) -join "`n"
$conflictosEsperados = ($esperados | Sort-Object) -join "`n"
if ($conflictosActuales -cne $conflictosEsperados) {
    throw 'La combinacion produjo conflictos diferentes a los revisados. Tu RF22 esta guardado. Comparte git status; no fuerces la resolucion.'
}
foreach ($registro in $meta.resoluciones) {
    $destino = Join-Path $repo $registro.ruta
    New-Item -ItemType Directory -Path (Split-Path -Parent $destino) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path (Join-Path $PSScriptRoot 'archivos') $registro.ruta) -Destination $destino -Force
}
$rutas = @($meta.resoluciones | ForEach-Object { $_.ruta })
Git-Rf22 -Argumentos (@('add','--') + $rutas) | Out-Host
Git-Rf22 -Argumentos @('diff','--cached','--check') | Out-Host
if ((Git-Rf22 -Argumentos @('write-tree')) -ne $meta.arbolFinal) {
    throw 'El resultado difiere de la integracion probada. RF22 tiene commit y respaldo. Comparte git status; no confirmes la fusion manualmente.'
}
Git-Rf22 -Argumentos @('commit','-m','merge: integrar main 8eaeef3 conservando RF22 y la sesion compartida') | Out-Host
Write-Host "Actualizacion integrada. Respaldo: $respaldo"
Write-Host 'Se crearon dos commits LOCALES. No se hizo push, PR ni fusion en GitHub. No se modifico tu rama main ni .env.'
Write-Host 'Ejecuta COMPROBAR_RF22_ACTUALIZADO.ps1 para verificar en tu Windows.'
