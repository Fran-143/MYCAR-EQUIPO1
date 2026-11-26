# Informe Agente 1 — V26 + APK (base para Agente 2)

Rama: `integracion/talleres-session-apk`  
Worktree: `C:\Proyectos\MyCarEquipo1-talleres-apk`  
Sin push / sin deploy / sin writes Neon.

## 1. SHA base compartido
- **BASE:** `ca97769cd8c224e0e546c3f11f3287b104d74f48`
- Mensaje: `feat(integracion): talleres V26 RF33-36 + recovery + hotfix sesion`
- Padre hotfix sesión: `23a67c4`

## 2. Commits Agente 1 (tras base)
- **A1:** `910ce058fbd1fc9bc6b6bd597f0008f0b806c205` — `feat(v26): contacto_autorizado + piloto sin usuario fijo + sidecar APK`

## 3. Estado V26
- SQL: `taller_global` + `taller` con `origen`, `tiene_whatsapp`, **`contacto_autorizado`** (default false).
- Constraint: `contacto_autorizado` ⇒ `tiene_whatsapp` + teléfono no vacío.
- Al vincular ASOCIADO se copia `contacto_autorizado` desde global.
- Semántica: teléfono o ASOCIADO **no** autorizan contacto solos.

## 4. Tests
- Backend unit (talleres/alertas/historial/mant/piloto/notif): **PASS**
- Android unit (auth/recovery/talleres/alertas/historial/perfil/prefs/notif): **PASS**
- `V26IsolatedMigrationTest` / `Rf34TalleresPostgresTest`: **BLOQUEADOS** — Docker Desktop no disponible en esta máquina.
- Neon RO relectura: **BLOQUEADA** — sin `.env.neon` en este worktree (sesión previa: 0 dups, Flyway 25, sin `taller_global`).

## 5. Bloqueos
- Docker/PG aislado off → no revalidar migración V26 en runtime local.
- Catálogo ASOCIADO vs Render actual: **inútil** hasta V26 Live (Render aún V25).
- Sidecar FCM: cliente stub `com.mycar.app.hotfix` en `google-services.json` local (gitignored); FCM puede fallar, sesión no.

## 6. Rutas APK
| APK | applicationId | Backend |
|-----|---------------|---------|
| `dist/MyCar-talleres-V26-debug-render.apk` | `com.mycar.app` | `https://mycar-backend-1dxi.onrender.com/` |
| `dist/MyCar-talleres-V26-sidecar-hotfix-render.apk` | `com.mycar.app.hotfix` | idem |

Build sidecar: `-Pmycar.hotfixSidecar=true -Pmycar.apiBaseUrl=https://mycar-backend-1dxi.onrender.com/`

## 7. Recovery / sesión / vehículos / mant / perfil
- Hotfix sesión FCM fuera del mutex: incluido en BASE.
- Recovery real (API/repo/UI): incluido en BASE.
- Talleres CRUD/catálogo/favoritos, historial, mantenimiento, perfil, preferencias: incluidos en BASE + contrato contacto.

## 8. Contrato para Agente 2
- Campo API/DTO: `ContactoTallerDto.contactoAutorizado` (también Android `ContactoTallerAlerta.contactoAutorizado`).
- **Agente 2** enciende botones WA/llamada en `DetalleAlertaScreen` **solo si** `contactoAutorizado == true` y taller asociado activo.
- No editar política exclusiva WhatsApp/llamada / `WhatsAppEnlace*` / permisos contacto salvo DTO (ya expuesto).

## 9. Prep Render
- Ver `docs/CHECKLIST_RENDER_V26.md` (documento; no ejecutado).

## 10. Autorizaciones concretas pendientes (humanas)
1. Migrar Flyway **V26** en Neon (backup previo verificable).
2. Redeploy backend Render compatible con `contactoAutorizado`.
3. Opcional: seed/piloto local con `MYCAR_TALLER_PILOTO_USUARIO_ID` + WhatsApp; `CONTACTO_AUTORIZADO=true` solo con prueba autorizada.
4. Tras commit Agente 2: cherry-pick/merge LOCAL, re-tests, APK final — sin push hasta autorización.
