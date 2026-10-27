# RF17 — contrato HTTP PROPUESTO

Estado: IMPLEMENTADO en código y COMPILA. BLOQUEADO POR CONTRATO COMPARTIDO para publicación.
No sustituye el documento REST propuesto del equipo ni significa que estas rutas estén aprobadas.

## Activación y seguridad

El controlador está ausente por defecto. La propiedad `mycar.rf17.api-habilitada=true` permite
registrarlo solo después de acordar rutas/seguridad. No se cambia SecurityFilterChain ni el login.
Todas las operaciones exigen Authentication no anónima y un bean real `IdentidadMantenimiento`.
Sin ese adaptador se rechaza la petición; no se convierte getName() en un idUsuario.
No existe campo idUsuario en el contrato Android.

Además, el guardado y la consulta de operación requieren un bean real
`RegistroIdempotenteMantenimiento`. No se incluye una implementación porque el protocolo común
no está confirmado. Su ausencia deja `registroDisponible=false` y produce 503 antes de guardar.
No se interpreta idOperacion como garantía de deduplicación sin implementar ese adaptador.

## Rutas propias propuestas

| Método | Ruta | Uso |
|---|---|---|
| GET | /api/v1/rf17/contexto | Vehículos propios ACTIVO, principal, tipos activos, fecha Lima, último km y aceite de solo lectura |
| GET | /api/v1/rf17/vehiculos/{id}/lecturas?fechaServicio=YYYY-MM-DD | Lecturas del mismo vehículo hasta el día del servicio en Lima |
| POST | /api/v1/rf17/vehiculos/{id}/mantenimientos | Solicitud final de mantenimiento y repuestos |
| GET | /api/v1/rf17/operaciones/{uuid} | Reconciliación mediante el adaptador común, cuando esté confirmado |

El prefijo RF17 evita ocupar las rutas compartidas de Francesca. Las rutas originales de main
permanecen intactas. Solo hay APIs de apoyo a RF17; no se implementa RF18/RF22/RF23.

## Solicitud POST propuesta

```json
{
  "idOperacion": "e4fd74b4-c8ef-4fb6-a93a-e5c005958cb3",
  "codigoTipoMantenimiento": "MANTENIMIENTO_GENERAL",
  "fechaServicio": "2026-10-26",
  "kmServicio": 21000,
  "origenKmServicio": "LECTURA",
  "idLecturaKmReferencia": 701,
  "costoTotal": null,
  "servicioOtro": null,
  "observaciones": "Mantenimiento realizado",
  "proximaFechaSugerida": null,
  "proximoKmSugerido": null,
  "repuestos": [{"nombre":"Filtro", "marca":null, "cantidad":1, "precioUnitario":0}]
}
```

Los identificadores del ejemplo son ilustrativos; no son fixtures cargadas ni datos reales.
El UUID obligatorio es parte de esta propuesta, condicionado al acuerdo del adaptador común.
Se usa el código del tipo porque es lo que acepta el servicio existente. El backend resuelve su ID.
`observaciones` se guarda en mantenimiento.descripcion. `descripcion` es un alias JSON de esa
misma propiedad: enviar un solo nombre, no ambos. No se crea otra columna ni otro dato obligatorio.
`servicioOtro` es una propiedad distinta y solo se permite con OTRO.
Para HISTORICO_MANUAL la referencia es null. Para LECTURA el valor coincide con la lectura.
La lista de repuestos puede omitirse o ser vacía; nombres repetidos son válidos.

Los importes son números JSON decimales exactos. Se admiten null y 0 distintos. Valores negativos,
mayores que 9999999999.99 o con más de dos decimales significativos se rechazan sin redondear.
Las longitudes de textos se validan por caracteres Unicode: código 50, servicioOtro 160,
nombre de repuesto 140 y marca 120. Las observaciones usan el TEXT existente, sin inventar un límite.

## Respuestas propuestas

201: idOperacion, idMantenimiento, estado REALIZADO, kmServicio, fechaServicio,
costoTotal nullable, cantidadRepuestos y alertaCreada=false.
El adaptador debe devolver/conservar un snapshot de esta respuesta tras el COMMIT.

La consulta de operación propone CONFIRMADA, EN_PROCESO y NO_ENCONTRADA.
NO_ENCONTRADA por sí sola no demuestra que una transacción concurrente no vaya a confirmar.
El cliente no inicia otro UUID por esa respuesta. La seguridad de un reintento depende de la
idempotencia efectiva y confirmada del adaptador.

Errores JSON: codigo, mensaje, campos y resultadoIncierto. Códigos HTTP propuestos:
400 formato/reglas; 401 sesión; 403 acceso; 404 vehículo no disponible; 409 datos cambiados;
503 INTEGRACION_PENDIENTE antes de escribir; 500 RESULTADO_DESCONOCIDO, conservar solicitud.
Los filtros de seguridad compartidos pueden producir su propio 401/403; Android también trata
estos códigos aunque no reciba este JSON. No se devuelven SQL, credenciales o stack traces.

## Adaptadores necesarios

1. Elsa: implementar IdentidadMantenimiento con su sesión vigente/no revocada/no expirada y cuenta
   ACTIVA. Resolver el usuario real. No confiar en el nombre textual del usuario de desarrollo.
2. Equipo: confirmar/implementar RegistroIdempotenteMantenimiento usando el mecanismo común.
   Debe autorizar usuario y tipo de operación, coordinar reserva/bloqueo/huella/resultado en la
   misma transacción, llamar al RegistroMantenimientoService existente y devolver el snapshot.
   La consulta de estado debe estar igualmente autorizada por usuario y tipo.
3. Acordar rutas, DTO, códigos de error y alcance del UUID. Hay código de referencia RF08 en la
   rama de Francesca, pero eso no demuestra que el contrato compartido de RF17 esté aprobado.

Las clases OperacionMantenimiento y OperacionMantenimientoDao existentes se conservan.
No se implementa un motor idempotente alternativo ni se crea V5.
