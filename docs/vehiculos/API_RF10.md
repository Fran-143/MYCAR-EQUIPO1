# API RF10 — Detalle de vehículo

## GET `/api/v1/vehiculos/{idVehiculo}`

**Autenticación:** Bearer (sesión Elsa), misma cadena que RF09.

**Respuesta 200** (`application/json`):

| Campo | Tipo | Descripción |
|-------|------|-------------|
| idVehiculo | long | Identificador interno |
| placa | string | Placa normalizada |
| marca, modelo | string | |
| anioFabricacion | int | |
| usoVehiculo | string | Nombre legible del catálogo |
| color | string | |
| tipoMotor | string | Incluye texto «Otro» si aplica |
| combustible | string? | Null en motores eléctricos |
| estadoVehiculo | ACTIVO \| INACTIVO | |
| principal | boolean | |
| kilometrajeActual | int? | Última lectura INICIAL/ACTUALIZACION |
| fechaUltimaLecturaKm | instant? | ISO-8601 UTC |
| numeroVin | string? | |
| numeroSerie, numeroMotor | string? | |
| tipoAceite | string? | Null si eléctrico o no registrado |
| motorElectrico | boolean | |

**Errores**

| HTTP | codigo | Cuándo |
|------|--------|--------|
| 401 | SESION_NO_VALIDA | Sin sesión |
| 403 | CUENTA_NO_AUTORIZADA | Cuenta suspendida |
| 404 | VEHICULO_NO_ENCONTRADO | Id inexistente o no pertenece al usuario |
| 503 | SERVICIO_NO_DISPONIBLE | Fallo de datos |

No expone endpoints de edición ni imagen.
