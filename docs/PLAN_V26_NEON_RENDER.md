# Plan V26 → Neon + Render (DETENERSE: requiere autorización)

Fecha prep: 2026-11-13 · Rama local `integracion/talleres-session-apk` · Código contacto `59a0817` · docs `eacd26a`

## 1. Estado real Neon (solo lectura — ejecutado)

| Chequeo | Resultado |
|---------|-----------|
| Host (sin secretos) | `ep-autumn-night-bcyfc2fr.c-16.us-east-1.aws.neon.tech` / `neondb` |
| Credenciales | Existen en `C:\Proyectos\MyCarEquipo1\.env.neon` (password set). **Faltan** en worktree `MyCarEquipo1-talleres-apk` |
| Usuario | `neondb_owner` — USAGE/CREATE schema + INSERT/UPDATE `taller` |
| Flyway máx | **25** (`alerta push recordatorios`) |
| V26 aplicada | **No** |
| `taller_global` | **No existe** |
| Columnas V26 (`origen`, `contacto_autorizado`, `id_taller_contacto`) | **No** |
| Talleres / activos / usuarios | 3 / 1 / 14 |
| Duplicados teléfono ACTIVO | **0** (precheck V26 OK) |
| ¿V26 borra datos? | **No** — ADD COLUMN / CREATE TABLE / índices; fail-fast si hubiera dups |

Credenciales en este worktree: copiar `.env.neon` desde el repo principal o `scripts/solicitar_password_neon.ps1` (no pegar en chat).

## 2. Estado real Render

| Chequeo | Resultado |
|---------|-----------|
| URL | `https://mycar-backend-1dxi.onrender.com/` |
| Perfiles | `SPRING_PROFILES_ACTIVE=neon,render` (`render.yaml`) |
| **Flyway al arrancar** | **`SPRING_FLYWAY_ENABLED=false`** + `application-neon.properties` → **deploy NO aplica V26** |
| JPA | `ddl-auto=validate` (falla si el código espera columnas ausentes) |
| Health | Intermitente por cold start Free; `catalogo-global` → **401** (ruta existe) |
| Sesión | `/api/v1/sesion` → 403 sin auth |
| Rama desplegada | Remoto `deploy/render-free` / RF12–16 en otros worktrees; **`integracion/talleres-session-apk` NO está en origin** (solo local) |
| V26 Live | **No** (coincide con Neon RO) |

## 3. Riesgos

1. **Orden obligatorio:** migrar V26 **antes** de redeploy del backend `59a0817+` (si no, `validate` tumba el arranque).
2. Revertir código Render **no** revierte V26 en Neon.
3. Free cold start: health puede timeout tras idle.
4. Push de rama integración requiere autorización (aún no está en remoto).
5. FCM sidecar (`.hotfix`) independiente; no bloquea talleres.
6. No autorizar `contacto_autorizado` ni piloto en Neon sin OK explícito.

## 4. Cambios productivos que requieren TU autorización

- [ ] Backup Neon verificable
- [ ] Flyway migrate **V26** en Neon (`-ConfirmoNeonV26`)
- [ ] Push / deploy Render del commit `59a0817` (o posterior validado)
- [ ] Cualquier seed piloto / `CONTACTO_AUTORIZADO=true`
- [ ] Push remoto / merge a rama de deploy

## 5. Procedimiento propuesto (NO ejecutado)

### A. Respaldo DB
```powershell
# En repo con .env.neon (p.ej. C:\Proyectos\MyCarEquipo1 o copia al worktree)
.\scripts\fcm_neon_backup_schema.ps1
```
Además: snapshot / PITR en consola Neon (punto de recuperación).

### B. Migración V26 (controlada)
```powershell
# Worktree talleres-apk con .env.neon presente
.\scripts\neon_flyway_v26.ps1 -ConfirmoNeonV26
```
Verificar RO: Flyway=26, existe `taller_global`, columnas `contacto_autorizado`, demos `es_demo` con auth=false.

### C. Deploy backend
1. Autorizar push de `integracion/talleres-session-apk` o cherry-pick a rama de deploy.
2. Redeploy servicio `mycar-backend` (Docker/`render.yaml`) con imagen de `59a0817+`.
3. Health `200 UP` (esperar cold start).

### D. Smoke post-deploy
- Auth login
- RF34 listar / catalogo-global
- Detalle alerta: manual sin teléfono accionable; asociado no autorizado redactado
- Mant / historial / perfil

### Recuperación
| Fallo | Acción |
|-------|--------|
| Backend malo | Redeploy commit anterior (**DB sigue en V26**) |
| V26 mala | Restaurar snapshot Neon / PITR (**no** alcanza con revert de Git) |

## 6. APK

Recomendada (ya apunta a Render):

`C:\Proyectos\MyCarEquipo1-talleres-apk\dist\MyCar-a2-contacto-59a0817-sidecar-hotfix-render.apk`  
Package: `com.mycar.app.hotfix` · Commit: `59a0817`

Alternativa: `...\MyCar-a2-contacto-59a0817-debug-render.apk` (`com.mycar.app`).

Recompilar solo si cambia `API_BASE_URL` o el contrato cliente tras deploy.

## 7. Checklist E2E (tras tu OK + deploy)

Ver `docs/CHECKLIST_E2E_V26_RENDER.md`.

## DETENERSE

**No se ha modificado Neon ni se ha desplegado Render.**  
Responde con autorización explícita, por ejemplo:

`Autorizo: (1) backup Neon (2) Flyway V26 (3) push+deploy Render desde 59a0817`
