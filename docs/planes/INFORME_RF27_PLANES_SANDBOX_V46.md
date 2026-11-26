# Informe — RF27 / RF28 / RF29 / RF57 Planes Sandbox (local hotfix v46)

Fecha: 2026-11-22 · Entorno: PG local `mycar_mvp` + backend `127.0.0.1:8081` · APK hotfix Samsung instalada.

## Entregables

| Ítem | Estado |
|------|--------|
| Flyway V46 | Hecho (version 46 en `flyway_schema_history`) |
| API catálogo / mi suscripción / eventos | Hecho |
| Sandbox `SIMULADOR_LOCAL` | Hecho (APROBADA / RECHAZADA verificados) |
| Cliente Mercado Pago TEST | **PROGRAMADO** (código presente, apagado sin credenciales) |
| Admin hub Suscripciones + APIs solo lectura | Hecho (código; UI admin requiere MFA en dispositivo) |
| Android usuario `features/planes` | Hecho (hotfix simula; general → Próximamente) |
| Android admin Suscripciones | Hecho |
| `CONFIGURACION_PENDIENTE_PLANES_SANDBOX.md` | Hecho |
| APK `dist/MyCar-Planes-Suscripciones-Sandbox-v46-Samsung.apk` | Hecho · `adb install -r` Success en `RFCX406KNYW` |

## Pruebas locales API (resumen)

| Caso | Resultado |
|------|-----------|
| A Catálogo FREE/PREMIUM | OK (`listoParaContratar=true` tras SQL demo + vigencia) |
| B Mi suscripción FREE | OK |
| C Historial eventos | OK (crece tras operaciones) |
| D Crear operación simulador | OK |
| E Simular APROBADA → Premium sandbox | OK (`origen=SANDBOX_LOCAL`) |
| F Consultar operación | OK |
| G Simular RECHAZADA | OK |
| H Admin resumen/listado/verificar | Código listo; login admin exige MFA step-up (igual resto hub) |
| I MP TEST sin credenciales | OK → `PROVEEDOR_NO_CONFIGURADO` |

## Clasificación pendiente

| Tema | Etiqueta |
|------|----------|
| Precio/moneda/periodicidad/flag/vigencia “oficiales” | **PENDIENTE CONFIG** (marcadores en doc; demo SQL local no es comercial) |
| Credenciales + return URLs Mercado Pago TEST | **PENDIENTE CONFIG** |
| Prueba end-to-end Checkout Pro / webhook firma | **PENDIENTE PRUEBA MP** |
| Cliente HTTP + verificación S2S en código | **PROGRAMADO** (no operativo hasta config) |

## Fuera de alcance (sin cambios)

Neon, Render, Cloudflare, FCM, Google Play Billing, cobros reales, APK general release, RF26, RF62, Gestión de ingresos.
