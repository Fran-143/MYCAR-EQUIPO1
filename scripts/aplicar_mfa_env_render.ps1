# Configura MYCAR_MFA_* en el servicio Render mycar-backend sin imprimir secretos.
# Requiere: $env:RENDER_API_KEY y archivo %LOCALAPPDATA%\MyCar\render-free\mfa-aes.properties
# Uso: .\scripts\aplicar_mfa_env_render.ps1 -ServiceId <id>
param(
    [Parameter(Mandatory=$true)][string]$ServiceId
)
$ErrorActionPreference='Stop'
if ([string]::IsNullOrWhiteSpace($env:RENDER_API_KEY)) {
    throw "Define RENDER_API_KEY en el entorno (no lo pegues en Git ni en el chat)."
}
$mfaFile=Join-Path $env:LOCALAPPDATA 'MyCar\render-free\mfa-aes.properties'
if (!(Test-Path $mfaFile)) { throw "Falta $mfaFile — genera clave con el flujo de despliegue." }
$map=@{}
Get-Content $mfaFile | ForEach-Object {
    if ($_ -match '^\s*#' -or $_ -notmatch '=') { return }
    $k,$v=$_.Split('=',2); $map[$k.Trim()]=$v.Trim()
}
foreach ($need in @('MYCAR_MFA_AES_KEY')) {
    if (-not $map.ContainsKey($need) -or [string]::IsNullOrWhiteSpace($map[$need])) { throw "Falta $need en mfa-aes.properties" }
}
$body=@{
    envVars=@(
        @{ key='MYCAR_MFA_ENABLED'; value='true' },
        @{ key='MYCAR_MFA_KEY_ID'; value=($(if($map['MYCAR_MFA_KEY_ID']){$map['MYCAR_MFA_KEY_ID']}else{'v1'})) },
        @{ key='MYCAR_MFA_AES_KEY'; value=$map['MYCAR_MFA_AES_KEY'] }
    )
} | ConvertTo-Json -Depth 5
$headers=@{ Authorization="Bearer $($env:RENDER_API_KEY)"; Accept='application/json'; 'Content-Type'='application/json' }
Invoke-RestMethod -Method Put -Uri "https://api.render.com/v1/services/$ServiceId/env-vars" -Headers $headers -Body $body | Out-Null
Write-Host "MYCAR_MFA_* actualizadas en Render service $ServiceId (valores no impresos). Redeploy si no auto-reinicia."
