package backend.vehiculos.dto;

/** Consulta orientativa para abrir el formulario, sin reservar cupo. */
public record DatosCupoRegistro(
        String plan,
        int limite,
        long registrados,
        boolean puedeRegistrar
) {
}
