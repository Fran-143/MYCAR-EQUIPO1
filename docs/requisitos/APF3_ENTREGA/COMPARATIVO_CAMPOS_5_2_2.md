# 5.2.2 Comparativo campos de pantalla ↔ modelo (MVP)

Procedencia de columnas BD: `V2__tablas_mvp.sql` (+ reglas V5/V6). Pantallas: Compose real.  
**No** se afirma aquí que cada migración se ejecutó en un host concreto.

| Pantalla (RF) | Campo UI | ¿En modelo? | Acción / ubicación |
|---------------|----------|-------------|--------------------|
| Registro usuario RF01 | nombre, apellido, correo, fecha_nacimiento, password | Sí | `mycar.usuario` |
| Registro usuario RF01 | consentimiento privacidad + versión | Sí | `consentimiento_privacidad_*` |
| Registro vehículo RF08 | placa | Sí | `vehiculo.placa` + validación dominio |
| Registro vehículo RF08 | marca, modelo, anio, color | Sí | columnas vehiculo |
| Registro vehículo RF08 | uso | Sí | FK `uso_vehiculo_codigo` |
| Registro vehículo RF08 | tipo motor / combustible / aceite | Sí | FK catálogos |
| Registro vehículo RF08 | km inicial | Sí vía lectura | `lectura_kilometraje` + operación |
| Registro vehículo RF08 | VIN opcional | Sí | `vehiculo.vin` (único nullable según diseño) |
| Actualizar km RF14 | nuevo kilometraje | Sí | `lectura_kilometraje.km` + `operacion_idempotente` |
| Mantenimiento RF17 | tipo, fecha, km, observación, repuestos | Sí | `mantenimiento`, `mantenimiento_repuesto`, `repuesto` |
| Alerta RF22 | criterio tiempo/km/ambos, intervalos, objetivos | Sí | `alerta` (+ V6 POR_TIEMPO sin lectura) |
| Mis alertas RF23 | estado, filtros | Sí | `alerta.estado` + `historial_alerta` |

Campos calculados (no persistidos como atributo libre): cupo FREE, km objetivo derivado, mensajes de UI.
