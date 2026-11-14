# Informe Agente 2 — Integración final, testing y APK

## A. Integración

| Campo | Valor |
|-------|-------|
| Worktree | `C:\Proyectos\MyCarEquipo1-talleres-apk` |
| Rama | `integracion/talleres-session-apk` |
| HEAD | `59a0817049ff1c55024795e6458b349691a4fecd` |
| Push/merge remoto | No |

### Commits verificados e integrados
| SHA | Rol | Ancestor de HEAD |
|-----|-----|------------------|
| `ca97769` | BASE compartida | Sí |
| `910ce05` | A1 contrato V26 | Sí |
| `46313ca` | A1 docs | Sí |
| `59a0817` | **A2** política contacto | HEAD |

Conflictos: ninguno (working tree limpio al partir de `46313ca`).

### Archivos A2
- `PoliticaContactoTaller.kt` + test
- `DetalleAlertaScreen.kt` (botones solo si política OK)
- `SincronizarAlertasService.aContacto` (redacta teléfono si no autorizado)
- `ContactoTallerAutorizacionTest.java`

## B. Funcionalidades

| RF | Funcionalidad | Estado | Evidencia | Pendientes |
|----|---------------|--------|-----------|------------|
| RF33–36 | Talleres CRUD/catálogo/favoritos | Código OK; Live parcial | Rf34 PG PASS | V26+redeploy Render |
| RF24 | Contacto WA/llamada | Política A2 cerrada | Unit BE+Android PASS | Probar con taller autorizado real |
| V26 | `contacto_autorizado` | Migración PG aislada PASS | V26IsolatedMigrationTest | Neon prod + Render |
| RF03 | Recovery | En BASE | Android unit PASS | Manual en APK |
| Sesión | Hotfix FCM mutex | En BASE `23a67c4`/`ca97769` | Compile+tests | Manual reabrir app |
| Mant/historial/perfil | Integrados en BASE | Unit parcial PASS | Manual en APK |

## C. Pruebas

| Test | Comando | Resultado | Nota |
|------|---------|-----------|------|
| ContactoTallerAutorizacionTest + talleres/alertas unit | `mvnw -Dtest=ContactoTallerAutorizacionTest,TallerServiceTest,...` | PASS | exit 0 |
| V26IsolatedMigrationTest | PG Docker 17 + `MYCAR_V26_*` | PASS | Incluye fail-fast dups |
| Rf34TalleresPostgresTest | PG Docker + `-Dmycar.rf34.tests=true` | PASS | Migra hasta v26 |
| Android talleres/alertas/auth/perfil | `gradlew testDebugUnitTest --tests ...` | PASS | Incluye PoliticaContactoTallerTest |
| Render health | GET `/actuator/health` | PASS | `200 {"status":"UP",...}` |
| Render catalogo-global | GET sin auth | 401 | Endpoint presente; auth requerida |
| Neon RO | — | BLOQUEADO | Sin `.env.neon` en worktree |

## D. Backend y base de datos

| Entorno | Estado |
|---------|--------|
| V26 local (Docker PG) | Validada (migrate OK; dups fail-fast OK) |
| PostgreSQL local | Temporal disposable; contenedores eliminados post-test |
| Neon prod | No tocado; sin credenciales aquí |
| Render | UP; RF34 responde 401; **sin evidencia de V26 Live** ni del DTO A2 desplegado |
| Pendiente deploy | Migrar V26 Neon + redeploy backend con `59a0817` (autorización humana) |

## E. APK

| Campo | Debug | Sidecar |
|-------|-------|---------|
| Nombre | `MyCar-a2-contacto-59a0817-debug-render.apk` | `MyCar-a2-contacto-59a0817-sidecar-hotfix-render.apk` |
| Ruta | `dist/` | `dist/` |
| Package | `com.mycar.app` | `com.mycar.app.hotfix` |
| Tamaño | 14 794 933 B | 14 794 945 B |
| Commit | `59a0817` | `59a0817` |
| Backend | `https://mycar-backend-1dxi.onrender.com/` | idem |
| Limitaciones | No E2E V26/contacto autorizado vs Render actual; FCM sidecar puede fallar (cliente stub) | |

APKs previas A1 conservadas: `MyCar-talleres-V26-*-render.apk`.

## F. Testing manual

Ver `docs/CHECKLIST_MANUAL_A2.md`.

## G. Próximos pasos

1. **Probar ya:** login, sesión, recovery, taller MANUAL, mant/historial/alertas/perfil, y que MANUAL no muestre WA/llamada.
2. **Para cerrar talleres:** V26 Neon + redeploy Render + taller ASOCIADO con `contacto_autorizado=true` autorizado explícitamente + retest casos 6–9.
3. **Autorización humana:** migrate Neon, deploy Render, piloto, autorizar contactos reales, push/merge.
4. **RF cerrados en código local:** política RF24 contacto; V26 SQL+DTO; RF33–36 en árbol local.
5. **Siguiente desarrollo recomendado:** (a) deploy V26, (b) prueba E2E asociado autorizado, (c) luego RF pendientes de notificaciones push/deep-link o siguientes RF del backlog — no empezar RF nuevos hasta validar APK en dispositivo.
