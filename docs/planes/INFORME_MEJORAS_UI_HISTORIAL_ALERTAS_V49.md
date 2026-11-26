# Informe — Mejoras UI / Historial / Alertas / Suscripciones (hotfix v49)

Fecha: 2026-11-22 · Backend local `http://127.0.0.1:8081` · Samsung `RFCX406KNYW`

## ADMINISTRACIÓN
- Resumen de suscripciones **plegable** (cerrado por defecto, `expand_more`/`expand_less`, AnimatedVisibility).
- Detalle solicitud/suscripción: flecha `arrow_back`; sin botón texto “Volver”.
- Historial oculto; botón **Ver historial** / **Ocultar historial** + timeline Lima.
- Cancelar Premium (prueba/sandbox) en detalle suscripción con modal y motivo obligatorio.
- Prueba gratuita **única por cuenta** (backend + índice V48).

## PERFIL
- Colores vía `MaterialTheme.colorScheme` (fondo/superficie/texto/campos/diálogos).
- Modo claro y oscuro con contraste corregido; acentos navy/teal de marca.

## INICIO
- Orden: Alertas → Talleres → Mantenimientos (datos reales / vacíos tipados).
- Recarga al volver; sin reload por recomposición.

## NAVEGACIÓN
- Vehículos: `DirectionsCar`.
- Selección teal/azul; adapta tema; insets systemBars.

## CABECERA
- Avatar real + campana en Inicio, Vehículos, Historial, Alertas, Notificaciones.

## HISTORIAL
- Orden: cabecera → título → acción → selector → pestañas → filtros → lista.
- `+ Registrar`; dropdown tipo; orden reciente/antiguo contrastado.
- Kilometraje sin filtro de tipo / sin Actualizar km.
- Talleres: `+ Agregar` solo formulario personal.

## TALLERES
- Sin asociación global para usuario.
- Backend 403 `TALLER_ASOCIACION_NO_AUTORIZADA`.
- Bug doble pantalla Agregar/Detalle corregido (tick sticky eliminado).

## ALERTAS
- Cabecera con avatar; paleta MyCar.
- Botones **Ver detalle | Anular** en fila (Anular solo si RF24 aplica).

## PRUEBAS
| # | Resultado |
|---|-----------|
| 1 Resumen plegable | NO EJECUTADA (UI Samsung) |
| 2 Detalle admin flecha/historial | NO EJECUTADA (UI) |
| 3 Cancelación Premium | NO EJECUTADA (requiere MFA admin en dispositivo) |
| 4 Prueba gratuita única | PASS (API: 2ª solicitud bloqueada; motivo “Ya utilizaste…”) |
| 5 Modo claro | NO EJECUTADA (UI) |
| 6 Modo oscuro | NO EJECUTADA (UI) |
| 7 Inicio orden/datos | NO EJECUTADA (UI) |
| 8 Barra inferior | NO EJECUTADA (UI) |
| 9 Avatar global | NO EJECUTADA (UI) |
| 10 Historial mantenimientos | NO EJECUTADA (UI) |
| 11 Kilometraje | NO EJECUTADA (UI) |
| 12 Talleres sin global | NO EJECUTADA (UI; backend auth añadido) |
| 13 Bug Agregar taller | NO EJECUTADA (UI; causa raíz corregida en código) |
| 14 Alertas botones | NO EJECUTADA (UI) |
| 15 Regresión RF27/API | PASS parcial (mia + unicidad prueba + Flyway 48) |

## APK
| Campo | Valor |
|-------|-------|
| Nombre | `MyCar-Mejoras-UI-Historial-Alertas-v49-Samsung.apk` |
| Ruta | `dist/MyCar-Mejoras-UI-Historial-Alertas-v49-Samsung.apk` |
| versionCode / Name | 49 / `1.2.25-mejoras-ui-historial-alertas` |
| applicationId | `com.mycar.app.hotfix` |
| Compilación | SUCCESS |
| Instalación Samsung | Success (`RFCX406KNYW`) |
| Backend | `127.0.0.1:8081` local |

## Migraciones
- `V48__rf27_prueba_gratuita_unica_por_cuenta.sql` — índice único parcial `PRUEBA_GRATUITA` por `id_usuario`.

## Archivos clave
- Admin: `SuscripcionesAdminScreens.kt`, `SuscripcionesAdminViewModel.kt`
- Planes: `PlanesService.java`, `PlanesJdbcDao.java`, `PlanesScreens.kt`, `FixtureCuentasLocalService.java`
- Perfil: `PerfilScreens.kt`, `PerfilTheme.kt`
- Inicio/Nav: `HomeScreen.kt`, `MyCarNavigation.kt`, `RemoteResumenInicioRepository.kt`, `CoordinadorInicio.kt`
- Historial/Talleres: `HistorialMantenimientoScreen.kt`, `TalleresScreen.kt`, `TallerService.java`, `TalleresController.java`
- Alertas: `AlertasScreen.kt`
