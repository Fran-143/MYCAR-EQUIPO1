# MyCar Test — cuenta local permanente (USUARIO)

## Identidad

| Campo | Valor |
|-------|--------|
| Etiqueta | **MyCar Test** |
| Correo | `mycar.test@mycar.local` |
| Rol | `USUARIO` (nunca ADMIN) |
| Nombre | MyCar Test |
| Persistencia | PostgreSQL Docker local (`mycar_mvp`) |
| Contraseña | Solo en `%LOCALAPPDATA%\MyCar\mycar-test-local.json` (fuera de Git) |

No uses La Checa ni admins reales para pruebas de usuario.

## Cuenta SIN TIVE (pruebas negativas RF63)

| Campo | Valor |
|-------|--------|
| Correo | `mycar.sintive@mycar.local` |
| Rol | `USUARIO` |
| Vehículo | `SNT101` · Versa SIN TIVE · **sin TIVE** |
| Credenciales | `%LOCALAPPDATA%\MyCar\mycar-sintive-local.json` |

Úsala para comprobar que Soporte bloquea con mensaje claro si el vehículo no tiene TIVE.

## ADMIN local de prueba (MFA)

Ya existe en local:

| Campo | Valor |
|-------|--------|
| Correo | `admin.mfa.local@mycar.test` |
| Rol | `ADMIN` |
| MFA | `ACTIVA` |
| Notas | Solo USB/local. Credenciales auxiliares en `%LOCALAPPDATA%\MyCar\desarrollo-local\` |

Si hiciera falta recrear otro ADMIN de prueba (sin tocar fundadores):

1. Registrar usuario normal por API/app.
2. `.\scripts\habilitar_admin_local.ps1 -Correo <correo>` con `MYCAR_ADMIN_BOOTSTRAP_SECRET` del `.env` local.
3. Login → enroll TOTP en Google Authenticator.

## Estado actual (local)

| Recurso | Valor |
|---------|--------|
| Usuario | id `25`, `USUARIO`, `ACTIVA` |
| Vehículo | id `17`, placa `TST101`, modelo `Corolla TEST`, VIN de prueba |

## Cómo iniciar sesión (APK USB)

1. Backend local en `http://127.0.0.1:8081/` con JWT local (`%LOCALAPPDATA%\MyCar\desarrollo-local\autenticacion.properties`). Sin ese archivo el login responde 503.
2. Conectar el teléfono por USB y ejecutar `adb reverse tcp:8081 tcp:8081`.
3. Abrir MyCar → Iniciar sesión con `mycar.test@mycar.local`.
4. Contraseña: la del archivo local de credenciales (ábrelo tú; no la compartas).

## Tras reiniciar el PC

```powershell
cd C:\Proyectos\MyCarEquipo1-talleres-apk
docker compose up -d postgres
# Preferido (incluye JWT fuera de Git):
.\scripts\iniciar_backend_integracion_local.ps1
# Con el teléfono USB conectado:
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" reverse tcp:8081 tcp:8081
```

La cuenta **no** se borra al recompilar el APK ni al reiniciar el backend: vive en el volumen Docker de Postgres.

## Reutilizar / recrear vehículo de prueba

```powershell
.\scripts\preparar_mycar_test_local.ps1
```

Vehículo ficticio esperado: placa `TST101`, modelo `Corolla TEST`, VIN de prueba, serie/motor con prefijo `TEST`.

## Cambiar correo o contraseña

- Contraseña: cámbiala desde Perfil en la app, o regenera con el script (opción interactiva).
- Correo propio: `.\scripts\preparar_mycar_test_local.ps1 -Correo tu.correo@dominio`
  (crea otra cuenta USUARIO; no borra la anterior).

## Fuera de alcance

Render, Neon, cuentas fundadoras ADMIN reales, producción.
