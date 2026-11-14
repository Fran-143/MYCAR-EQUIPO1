package backend.talleres.model;

import java.time.Instant;
import java.util.List;

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
            Instant creadoUtc,
            String origen,
            boolean tieneWhatsapp,
            Long idTallerGlobal,
            boolean editable
    ) {
        /** Compatibilidad tests/legacy: MANUAL editable. */
        public Taller(
                long idTaller, String nombre, String telefono, String correo, String direccion,
                String notas, String estado, boolean favorito, int versionFila, Instant creadoUtc) {
            this(idTaller, nombre, telefono, correo, direccion, notas, estado, favorito, versionFila,
                    creadoUtc, "MANUAL", false, null, true);
        }
    }

    public record TallerGlobal(
            long idTallerGlobal,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            boolean tieneWhatsapp,
            String estado,
            boolean esDemo
    ) {
        /** Compatibilidad tests legacy sin esDemo. */
        public TallerGlobal(
                long idTallerGlobal, String nombre, String telefono, String correo,
                String direccion, boolean tieneWhatsapp, String estado) {
            this(idTallerGlobal, nombre, telefono, correo, direccion, tieneWhatsapp, estado, false);
        }
    }

    public record RegistrarTallerRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas,
            Boolean tieneWhatsapp
    ) {
        public RegistrarTallerRequest(String nombre, String telefono, String correo,
                                      String direccion, String notas) {
            this(nombre, telefono, correo, direccion, notas, false);
        }
    }

    public record VincularAsociadoRequest(long idTallerGlobal) { }

    public record ActualizarTallerRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas,
            Boolean tieneWhatsapp,
            int versionFila
    ) {
        public ActualizarTallerRequest(String nombre, String telefono, String correo,
                                       String direccion, String notas, int versionFila) {
            this(nombre, telefono, correo, direccion, notas, null, versionFila);
        }
    }

    public record RetirarTallerRequest(int versionFila) { }

    public record ListaTalleres(List<Taller> talleres) {
        public ListaTalleres {
            talleres = talleres == null ? List.of() : List.copyOf(talleres);
        }
    }

    public record ListaTalleresGlobales(List<TallerGlobal> talleres) {
        public ListaTalleresGlobales {
            talleres = talleres == null ? List.of() : List.copyOf(talleres);
        }
    }
}
