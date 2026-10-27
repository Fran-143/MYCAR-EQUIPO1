$ErrorActionPreference = 'Stop'

function Get-RF18Hash([string]$Ruta) {
    $texto = [IO.File]::ReadAllText($Ruta).Replace("`r`n", "`n")
    $algoritmo = [Security.Cryptography.SHA256]::Create()
    try {
        return ([BitConverter]::ToString($algoritmo.ComputeHash([Text.Encoding]::UTF8.GetBytes($texto)))).Replace('-', '').ToLowerInvariant()
    } finally { $algoritmo.Dispose() }
}

function Preparar-RF18([string]$Repositorio) {
    if (-not (Test-Path -LiteralPath (Join-Path $Repositorio 'backend\pom.xml'))) {
        throw 'No encuentro el proyecto original. Indica -Repositorio con la ruta correcta.'
    }
    $raiz = & git -C $Repositorio rev-parse --show-toplevel
    if ($LASTEXITCODE -ne 0) { throw 'La carpeta no es un repositorio Git.' }
    if ([IO.Path]::GetFullPath($raiz).TrimEnd('\') -ne [IO.Path]::GetFullPath($Repositorio).TrimEnd('\')) {
        throw 'La ruta indicada no corresponde a la raiz del repositorio.'
    }
    $conflictos = & git -C $Repositorio diff --name-only --diff-filter=U
    if ($LASTEXITCODE -ne 0 -or $conflictos) { throw 'Resuelve la integracion pendiente antes de aplicar RF18.' }
    $rama = & git -C $Repositorio branch --show-current
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo comprobar la rama.' }
    if ($rama -ne 'feat/historial-mantenimiento-rf18') {
        $pendientes = & git -C $Repositorio status --porcelain
        if ($LASTEXITCODE -ne 0 -or $pendientes) {
            throw 'Guarda o revisa tus cambios antes de cambiar a la rama de RF18. No se copiaron archivos.'
        }
        & git -C $Repositorio fetch origin 'refs/heads/main:refs/remotes/origin/main'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar main en GitHub. Revisa tu conexion.' }
        & git -C $Repositorio show-ref --verify --quiet refs/heads/feat/historial-mantenimiento-rf18
        if ($LASTEXITCODE -eq 0) {
            & git -C $Repositorio switch feat/historial-mantenimiento-rf18
        } else {
            & git -C $Repositorio switch --no-track -c feat/historial-mantenimiento-rf18 origin/main
        }
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo abrir la rama de RF18.' }
    }
    $incluyeLogin = $false
    $baseConocida = & git -C $Repositorio rev-parse --verify --quiet '032aef221e796fad68cc2772fe98b7a6b9333ba9^{commit}'
    if ($LASTEXITCODE -eq 0 -and $baseConocida) {
        & git -C $Repositorio merge-base --is-ancestor 032aef221e796fad68cc2772fe98b7a6b9333ba9 HEAD
        $incluyeLogin = $LASTEXITCODE -eq 0
    }
    if (-not $incluyeLogin) {
        $pendientes = & git -C $Repositorio status --porcelain
        if ($LASTEXITCODE -ne 0 -or $pendientes) { throw 'Falta el main actualizado con RF08/RF09 y hay cambios pendientes. Revisa git status antes de continuar.' }
        & git -C $Repositorio fetch origin 'refs/heads/main:refs/remotes/origin/main'
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo consultar el main actualizado.' }
        & git -C $Repositorio merge --ff-only origin/main
        if ($LASTEXITCODE -ne 0) { throw 'Esta rama necesita integrar main manualmente antes de aplicar RF18. No se copiaron archivos.' }
        & git -C $Repositorio merge-base --is-ancestor 032aef221e796fad68cc2772fe98b7a6b9333ba9 HEAD
        if ($LASTEXITCODE -ne 0) { throw 'Falta incorporar el main actualizado con RF08/RF09.' }
    }
}

function Aplicar-RF18([string]$Repositorio, [string]$Paquete, [string]$Modulo) {
    $manifiesto = Get-Content -LiteralPath (Join-Path $Paquete 'MANIFIESTO_RF18.json') -Raw -Encoding UTF8 | ConvertFrom-Json
    foreach ($archivo in $manifiesto.archivos) {
        if (-not $archivo.ruta.StartsWith($Modulo + '/') -or $archivo.ruta.Contains('..')) {
            throw 'El manifiesto contiene una ruta inesperada.'
        }
        $origen = Join-Path (Join-Path $Paquete 'archivos') $archivo.ruta
        $destino = Join-Path $Repositorio $archivo.ruta
        if (-not (Test-Path -LiteralPath $origen) -or (Get-RF18Hash $origen) -ne $archivo.nuevo) {
            throw "Paquete incompleto o alterado: $($archivo.ruta)"
        }
        if (Test-Path -LiteralPath $destino) {
            $actual = Get-RF18Hash $destino
            if ($actual -ne $archivo.original -and $actual -ne $archivo.nuevo) {
                throw "Hay una version diferente a la revisada. No se copiaron archivos: $($archivo.ruta)"
            }
        } elseif ($null -ne $archivo.original) {
            throw "Falta un archivo original. No se copiaron archivos: $($archivo.ruta)"
        }
    }
    $respaldo = Join-Path (Split-Path $Repositorio -Parent) ('RF18_respaldo_' + $Modulo + '_' + (Get-Date -Format 'yyyyMMdd_HHmmss') + '_' + [Guid]::NewGuid().ToString('N').Substring(0,8))
    New-Item -ItemType Directory -Path $respaldo -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $Paquete 'MANIFIESTO_RF18.json') -Destination (Join-Path $respaldo 'MANIFIESTO_RF18.json')
    foreach ($archivo in $manifiesto.archivos) {
        $destino = Join-Path $Repositorio $archivo.ruta
        if (Test-Path -LiteralPath $destino) {
            $copia = Join-Path $respaldo $archivo.ruta
            New-Item -ItemType Directory -Path (Split-Path $copia -Parent) -Force | Out-Null
            Copy-Item -LiteralPath $destino -Destination $copia
        }
    }
    foreach ($archivo in $manifiesto.archivos) {
        $destino = Join-Path $Repositorio $archivo.ruta
        New-Item -ItemType Directory -Path (Split-Path $destino -Parent) -Force | Out-Null
        Copy-Item -LiteralPath (Join-Path (Join-Path $Paquete 'archivos') $archivo.ruta) -Destination $destino -Force
    }
    Write-Host "RF18 $Modulo aplicado. Respaldo: $respaldo"
}
