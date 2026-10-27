package backend.mantenimiento.service;

import backend.mantenimiento.model.DatosRepuesto;

public class ValidadorRepuesto {

    public void validar(DatosRepuesto datos) {
        if (datos == null) {
            throw new IllegalArgumentException(
                    "repuesto: la línea es obligatoria"
            );
        }

        if (datos.nombre() == null || datos.nombre().isBlank()) {
            throw new IllegalArgumentException(
                    "nombre: es obligatorio y no puede estar vacío"
            );
        }

        LimitesAlmacenamiento.texto("nombre", datos.nombre(), 140);
        LimitesAlmacenamiento.texto("marca", datos.marca(), 120);
        LimitesAlmacenamiento.importe("precioUnitario", datos.precioUnitario());

        if (datos.cantidad() == null || datos.cantidad() <= 0) {
            throw new IllegalArgumentException(
                    "cantidad: es obligatoria y debe ser mayor que cero"
            );
        }

        if (datos.precioUnitario() != null
                && datos.precioUnitario().signum() < 0) {

            throw new IllegalArgumentException(
                    "precioUnitario: no puede ser negativo"
            );
        }
    }
}
