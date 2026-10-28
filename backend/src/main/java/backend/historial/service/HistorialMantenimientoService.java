package backend.historial.service;

import backend.historial.dao.HistorialMantenimientoDao;
import backend.historial.model.DatosHistorial.*;
import backend.historial.model.HistorialInconsistente;
import backend.historial.model.HistorialNoDisponible;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class HistorialMantenimientoService {
    private final HistorialMantenimientoDao dao;
    public HistorialMantenimientoService(HistorialMantenimientoDao dao) { this.dao = dao; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Contexto contexto(long usuario) {
        exigirUsuario(usuario);
        var vehiculos = dao.vehiculosPropios(usuario);
        vehiculos.forEach(this::validarVehiculo);
        if (vehiculos.stream().filter(Vehiculo::principal).count() > 1
                || vehiculos.stream().map(Vehiculo::idVehiculo).distinct().count() != vehiculos.size()) {
            throw new HistorialInconsistente();
        }
        return new Contexto(vehiculos);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Resultado consultar(long usuario, long idVehiculo) {
        exigirUsuario(usuario);
        if (idVehiculo <= 0) throw new IllegalArgumentException("El vehículo debe ser un identificador positivo");
        var vehiculo = dao.vehiculoPropio(usuario, idVehiculo).orElseThrow(HistorialNoDisponible::new);
        validarVehiculo(vehiculo);
        if (vehiculo.idVehiculo() != idVehiculo) throw new HistorialInconsistente();
        var registros = dao.mantenimientosPropios(usuario, idVehiculo);
        registros.forEach(this::validarRegistro);
        if (registros.stream().map(Mantenimiento::idMantenimiento).distinct().count() != registros.size()) {
            throw new HistorialInconsistente();
        }
        for (int i = 1; i < registros.size(); i++) {
            var anterior = registros.get(i - 1);
            var siguiente = registros.get(i);
            int fechas = anterior.fechaServicio().compareTo(siguiente.fechaServicio());
            if (fechas < 0 || (fechas == 0 && anterior.idMantenimiento() < siguiente.idMantenimiento())) {
                throw new HistorialInconsistente();
            }
        }
        // Contador y tarjetas pertenecen al mismo resultado/snapshot de lectura.
        return new Resultado(vehiculo, registros.size(), registros);
    }

    private void exigirUsuario(long usuario) {
        if (usuario <= 0) throw new IllegalArgumentException("Se requiere una identidad autenticada válida");
    }

    private void validarVehiculo(Vehiculo v) {
        if (v == null || v.idVehiculo() <= 0 || (!"ACTIVO".equals(v.estado()) && !"INACTIVO".equals(v.estado()))
                || v.placa() == null || v.placa().isBlank() || v.marca() == null || v.modelo() == null) {
            throw new HistorialInconsistente();
        }
    }

    private void validarRegistro(Mantenimiento m) {
        if (m == null || m.idMantenimiento() <= 0 || m.fechaServicio() == null
                || m.kmServicio() == null || m.kmServicio() <= 0
                || m.codigoTipo() == null || m.codigoTipo().isBlank() || m.tipo() == null || m.tipo().isBlank()
                || (!"REALIZADO".equals(m.estado()) && !"ANULADO".equals(m.estado()))
                || (m.costoTotal() != null && m.costoTotal().signum() < 0)
                || ("OTRO".equals(m.codigoTipo()) && (m.servicioOtro() == null || m.servicioOtro().isBlank()))) {
            throw new HistorialInconsistente();
        }
    }
}
