param(
    [string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git"
)
$ErrorActionPreference = 'Stop'

function Get-HashTexto([string]$Ruta) {
    $contenido = [IO.File]::ReadAllText($Ruta).Replace("`r`n", "`n")
    $bytes = [Text.Encoding]::UTF8.GetBytes($contenido)
    $algoritmo = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algoritmo.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    } finally {
        $algoritmo.Dispose()
    }
}

if (-not (Test-Path -LiteralPath (Join-Path $Repositorio 'backend\pom.xml'))) {
    throw 'No encuentro backend\pom.xml en el proyecto original.'
}
$raizGit = & git -C $Repositorio rev-parse --show-toplevel
if ($LASTEXITCODE -ne 0) { throw 'La carpeta no es un repositorio Git.' }
if ([IO.Path]::GetFullPath($raizGit).TrimEnd('\') -ne [IO.Path]::GetFullPath($Repositorio).TrimEnd('\')) {
    throw 'La ruta indicada no es la raiz del repositorio original.'
}
$rama = & git -C $Repositorio branch --show-current
if ($LASTEXITCODE -ne 0 -or $rama -ne 'feat/mantenimientos-alertas-rf17-rf27') {
    throw 'Abre la rama feat/mantenimientos-alertas-rf17-rf27 antes de aplicar las correcciones.'
}
$manifiesto = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO.json') -Raw -Encoding UTF8 | ConvertFrom-Json

# Se comprueban TODOS los archivos antes de modificar el primero.
foreach ($archivo in $manifiesto.archivos) {
    if (-not $archivo.ruta.StartsWith('backend/src/') -or $archivo.ruta.Contains('..')) {
        throw 'El manifiesto contiene una ruta inesperada.'
    }
    $origen = Join-Path (Join-Path $PSScriptRoot 'archivos') $archivo.ruta
    $destino = Join-Path $Repositorio $archivo.ruta
    if (-not (Test-Path -LiteralPath $origen) -or (Get-HashTexto $origen) -ne $archivo.nuevo) {
        throw "El paquete esta incompleto o alterado: $($archivo.ruta)"
    }
    if (Test-Path -LiteralPath $destino) {
        $actual = Get-HashTexto $destino
        if ($actual -ne $archivo.original -and $actual -ne $archivo.nuevo) {
            throw "Este archivo tiene cambios diferentes al ZIP revisado. No se reemplazo nada: $($archivo.ruta)"
        }
    } elseif ($null -ne $archivo.original) {
        throw "Falta un archivo original. No se reemplazo nada: $($archivo.ruta)"
    }
}

$respaldo = Join-Path (Split-Path $Repositorio -Parent) ('RF17_respaldo_correcciones_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $respaldo -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'MANIFIESTO.json') -Destination (Join-Path $respaldo 'MANIFIESTO.json')
foreach ($archivo in $manifiesto.archivos) {
    $destino = Join-Path $Repositorio $archivo.ruta
    if (Test-Path -LiteralPath $destino) {
        $copia = Join-Path $respaldo $archivo.ruta
        New-Item -ItemType Directory -Path (Split-Path $copia -Parent) -Force | Out-Null
        Copy-Item -LiteralPath $destino -Destination $copia -ErrorAction Stop
    }
}
foreach ($archivo in $manifiesto.archivos) {
    $origen = Join-Path (Join-Path $PSScriptRoot 'archivos') $archivo.ruta
    $destino = Join-Path $Repositorio $archivo.ruta
    New-Item -ItemType Directory -Path (Split-Path $destino -Parent) -Force | Out-Null
    Copy-Item -LiteralPath $origen -Destination $destino -Force -ErrorAction Stop
}
Write-Host "Correcciones aplicadas. Respaldo: $respaldo"

Push-Location (Join-Path $Repositorio 'backend')
try {
    & .\mvnw.cmd '-Dtest=ValidadorRegistroMantenimientoTest,ValidadorRepuestoTest,VehiculoMantenimientoServiceTest,TipoMantenimientoServiceTest,LecturaKmMantenimientoServiceTest,RegistroMantenimientoServiceTest' test
    if ($LASTEXITCODE -ne 0) {
        throw "Las pruebas no terminaron correctamente. Conserva el resultado y el respaldo: $respaldo"
    }
} finally {
    Pop-Location
}
Write-Host 'Pruebas unitarias terminadas. Este script no ejecuta las pruebas PostgreSQL.'
Write-Host 'No se crearon commits ni se hizo push. La integracion con main se revisa aparte.'
