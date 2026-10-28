package backend.alertas.programacion.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Modelos propios de RF22; usuario, vehículo, sesión y lecturas permanecen compartidos. */
public final class ProgramacionAlerta {
    private ProgramacionAlerta() { }
    public enum Criterio { POR_TIEMPO, POR_KILOMETRAJE, AMBOS }
    public enum Unidad { SEMANAS, MESES }
    public record Configuracion(
        @JsonDeserialize(using=EnterosRf22.Largo.class) Long idVehiculo,
        @JsonDeserialize(using=EnterosRf22.Largo.class) Long idTipoMantenimiento,
        String servicioOtro, Criterio criterio, Unidad unidadTiempo,
        @JsonDeserialize(using=EnterosRf22.Entero.class) Integer cantidadTiempo,
        @JsonDeserialize(using=EnterosRf22.Entero.class) Integer intervaloKm,
        @JsonDeserialize(using=EnterosRf22.Largo.class) Long idMantenimientoReferencia) { }
    public record Vehiculo(long idVehiculo, String placa, String marca, String modelo, boolean principal,
                           String aceite) { }
    public record Tipo(long idTipoMantenimiento, String codigo, String nombre) { }
    public record Lectura(long idLectura, int km) { }
    public record Referencia(long idMantenimiento, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaServicio,
                             int kmServicio, String servicioOtro) { }
    public record Contexto(String claveCuenta, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaServidor,
                            List<Vehiculo> vehiculos, List<Tipo> tipos) { }
    public record Antecedentes(@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaServidor,
                                Long idLectura, Integer kmActual, List<String> sugerencias, Referencia referencia) { }
    public record Bases(@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaBase, Long idLectura, Integer kmBase) { }
    public record Objetivos(@JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaObjetivo,
                             Integer kmObjetivo, String estado) { }
    public record Revision(Configuracion configuracion, Bases bases, Objetivos objetivos, Referencia referencia) { }
    public record Solicitud(UUID idOperacion, Revision revision) { }
    public record Resultado(UUID idOperacion, long idAlerta, long idVehiculo, String servicio,
        Criterio criterio, @JsonFormat(shape=JsonFormat.Shape.STRING) LocalDate fechaObjetivo,
        Integer kmObjetivo, String estadoInicial, boolean eventoCreacionRegistrado) { }
    public record EstadoOperacion(UUID idOperacion, String estado, Resultado resultado) { }
}
