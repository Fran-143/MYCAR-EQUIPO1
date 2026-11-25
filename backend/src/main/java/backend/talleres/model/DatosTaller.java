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
            boolean contactoAutorizado,
            Long idTallerGlobal,
            boolean editable
    ) {
        /** Compatibilidad tests/legacy: MANUAL editable, sin autorización de contacto. */
        public Taller(
                long idTaller, String nombre, String telefono, String correo, String direccion,
                String notas, String estado, boolean favorito, int versionFila, Instant creadoUtc) {
            this(idTaller, nombre, telefono, correo, direccion, notas, estado, favorito, versionFila,
                    creadoUtc, "MANUAL", false, false, null, true);
        }

        /** Compatibilidad tests con origen/WA sin contactoAutorizado. */
        public Taller(
                long idTaller, String nombre, String telefono, String correo, String direccion,
                String notas, String estado, boolean favorito, int versionFila, Instant creadoUtc,
                String origen, boolean tieneWhatsapp, Long idTallerGlobal, boolean editable) {
            this(idTaller, nombre, telefono, correo, direccion, notas, estado, favorito, versionFila,
                    creadoUtc, origen, tieneWhatsapp, false, idTallerGlobal, editable);
        }
    }

    public record TallerGlobal(
            long idTallerGlobal,
            String nombre,
            String telefono,
            String correo,
            String direccion,
            boolean tieneWhatsapp,
            boolean contactoAutorizado,
            String estado,
            boolean esDemo
    ) {
        /** Compatibilidad tests legacy sin esDemo / contactoAutorizado. */
        public TallerGlobal(
                long idTallerGlobal, String nombre, String telefono, String correo,
                String direccion, boolean tieneWhatsapp, String estado) {
            this(idTallerGlobal, nombre, telefono, correo, direccion, tieneWhatsapp, false, estado, false);
        }

        public TallerGlobal(
                long idTallerGlobal, String nombre, String telefono, String correo,
                String direccion, boolean tieneWhatsapp, String estado, boolean esDemo) {
            this(idTallerGlobal, nombre, telefono, correo, direccion, tieneWhatsapp, false, estado, esDemo);
        }
    }

    public record RegistrarTallerRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            String notas,
            Boolean tieneWhatsapp,
            String origen,
            Boolean esGlobal,
            Boolean asociado,
            Long idTallerGlobal
    ) {
        public RegistrarTallerRequest(String nombre, String telefono, String correo,
                                      String direccion, String notas, Boolean tieneWhatsapp) {
            this(nombre, telefono, correo, direccion, notas, tieneWhatsapp, null, null, null, null);
        }

        public RegistrarTallerRequest(String nombre, String telefono, String correo,
                                      String direccion, String notas) {
            this(nombre, telefono, correo, direccion, notas, false);
        }

        /** Intento de marcar el alta como catálogo global u oficialmente asociado a MyCar. */
        public boolean intentaMarcarGlobalOAsociado() {
            if (idTallerGlobal != null && idTallerGlobal > 0) return true;
            if (Boolean.TRUE.equals(esGlobal) || Boolean.TRUE.equals(asociado)) return true;
            if (origen == null || origen.isBlank()) return false;
            String o = origen.trim();
            return o.equalsIgnoreCase("ASOCIADO")
                    || o.equalsIgnoreCase("GLOBAL")
                    || o.equalsIgnoreCase("OFICIAL");
        }
    }

    public record VincularAsociadoRequest(long idTallerGlobal) { }

    /** Alta admin de taller oficial en catálogo global (no es taller del usuario). */
    public record RegistrarTallerGlobalRequest(
            String nombre,
            String telefono,
            String correo,
            String direccion,
            Boolean tieneWhatsapp,
            Boolean contactoAutorizado,
            Boolean esDemo
    ) { }

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
