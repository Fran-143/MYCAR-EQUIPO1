# Informe — RF27 UX Planes + admin plan/cancel (hotfix v48)

Fecha: 2026-11-22 · Local PG + backend `:8081` · APK hotfix Samsung.

## Entregado
| Ítem | Estado |
|------|--------|
| UX Planes: prueba 30d, simular pago, “ya solicitaste” | Hecho |
| Admin usuario → Suscripciones: estado + aprobar/rechazar | Hecho |
| Admin cancela Premium prueba/sandbox → FREE | Hecho |
| Fixtures `plan.*@mycar.test` + endpoint preparar-planes | Hecho |
| Perfil: iconos y colores | Hecho |
| APK v48 sidecar | Ver build |

## API
- `POST /api/v1/admin/suscripciones/{idUsuario}/cancelar-premium`
- Detalle usuario incluye `suscripcion.modalidad`, `puedeCancelarAdmin`, `solicitudPremiumAbierta`
- `POST /api/v1/local-test/fixtures/preparar-planes`

## APK
| Campo | Valor |
|-------|-------|
| Nombre | `MyCar-Planes-UX-Admin-v48-Samsung.apk` |
| Ruta | `dist/MyCar-Planes-UX-Admin-v48-Samsung.apk` |
| applicationId | `com.mycar.app.hotfix` |
| versionCode / Name | 48 / `1.2.24-rf27-ux-admin-plan` |

## Notas
- Sin Play Billing / MP real / Neon / Render.
- Aprobar/rechazar puede pedir MFA step-up en sesión admin.
