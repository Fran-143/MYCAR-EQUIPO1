# RF14 — Actualizar kilometraje

## Fuentes de reglas

| Regla | Fuente |
|-------|--------|
| km entero > lectura vigente | `diccionario_columnas.csv` (valor_km), `V4` trigger, `06_TRANSACCIONES_SPRING_BOOT.md` |
| Solo vehículo ACTIVO | `diccionario` estado_vehiculo, trigger `tr_validar_lectura` |
| Idempotencia `ACTUALIZAR_KM` + hash | `06_TRANSACCIONES_SPRING_BOOT.md`, `operacion_idempotente` |
| `version_km` optimista | `diccionario` version_km, `README.md` MVP RF14 |
| Vista previa sin POST | `06_Contratos_REST_Francesca…docx` (Android) |
| GET actual / POST lecturas / GET operación km | Mismo contrato docx (rutas implementadas) |
| No modificar alertas | `CORRECCIONES.md`, transacciones RF14 |

## API implementada

- `GET /api/v1/vehiculos/{id}/kilometraje/actual`
- `POST /api/v1/vehiculos/{id}/kilometraje/lecturas`
- `GET /api/v1/vehiculos/kilometraje/operaciones/{idOperacion}`

## Dependencias PR

- Base temporal: **`feat/detalle-vehiculo-rf10`** (incluye RF10, integración, RF06).
- Tras fusionar RF10, cambiar base del PR a `main` o cadena acordada.

## Participación

Francesca (RF14), Elsa (auth), equipo RF17/RF18 sin reescritura; apoyo Codex/Cursor.
