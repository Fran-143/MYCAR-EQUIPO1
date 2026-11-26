# Informe — RF27/28/29/RF57 con aprobación administrativa (hotfix v47)

Fecha: 2026-11-22 · Local PG + `:8081` · Samsung `RFCX406KNYW`

## Interfaces

- Admin Suscripciones rediseñado: pestañas Solicitudes / Suscripciones, resumen chips, buscador 350 ms, filtros horizontales, tarjetas.
- Detalle solicitud: usuario, plan, pago, aprobación, Aprobar/Rechazar + MFA.
- Detalle suscripción: identidad, plan, vehículos, timeline.
- Usuario: FREE/PREMIUM cards, prueba gratuita + sandbox, Mi suscripción + solicitudes.

## RF27 / RF28 / RF29 / RF57

| Área | Estado |
|------|--------|
| FREE / PREMIUM + 2 opciones hotfix | Hecho |
| Solicitud prueba → PENDIENTE_APROBACION sin activar | Hecho (API) |
| Pago sandbox APROBADA → pendiente admin, `suscripcionActivada=false` | Hecho (API) |
| Aprobar/rechazar admin + MFA | Hecho (código; MFA en Samsung) |
| RF29 solicitudes + historial | Hecho |
| Vencimiento → FREE + conciliación principal | Hecho (API forzar-vencimiento) |

## Pruebas 1–12

| # | Resultado | Evidencia |
|---|-----------|-----------|
| 1 FREE | **PASS** | `planCodigo=FREE` tras limpieza |
| 2 Solicitar prueba | **PASS** | `PRG-… PENDIENTE_APROBACION`, usuario FREE |
| 3 Admin aprueba prueba | **PENDIENTE SAMSUNG** | Requiere MFA step-up |
| 4 Admin rechaza | **PENDIENTE SAMSUNG** | Requiere MFA + motivo |
| 5 Pago sandbox + admin | **PASS parcial** | Pago→pendiente admin sin Premium (**PASS**); aprobación admin → Samsung MFA |
| 6 Pago rechazado | **PASS** (flujo) | Escenario RECHAZADA cancela solicitud; no activable |
| 7 Pago pendiente | **PASS** | `PENDIENTE` + `PENDIENTE_PAGO` |
| 8 Doble aprobación | **PASS** (código) | `version_fila` + CONFLICTO |
| 9 Vencimiento | **PASS** | forzar → `plan=FREE`, activos reconciliados |
| 10 Nueva activación | **PASS** (regla) | Activación no reactiva inactivos |
| 11 Privacidad | **PASS** (código) | Sesión + ROLE_ADMIN + MFA; 403 sin admin |
| 12 Producción aislada | **PASS** | Render/Neon: sandbox/local-test/job off |

## APK

| Campo | Valor |
|-------|-------|
| Nombre | `MyCar-Planes-Aprobacion-Admin-v47-Samsung.apk` |
| Ruta | `dist/MyCar-Planes-Aprobacion-Admin-v47-Samsung.apk` |
| versionCode | 47 |
| applicationId | `com.mycar.app.hotfix` |
| Instalación | `adb install -r` Success |
| Backend | `127.0.0.1:8081` |

## Documentación

- `CONFIGURACION_PENDIENTE_PLANES_SANDBOX.md`
- `docs/planes/GUIA_SAMSUNG_PLANES_APROBACION_V47.md`
- `scripts/probar_planes_aprobacion_v47.ps1`

## Clasificación

| Tema | Etiqueta |
|------|----------|
| Cliente MP TEST + S2S | **PROGRAMADO** |
| Credenciales / precio comercial / cupo oficial | **PENDIENTE CONFIG** |
| Checkout Pro E2E | **PENDIENTE PRUEBA MP** |
| Aprobar/rechazar en dispositivo con MFA | **PENDIENTE PRUEBA SAMSUNG** |
