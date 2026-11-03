# Bloque RF22 / RF23 + integración RF14

## Matriz breve

| Requisito | Fuente | Regla | Implementación | Prueba |
|-----------|--------|-------|----------------|--------|
| RF22 programar alerta manual | `06_TRANSACCIONES_SPRING_BOOT.md` | Idempotencia, lock vehículo, firma SHA-256, historial v0 | `POST /api/v1/rf22/vehiculos/{id}/alertas` | `AlertasControllerTest` |
| RF23 Mis alertas | Misma + V4 SQL | `sincronizar_alertas_vehiculo` antes del GET | `GET /api/v1/rf22/vehiculos/{id}/alertas` | Manual + RF23 en BD (`MvpDatabaseTest` existente) |
| RF14 desde RF17 | Nayu contratos docx | Conservar borrador, vehículo seleccionado | Overlay `ActualizarKilometrajeRoute` en MANTENIMIENTOS | Manual + `ON_RESUME` recarga |
| RF14 desde RF10 | Corrección entrega | Formulario real, recarga detalle | Secundaria KILOMETRAJE + `recargaDetalle` | Manual |
| Aceite vehículo RF17 | Diccionario + contexto RF17 | Sugerir, no inventar | `aceiteRegistrado` + botón repuesto | `MantenimientoAceiteVehiculoTest` |

## Ya existía (reutilizado)

- Triggers y función RF23 en Flyway V4.
- RF17 contexto con `aceiteRegistrado`.
- RF14 Android + backend completo en rama base.

## Pendientes

- Escenarios EA completos del Excel sin parser local (validación manual con profesora).
- Enlace directo “crear alerta” desde recomendaciones RF17 (hoy vía pestaña Alertas).
- Prueba Postgres integrada RF22 en CI (`mycar.rf08.tests`).
