param([string]$Repositorio = "$env:USERPROFILE\Documents\MYCAR-EQUIPO1-git", [string]$Dispositivo = 'emulator-5554')
. (Join-Path $PSScriptRoot 'RF22_COMUN.ps1')
Build-Rf22Android $Repositorio
Abrir-Rf22 $Repositorio '.features.alertas.programacion.ui.EntradaRf22' $Dispositivo
Write-Host 'RF22 real: inicia sesion con una cuenta existente. Backend RF22 debe estar habilitado en 8081.'
