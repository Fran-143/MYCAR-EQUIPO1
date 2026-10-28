param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git")
. (Join-Path $PSScriptRoot 'RF22_COMUN.ps1')
$repo = Aplicar-Rf22 $Repositorio 'android'
Build-Rf22Android $repo
Write-Host 'Android RF22 compilado. Usa VER_DISENO_RF22.ps1 para ver ejemplos o ABRIR_RF22_REAL.ps1 para iniciar sesion y guardar.'
