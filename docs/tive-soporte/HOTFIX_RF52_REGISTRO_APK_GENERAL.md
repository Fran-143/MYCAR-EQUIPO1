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

## Publicación (un solo despliegue, cuando autorices)

1. Backup fresco verificado.
2. Push `feat/tive-soporte-vin` → `deploy/render-free` + redeploy.
3. APK general **v14** `1.1.2-tive-soporte-hotfix` → Render URL, misma firma Debug.
4. **Se necesitan ambos:** redeploy Render **y** nuevo APK.
5. Rollback código: `8b1f98a`. Datos: dump del gate.
