param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git", [string]$Dispositivo = 'emulator-5554')
. (Join-Path $PSScriptRoot 'RF22_COMUN.ps1')
Build-Rf22Android $Repositorio
Abrir-Rf22 $Repositorio '.features.alertas.programacion.ui.ProgramacionPreviewActivity' $Dispositivo
Write-Host 'Diseno DEBUG: datos de ejemplo, sin conexion ni guardado real.'
