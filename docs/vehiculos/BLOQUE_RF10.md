# RF10 — Detalle del vehículo

Actualización: 28/10/2026. Rama: `feat/detalle-vehiculo-rf10`.

## Alcance

- Consulta de un vehículo **propio** autenticado (misma sesión que RF08/RF09).
- Campos alineados con el registro RF08 y la tarjeta RF09, más identificadores opcionales y aceite cuando aplica.
- Navegación desde listado RF09 («Ver detalles») e Inicio RF50 («Ver vehículo →»).
- RF14 (actualizar kilometraje) sigue en «Próximamente».

## Backend

- `GET /api/v1/vehiculos/{idVehiculo}` — JSON `DatosDetalleVehiculo`.
- Autorización: el `id_usuario` de la sesión debe coincidir con el propietario; otro id devuelve **404** `VEHICULO_NO_ENCONTRADO` (sin filtrar existencia ajena).
- Sin migraciones nuevas; lectura sobre `mycar.vehiculo` y última lectura INICIAL/ACTUALIZACION.

## Android

- `DetalleVehiculoRoute` + `DetalleVehiculoViewModel` + repositorio Retrofit compartiendo `ClienteVehiculosAutenticado`.
- `vehiculoDetalleId` en `MyCarNavigation` (saveable) para conservar el listado al volver.
- Estados: carga, error con reintento, sesión requerida, no disponible.

## Pruebas

- Backend: `ListadoVehiculosControllerTest`, `ListadoVehiculosPostgresTest`, `SesionVehiculosIntegrationTest` (propiedad).
- Android: `RemoteDetalleVehiculosRepositoryTest`, `DetalleVehiculoScreenTest`.

## Participación

- Integración RF50/sesión y backend RF10: equipo MyCar Equipo 1 con apoyo Codex/Cursor.
- RF01/RF02/RF06 (Elsa), RF08/RF09, RF17/RF18 (compañeras): sin reescritura en este bloque.

## Pendiente funcional

- Imagen del vehículo (sin contrato de almacenamiento en RF08).
- Edición de datos del vehículo (fuera de RF10).
