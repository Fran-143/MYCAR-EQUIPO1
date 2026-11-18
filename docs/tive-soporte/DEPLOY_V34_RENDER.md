# Despliegue TIVE + Soporte VIN (V32–V34) — Neon / Render / APK general

## Estado aplicado

| Elemento | Valor |
|----------|-------|
| Commit | `88dddf5` en `deploy/render-free` y `feat/tive-soporte-vin` |
| Neon Flyway | **V34** (`tive`, `soporte correccion vin`, `soporte evidencia opcional`) |
| Backup gate | `STAMP=20261118_022801` |
| Dump full | `%LOCALAPPDATA%\MyCar\neon-backups\neon_mycar_full_20261118_022801.sql` |
| Meta | `%LOCALAPPDATA%\MyCar\neon-backups\neon_mycar_full_20261118_022801.meta.txt` |
| Restore smoke | Docker PG17 (sin `pg_attver`): usuarios=15, vehiculos=8, flyway_max=31, mfa_cfg=2 |
| Backend | `https://mycar-backend-1dxi.onrender.com/` health UP; rutas TIVE/Soporte/admin VIN responden 401 sin sesión |
| APK general | `dist/MyCar-General-TIVE-Soporte.apk` (+ copia en `%LOCALAPPDATA%\MyCar\entregas\`) |
| APK versión | `versionCode=13` / `versionName=1.1.1-tive-soporte` |
| Firma | Android Debug SHA-256 `320bc5e68d76892c5488fef08a71259eebee2dd85aa7594f3014001f2f6a35b3` (igual que MFA-Admins) |
| API embebida | `https://mycar-backend-1dxi.onrender.com/` (sin `127.0.0.1:8081`) |

## Orden ejecutado

1. Backup full Neon + verificación de restore aislada.
2. `scripts/neon_flyway_v34.ps1 -ConfirmoNeonV34` (pendientes V32→V34).
3. Commit + push a `origin/deploy/render-free`.
4. Health Render UP.
5. APK general v13.

## Recuperación / rollback

### Código Render malo

```text
git push origin f3f20c2:deploy/render-free
```

Redeploy en Render. El esquema V32–V34 puede permanecer (aditivo; el backend anterior lo ignora).

### Datos / schema a medias o corruptos

1. No redeployar código nuevo hasta recuperar datos.
2. Restaurar dump `neon_mycar_full_20261118_022801.sql` (sanitizar líneas `pg_attver` si el destino no es Neon).
3. Alternativa Neon Free: PITR / Instant restore **solo si aún cabe en la ventana** (~6 h).
4. Reaplicar Flyway solo tras backup nuevo.

### APK malo

Redistribuir `%LOCALAPPDATA%\MyCar\entregas\MyCar-General-Actualizado-MFA-Admins.apk` (`versionCode=12`).

### Flyway duplicado

No reaplicar V32–V34 si `flyway_schema_history` ya tiene versiones 32–34 con `success=true`.
