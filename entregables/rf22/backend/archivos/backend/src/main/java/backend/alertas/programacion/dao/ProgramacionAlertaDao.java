package backend.alertas.programacion.dao;

import backend.alertas.programacion.model.ProgramacionAlerta.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistencia del agregado RF22. No gestiona sesiones, formularios ni cálculos. */
public interface ProgramacionAlertaDao {
    void exigirCuentaActiva(long usuario);
    List<Vehiculo> vehiculosActivos(long usuario);
    List<Tipo> tiposActivos();
    boolean vehiculoPropioActivo(long usuario,long vehiculo,boolean bloquear);
    Optional<Tipo> tipoActivo(long tipo,boolean bloquear);
    Optional<Lectura> ultimaLectura(long vehiculo);
    List<String> sugerencias(long vehiculo,long tipo);
    Optional<Referencia> ultimoRealizado(long vehiculo,long tipo,String servicioOtro);
    boolean referenciaCompatible(long vehiculo,long tipo,String servicioOtro,long referencia);
    String normalizar(String servicio);
    Optional<Long> equivalente(long vehiculo,String firma);
    long insertar(Revision revision,String firma);
    void historialInicial(long alerta,String estado);
    Optional<EstadoOperacion> consultarOperacion(long usuario,UUID id);
}
