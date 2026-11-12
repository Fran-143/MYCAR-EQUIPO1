package backend.talleres.model;

import java.time.Instant;

public final class DatosTaller {
    private DatosTaller() { }

    public record Taller(
            long idTaller,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas,
            String estado,
            boolean favorito,
            int versionFila,
            Instant creadoUtc
    ) { }

    public record RegistrarTallerRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas
    ) { }

    public record ActualizarTallerRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas,
            int versionFila
    ) { }

    public record RetirarTallerRequest(int versionFila) { }

    public record ListaTalleres(java.util.List<Taller> talleres) {
        public ListaTalleres {
            talleres = talleres == null ? java.util.List.of() : java.util.List.copyOf(talleres);
        }
    }
}
