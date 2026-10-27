package backend.mantenimiento.service;

import backend.mantenimiento.api.IntegracionRf17PendienteException;
import backend.mantenimiento.api.dto.ContratoRf17.*;
import backend.mantenimiento.dao.ContextoMantenimientoJdbcDao;
import backend.mantenimiento.integracion.RegistroIdempotenteMantenimiento;
import backend.mantenimiento.model.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
public class MantenimientoHttpService {
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private final ContextoMantenimientoJdbcDao contexto;
    private final VehiculoMantenimientoService vehiculos;
    private final ObjectProvider<RegistroIdempotenteMantenimiento> registros;

    public MantenimientoHttpService(ContextoMantenimientoJdbcDao contexto,
            VehiculoMantenimientoService vehiculos,
            ObjectProvider<RegistroIdempotenteMantenimiento> registros) {
        this.contexto=contexto; this.vehiculos=vehiculos; this.registros=registros;
    }

    public ContextoResponse contexto(long usuario) {
        return new ContextoResponse(LocalDate.now(LIMA), LIMA.getId(),
                contexto.vehiculosActivos(usuario), contexto.tiposActivos(),
                registros.getIfAvailable()!=null);
    }

    public List<LecturaDto> lecturas(long usuario,long vehiculo,LocalDate fecha) {
        if (vehiculo <= 0 || fecha == null || fecha.isAfter(LocalDate.now(LIMA))) {
            throw new IllegalArgumentException("fechaServicio: revisa la fecha y el vehículo");
        }
        vehiculos.obtenerVehiculoPropioActivo(usuario,vehiculo);
        return contexto.lecturas(vehiculo,fecha);
    }

    @Transactional
    public RegistroResponse registrar(long usuario,long vehiculo,RegistroRequest r) {
        if (vehiculo <= 0) throw new IllegalArgumentException("idVehiculo: debe ser positivo");
        var datos=new DatosRegistroMantenimiento(r.codigoTipoMantenimiento(),r.fechaServicio(),
                r.kmServicio(),r.origenKmServicio(),r.idLecturaKmReferencia(),r.costoTotal(),
                r.servicioOtro(),r.proximaFechaSugerida(),r.proximoKmSugerido());
        var repuestos=r.repuestos()==null ? List.<DatosRepuesto>of() : r.repuestos().stream()
                .map(p -> new DatosRepuesto(p.nombre(),p.marca(),p.cantidad(),p.precioUnitario())).toList();
        var solicitud=new SolicitudRegistroMantenimiento(r.idOperacion(),vehiculo,datos,
                r.observaciones(),repuestos);
        var adaptador=exigirAdaptador();
        // El adaptador confirmado debe invocar el servicio existente dentro de esta transacción.
        var resultado=adaptador.registrar(usuario,solicitud);
        if (resultado==null || !Objects.equals(resultado.idOperacion(),r.idOperacion())
                || !Objects.equals(resultado.idVehiculo(),vehiculo)) {
            throw new IllegalStateException("Respuesta de integración inválida");
        }
        return RegistroResponse.de(resultado);
    }

    public EstadoResponse consultar(long usuario,UUID id) {
        var estado=exigirAdaptador().consultar(usuario,id);
        if (estado==null || !Objects.equals(estado.idOperacion(),id)
                || !List.of("CONFIRMADA","EN_PROCESO","NO_ENCONTRADA").contains(estado.estado())
                || ("CONFIRMADA".equals(estado.estado()) && estado.resultado()==null)
                || (estado.resultado()!=null && !Objects.equals(estado.resultado().idOperacion(),id))) {
            throw new IllegalStateException("Estado de integración inválido");
        }
        return new EstadoResponse(id,estado.estado(),estado.resultado()==null ? null
                : RegistroResponse.de(estado.resultado()));
    }

    private RegistroIdempotenteMantenimiento exigirAdaptador() {
        var adaptador=registros.getIfAvailable();
        if (adaptador==null) throw new IntegracionRf17PendienteException();
        return adaptador;
    }
}
