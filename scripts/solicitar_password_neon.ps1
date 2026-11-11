# Solicita la contraseña Neon UNA vez via dialogo Windows (oculta).
# Escribe solo en .env.neon (gitignored). No imprime el valor.
[CmdletBinding()]
param(
    [string]$EnvFile = ""
)
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

$raiz = Split-Path -Parent $PSScriptRoot
if (-not $EnvFile) { $EnvFile = Join-Path $raiz ".env.neon" }
if (!(Test-Path -LiteralPath $EnvFile)) {
    Copy-Item -LiteralPath (Join-Path $raiz ".env.neon.example") -Destination $EnvFile
}

$form = New-Object System.Windows.Forms.Form
$form.Text = "MyCar — Neon PostgreSQL"
$form.Size = New-Object System.Drawing.Size(460, 200)
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedDialog"
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.TopMost = $true

$label = New-Object System.Windows.Forms.Label
$label.Location = New-Object System.Drawing.Point(16, 16)
$label.Size = New-Object System.Drawing.Size(420, 40)
$label.Text = "Introduce la contraseña de Neon (neondb_owner). No se muestra ni se envía al chat."
$form.Controls.Add($label)

$box = New-Object System.Windows.Forms.TextBox
$box.Location = New-Object System.Drawing.Point(16, 64)
$box.Size = New-Object System.Drawing.Size(410, 28)
$box.UseSystemPasswordChar = $true
$form.Controls.Add($box)

$ok = New-Object System.Windows.Forms.Button
$ok.Text = "Guardar en .env.neon"
$ok.Location = New-Object System.Drawing.Point(200, 110)
$ok.Size = New-Object System.Drawing.Size(140, 30)
$ok.DialogResult = [System.Windows.Forms.DialogResult]::OK
$form.AcceptButton = $ok
$form.Controls.Add($ok)

$cancel = New-Object System.Windows.Forms.Button
$cancel.Text = "Cancelar"
$cancel.Location = New-Object System.Drawing.Point(350, 110)
$cancel.Size = New-Object System.Drawing.Size(76, 30)
$cancel.DialogResult = [System.Windows.Forms.DialogResult]::Cancel
$form.CancelButton = $cancel
$form.Controls.Add($cancel)

$result = $form.ShowDialog()
if ($result -ne [System.Windows.Forms.DialogResult]::OK) {
    Write-Host "CANCELLED"
    exit 2
}
$pwdPlain = $box.Text
$box.Text = ""
$form.Dispose()
if ([string]::IsNullOrWhiteSpace($pwdPlain)) {
    Write-Host "EMPTY"
    exit 3
}

$lines = Get-Content -LiteralPath $EnvFile
$out = New-Object System.Collections.Generic.List[string]
$seen = $false
foreach ($line in $lines) {
    if ($line -match '^\s*SPRING_DATASOURCE_PASSWORD\s*=') {
        $out.Add("SPRING_DATASOURCE_PASSWORD=$pwdPlain")
        $seen = $true
    } else {
        $out.Add($line)
    }
}
if (-not $seen) { $out.Add("SPRING_DATASOURCE_PASSWORD=$pwdPlain") }
[System.IO.File]::WriteAllLines($EnvFile, $out)
$pwdPlain = $null
[GC]::Collect()
Write-Host "PASSWORD_SAVED_TO_ENV_NEON"
exit 0
