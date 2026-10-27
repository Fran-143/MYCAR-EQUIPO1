# RF08 — Validaciones e identificación del vehículo

Actualización: 26/10/2026.

## Reglas de identificación

Los tres campos son opcionales. La ausencia se representa con null.
Una cadena vacía no es un identificador válido.

- VIN: exactamente 17 caracteres, letras A-Z y números 0-9.
  Se excluyen I, O y Q.
- Número de motor: entre 5 y 15 caracteres.
  Admite A-Z, 0-9, guion y espacio normal.
  Un valor compuesto solo por espacios es inválido.
- Número de serie: entre 6 y 80 caracteres.
  Admite únicamente A-Z y 0-9.
- Para motor eléctrico, numeroMotor debe permanecer null.
- La validación comprueba el valor recibido sin convertirlo
  automáticamente a mayúsculas ni eliminar espacios.

## Implementación disponible

- Validaciones básicas del registro.
- Validaciones condicionales de motor y opciones OTRO.
- Servicio de validación que consulta los catálogos.
- DAO para leer catálogos.
- DAO para comprobar placa, VIN y cantidad de vehículos.
- Inserción de la fila del vehículo.
- V5 incorpora tres restricciones CHECK de identificación.

V1–V5 ya aplicadas deben conservarse sin modificar.
Los cambios posteriores requieren una migración nueva.

## Evidencia local

Resultados proporcionados por Francesca:

- 86 pruebas de validación aprobadas.
- 39 pruebas PostgreSQL aprobadas en contenedor temporal.
- Prueba de contexto Spring aprobada.
- Health HTTP con estado UP.
- V1–V5 aplicadas correctamente en mycar_mvp.

## Pendiente para completar RF08

- Coordinar la identidad autenticada con Elsa.
- Integrar el servicio de plan Free de Sebastián.
- Resolver idempotencia antes de revalidar cupo.
- Coordinar vehículo, lectura inicial, configuración y resultado
  idempotente en una única transacción.
- Implementar el contrato HTTP y la pantalla Android.

La inserción aislada de la fila del vehículo todavía no constituye
el registro completo de RF08.