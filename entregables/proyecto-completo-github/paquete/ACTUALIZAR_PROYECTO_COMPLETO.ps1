[CmdletBinding()]
param(
    [string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git",
    [string]$Respaldos = [Environment]::GetFolderPath('MyDocuments'),
    [string]$UsuarioBD = 'mycar', [string]$BaseBD = 'mycar_mvp',
    [string]$Dispositivo = 'emulator-5554',
    [switch]$AplicarV6Publicada
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path -LiteralPath $Repositorio).Path
$meta = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO.json') -Raw | ConvertFrom-Json
function Git-Proyecto([string[]]$Argumentos) {
    $salida = & git -C $repo @Argumentos
    if ($LASTEXITCODE -ne 0) { throw "Git se detuvo: $($Argumentos -join ' '). Comparte el resultado sin forzar cambios." }
    return $salida
}
function Docker-Proyecto([string[]]$Argumentos) {
    $salida = & docker @Argumentos
    if ($LASTEXITCODE -ne 0) { throw 'Docker no pudo completar la comprobacion o el respaldo. No se continuara.' }
    return $salida
}
function Consulta-Proyecto([string]$Sql) {
    return Docker-Proyecto -Argumentos @('compose','exec','-T','postgres','psql','-X','-U',$UsuarioBD,'-d',$BaseBD,'-At','-v','ON_ERROR_STOP=1','-c',$Sql)
}
$raiz = Git-Proyecto -Argumentos @('rev-parse','--show-toplevel')
if ([IO.Path]::GetFullPath($raiz) -ine [IO.Path]::GetFullPath($repo)) { throw 'Indica la raiz original MYCAR-EQUIPO1-git.' }
if (Git-Proyecto -Argumentos @('status','--porcelain')) { throw 'Hay cambios locales nuevos. Comparte git status antes de actualizar; no se ha copiado nada.' }
$arbol = Git-Proyecto -Argumentos @('rev-parse','HEAD^{tree}')
$yaActualizado = $arbol -eq $meta.arbolFinal
if (-not $yaActualizado -and $arbol -notin @($meta.arbolesAnteriores)) { throw 'Tu version difiere de la que revisamos. Comparte git status y git log -3 --oneline.' }
if (-not $yaActualizado) {
    Git-Proyecto -Argumentos @('var','GIT_AUTHOR_IDENT') | Out-Null
    Git-Proyecto -Argumentos @('var','GIT_COMMITTER_IDENT') | Out-Null
    Git-Proyecto -Argumentos @('fetch','origin') | Out-Host
    if ((Git-Proyecto -Argumentos @('rev-parse','origin/main')) -ne $meta.mainGitHub -or
        (Git-Proyecto -Argumentos @('rev-parse','origin/feat/mantenimientos-rf22-rf23')) -ne $meta.baseGitHub) {
        throw 'GitHub recibio otros cambios. Comparte el resultado para revisar la version nueva; no se modifico tu proyecto ni tu BD.'
    }
    & git -C $repo show-ref --verify --quiet refs/heads/feat/mantenimientos-rf22-rf23
    if ($LASTEXITCODE -eq 0) { throw 'La rama feat/mantenimientos-rf22-rf23 ya existe. Comparte git status antes de usarla.' }
}
foreach ($item in $meta.archivos) {
    $archivo = Join-Path (Join-Path $PSScriptRoot 'proyecto') $item.ruta
    if (-not (Test-Path -LiteralPath $archivo -PathType Leaf) -or
        (Get-FileHash -LiteralPath $archivo -Algorithm SHA256).Hash.ToLowerInvariant() -ne $item.sha256) { throw "Paquete incompleto o alterado: $($item.ruta)" }
}
if ((Get-FileHash (Join-Path $PSScriptRoot 'AlinearV6.java') -Algorithm SHA256).Hash.ToLowerInvariant() -ne $meta.shaAuxiliar) { throw 'El auxiliar de compatibilidad fue alterado.' }
$tcp = New-Object Net.Sockets.TcpClient
$ocupado = $false
try { $tcp.Connect('127.0.0.1',8081); $ocupado = $true } catch {} finally { $tcp.Dispose() }
if ($ocupado) { throw 'Primero detén Spring Boot con Ctrl+C. Debe quedar libre 8081 antes de actualizar.' }
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
if (-not (Test-Path -LiteralPath $adb)) { throw 'No encuentro adb en el SDK habitual.' }
$estado = & $adb -s $Dispositivo get-state
if ($LASTEXITCODE -ne 0 -or $estado -ne 'device') { throw 'Enciende el emulador antes de continuar.' }
$archivosApp = & $adb -s $Dispositivo shell run-as com.mycar.app ls no_backup
if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar el almacenamiento de la app instalada. Comparte el resultado.' }
if (($archivosApp -join "`n") -match 'rf22-pendiente-') { throw 'Hay una operacion RF22 pendiente en la app anterior. Conservamos todo; resuelvela antes de cambiar de version.' }
Push-Location $repo
try {
    $historial = @(Consulta-Proyecto 'SELECT version,checksum,success FROM mycar.flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank;')
    if ($historial.Count -ne 6) { throw 'La base no tiene exactamente V1-V6. Comparte el historial antes de continuar.' }
    $v6 = @($historial | Where-Object { $_ -like '6|*' })
    if ($v6.Count -ne 1) { throw 'No encuentro V6 aplicada.' }
    $checksumActual = [int]($v6[0].Split('|')[1])
    if ($checksumActual -ne $meta.checksumV6Anterior -and $checksumActual -ne $meta.checksumV6GitHub) { throw 'Tu V6 tiene otro checksum. No se modifico nada.' }
    if ($checksumActual -ne $meta.checksumV6GitHub) {
        $nAlertas = [int](Consulta-Proyecto 'SELECT count(*) FROM mycar.alerta;')
        $nOps = [int](Consulta-Proyecto "SELECT count(*) FROM mycar.operacion_idempotente WHERE tipo_operacion='PROGRAMAR_ALERTA';")
        if ($nAlertas -gt 0 -or $nOps -gt 0) { throw "Hay $nAlertas alertas y $nOps operaciones RF22 de la version anterior. Comparte estos conteos; no se cambio ningun archivo ni dato." }
        if (-not $AplicarV6Publicada) { throw 'La V6 difiere. Revisa LEEME.md y usa -AplicarV6Publicada para aplicar la V6 oficial tras el respaldo.' }
    }
    $marca = (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0,8)
    $respaldo = Join-Path $Respaldos ('MyCar_antes_proyecto_completo_' + $marca)
    New-Item -ItemType Directory -Path $respaldo -Force | Out-Null
    $rutaZip = Join-Path $respaldo 'codigo_anterior.zip'
    Git-Proyecto -Argumentos @('archive','--format=zip','--output',$rutaZip,'HEAD') | Out-Host
    Git-Proyecto -Argumentos @('log','-3','--oneline') | Set-Content (Join-Path $respaldo 'version_anterior.txt') -Encoding UTF8
    $historial | Set-Content (Join-Path $respaldo 'flyway_antes.txt') -Encoding UTF8
    Git-Proyecto -Argumentos @('branch',('respaldo/antes-proyecto-completo-' + $marca)) | Out-Host
    $envArchivo = Join-Path $repo '.env'
    $envHash = if (Test-Path -LiteralPath $envArchivo) { (Get-FileHash $envArchivo).Hash } else { $null }
    $contenedor = Docker-Proyecto -Argumentos @('compose','ps','-q','postgres')
    if (-not $contenedor) { throw 'No encuentro el contenedor postgres de este proyecto.' }
    $dumpRemoto = '/tmp/mycar_respaldo_' + [Guid]::NewGuid().ToString('N') + '.dump'
    Docker-Proyecto -Argumentos @('compose','exec','-T','postgres','pg_dump','-U',$UsuarioBD,'-d',$BaseBD,'-Fc','-f',$dumpRemoto) | Out-Host
    Docker-Proyecto -Argumentos @('cp',($contenedor + ':' + $dumpRemoto),(Join-Path $respaldo 'base_anterior.dump')) | Out-Host
    Docker-Proyecto -Argumentos @('compose','exec','-T','postgres','rm','--',$dumpRemoto) | Out-Host
    if ((Get-Item (Join-Path $respaldo 'base_anterior.dump')).Length -lt 100) { throw 'El respaldo de BD no es valido.' }
    Write-Host "Respaldos de codigo y BD: $respaldo"
    Push-Location (Join-Path $repo 'backend')
    try {
        $rutaCP = Join-Path $respaldo 'classpath.txt'
        & .\mvnw.cmd -q dependency:build-classpath '-DincludeScope=runtime' ("-Dmdep.outputFile=" + $rutaCP)
        if ($LASTEXITCODE -ne 0) { throw 'No se pudieron preparar las dependencias. No se alineo V6.' }
    } finally { Pop-Location }
    $puerto = Docker-Proyecto -Argumentos @('compose','port','postgres','5432')
    if ($puerto -notmatch ':(\d+)$') { throw 'No se pudo determinar el puerto PostgreSQL.' }
    $urlBD = 'jdbc:postgresql://127.0.0.1:' + $Matches[1] + '/' + $BaseBD
    $claveBD = Docker-Proyecto -Argumentos @('compose','exec','-T','postgres','printenv','POSTGRES_PASSWORD')
    $anterior = @{}
    foreach ($nombre in @('CLASSPATH','MYCAR_ACTUALIZAR_URL','MYCAR_ACTUALIZAR_USER','MYCAR_ACTUALIZAR_PASSWORD')) { $anterior[$nombre] = [Environment]::GetEnvironmentVariable($nombre,'Process') }
    try {
        $env:CLASSPATH = [IO.File]::ReadAllText($rutaCP).Trim()
        $env:MYCAR_ACTUALIZAR_URL = $urlBD
        $env:MYCAR_ACTUALIZAR_USER = $UsuarioBD
        $env:MYCAR_ACTUALIZAR_PASSWORD = ($claveBD -join "`n")
        $java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
        $sqlCarpeta = Join-Path $PSScriptRoot 'proyecto/backend/src/main/resources/db/mvp'
        & $java (Join-Path $PSScriptRoot 'AlinearV6.java') $sqlCarpeta ([string]$meta.checksumV6GitHub) '--aplicar-v6-publicada'
        if ($LASTEXITCODE -ne 0) { throw "No se completo la compatibilidad. Conserva el respaldo $respaldo y comparte el error antes de arrancar Spring." }
    } finally {
        foreach ($nombre in $anterior.Keys) { [Environment]::SetEnvironmentVariable($nombre,$anterior[$nombre],'Process') }
        $claveBD = $null
    }
    if (-not $yaActualizado) {
        Git-Proyecto -Argumentos @('switch','-c','feat/mantenimientos-rf22-rf23','--track','origin/feat/mantenimientos-rf22-rf23') | Out-Host
        Git-Proyecto -Argumentos @('merge','--no-edit',$meta.mainGitHub) | Out-Host
    }
    if ((Git-Proyecto -Argumentos @('rev-parse','HEAD^{tree}')) -ne $meta.arbolFinal) { throw 'El proyecto no coincide con la version GitHub revisada. Comparte el resultado.' }
    if ($null -ne $envHash -and (Get-FileHash $envArchivo).Hash -ne $envHash) { throw '.env difiere; revisar el respaldo.' }
    Write-Host 'Proyecto completo de GitHub incorporado. Rama: feat/mantenimientos-rf22-rf23. V1-V6, sin V7.'
    Write-Host 'Tu rama anterior y los respaldos siguen disponibles. No se hizo push ni se fusiono main en GitHub.'
} finally { Pop-Location }
