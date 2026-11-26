# Informe hotfix v51 — cupo FREE + planes simples + UI

Fecha: 2026-11-24 · Local PG + backend `:8081` · Flyway **v49**.

## Cambios

| Área | Detalle |
|------|---------|
| Cupo FREE | `max_vehiculos_registrados = 1` (V49). rf.activa no puede registrar más de 1. |
| Planes | Sin escenarios sandbox. Prueba gratuita 14d (admin aprueba/rechaza una vez). **Pagar** → solicitud pago simulado → admin aprueba = Premium 30d. Detener Premium → FREE + conciliación. |
| Perfil | Al reabrir siempre hub (`resetAlAbrir`). Sin selector claro/oscuro. Tema fijo claro. |
| Inicio / Historial / Alertas | Cards con iconos; filtros hide-on-scroll-down; cache de listas. |
| Mantenimiento | Formulario + repuesto con iconos; Actualizar KM vuelve al formulario (con o sin editar). |
| Admin | Contador **días restantes** en detalle y listado de suscripciones. |

## APK

| Campo | Valor |
|-------|-------|
| Archivo | `dist/MyCar-hotfix-v51-cupo-planes-ui.apk` |
| applicationId | `com.mycar.app.hotfix` |
| versionCode / Name | **51** / `1.2.27-cupo-planes-ui-v51` |
| Compilación assembleDebug hotfix | PASS |
| Backend health | UP · Flyway v49 aplicada |
