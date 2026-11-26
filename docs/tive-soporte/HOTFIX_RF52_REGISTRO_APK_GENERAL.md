# Hotfix RF52 + RF63 — TIVE registro y Soporte VIN (sin publicar aún)

## RF52 — causa raíz

1. Android `callTimeout(30s)` abortaba `POST .../tive` en frío Render Free.
2. `TiveService.registrar` enmascaraba integridad como `TIVE_EXISTE`.
3. UI ocultaba mensajes con `campos`; post-éxito frágil si fallaba el GET.

## RF63 — causa raíz

1. En `FormularioNueva`, un `return` temprano si `!tieneTive` **ocultaba** el VIN registrado (punto 7).
2. `GET /vehiculos-elegibles` leía solo `vehiculo.numero_vin`; ahora prioriza `tive.numero_vin` con `COALESCE`.
3. No se refrescaban vehículos al abrir Corrección (TIVE recién registrada quedaba stale).
4. Idempotencia Soporte: reutiliza `idOperacion` si el intento queda incierto (timeout 90s).

## Sin migraciones nuevas

Solo cambios de código Java/Kotlin. V32–V34 ya aplicadas.

## Publicación ejecutada

| Campo | Valor |
|-------|-------|
| Commits | `736a257` + `ab38ed4` en `origin/deploy/render-free` |
| Backup gate | `20261118_174039` |
| APK | `dist/MyCar-General-TIVE-Soporte-v14-hotfix.apk` (+ `%LOCALAPPDATA%\MyCar\entregas\`) |
| versionCode / Name | 14 / `1.1.2-tive-soporte-hotfix` |
| API | `https://mycar-backend-1dxi.onrender.com/` |
| Firma | Android Debug (misma SHA-256 que general anterior) |
| Rollback código | `git push origin 8b1f98a:deploy/render-free` |
| Rollback datos | dump `neon_mycar_full_20261118_174039.sql` (no auto-restore) |
