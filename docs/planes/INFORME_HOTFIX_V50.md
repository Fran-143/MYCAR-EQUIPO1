# Informe hotfix v50 — UI Historial + Planes + Tema + rediseños

Fecha: 2026-11-24 · Backend local `http://127.0.0.1:8081` · Samsung `RFCX406KNYW`

## Estado del plan (auditoría)

| Ítem | Estado |
|------|--------|
| A Historial: botón al lado del título; sin cabecera/nav en form taller | Completo (previo + verificado) |
| B Listado 1 solicitud/usuario + hermanas + sandbox P30D | Completo (previo + verificado) |
| C Tema oscuro negro + hardcodes admin | Completo (tokens + barrido admin) |
| D Registrar mantenimiento visual | Completo (5 secciones + cabecera degradado) |
| E RF22 3 pantallas + modal contacto talleres | Completo (previo + títulos/ajustes) |
| F APK v50 | Ver sección APK |

## Continuación en esta sesión

- Confirmado: A/B ya estaban implementados; no se rehicieron.
- Tema claro: `background = MyCarBg` (`#F6F8FC`).
- Admin: Usuarios, Catálogos, Incidencias, Hub → `colorScheme`.
- Títulos mantenimiento alineados a especificación.
- RF22: sección «Vehículo y servicio».
- `SoporteDetalleUi`: valor con `onSurface`.
- versionCode **50**.

## APK

| Campo | Valor |
|-------|-------|
| Nombre | `MyCar-UI-Planes-Tema-v50-Samsung.apk` |
| Ruta | `dist/MyCar-UI-Planes-Tema-v50-Samsung.apk` |
| versionCode / Name | 50 / `1.2.26-ui-planes-tema-v50` |
| applicationId | `com.mycar.app.hotfix` |

## Pruebas

| Caso | Resultado |
|------|-----------|
| Compilación assembleDebug hotfix | PASS |
| Instalación Samsung `RFCX406KNYW` | PASS (`versionCode=50`) |
| Backend `:8081` + Postgres local | PASS (UP tras reinicio Docker) |
| API login + `/suscripciones/mia` fixtures | PASS |
| UI visual claro/oscuro Samsung | NO EJECUTADA (revisión manual) |
