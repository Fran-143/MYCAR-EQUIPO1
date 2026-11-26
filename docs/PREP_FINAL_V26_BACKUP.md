# Prep final V26 — respaldo y listos (sin migrate/deploy)

## Resumen ejecutivo

1. **Respaldo creado:** SÍ  
2. **¿Incluye datos?:** SÍ (full dump; el script viejo era schema-only)  
3. **¿Restauración comprobada?:** SÍ (Docker aislado; no sobre Neon)  
4. **Riesgos pendientes:** ver abajo  
5. **¿V26 lista para ejecutarse?:** SÍ (técnica; falta tu OK)  
6. **¿Render listo para desplegarse?:** SÍ (procedimiento; falta push+deploy)  
7. **Autorización que necesito:**  
   `Autorizo Flyway V26 en Neon con -ConfirmoNeonV26`  
   y, en paso separado tras verificar V26:  
   `Autorizo push de integracion/talleres-session-apk y deploy Render desde 59a0817+`

## 1. Qué hacía el script viejo

`fcm_neon_backup_schema.ps1`: `pg_dump --schema-only` → **sin** usuarios/vehículos/talleres/datos.

## 2. Respaldo ejecutado

Script nuevo: `scripts/neon_backup_full.ps1`  
Stamp: `20261113_222550`  
Ruta: `%LOCALAPPDATA%\MyCar\neon-backups\` (no Git)

Restauración aislada OK: 14 usuarios, 8 vehículos, 3 talleres, 12 mant., 22 alertas, 14 prefs, Flyway máx 25.

## 3. Neon Free restore options

| Mecanismo | Free |
|-----------|------|
| Dump local (este) | Sí — retención bajo tu control |
| PITR / Instant restore | Sí, **máx 6 h** / 1 GB history |
| Manual snapshot | **1** |
| Scheduled backups | No |

## 4. V26 (sin ejecutar)

- `neon_flyway_v26.ps1` exige `-ConfirmoNeonV26`; sin flag aborta.
- Flyway migrate desde `db/mvp` sobre V25 → aplica solo pendientes (V26).
- ADD COLUMN defaults **false**; demos en `taller_global` con `es_demo` y **sin** contacto autorizado.
- No DELETE de usuarios/talleres; fail-fast si dups (hoy 0).
- No activa talleres asociados ni setea `contacto_autorizado=true`.

## 5. Render (sin desplegar)

- Rama remota deploy: `origin/deploy/render-free` (tip reciente ~`5f8d513` RF12–16).
- Código talleres/contacto: local `59a0817` (+ docs posteriores); **~117 archivos** vs deploy tip.
- `SPRING_FLYWAY_ENABLED=false` → deploy **no** migra; orden: **V26 primero**, luego imagen `59a0817+`.
- Push de `integracion/talleres-session-apk` aún no autorizado.

## 6. Neon post-backup

Flyway prod sigue en **25** — sin escritura DDL.
