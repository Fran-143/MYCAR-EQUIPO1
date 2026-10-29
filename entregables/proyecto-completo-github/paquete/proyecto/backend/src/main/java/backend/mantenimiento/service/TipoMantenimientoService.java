package backend.mantenimiento.service;

import backend.mantenimiento.dao.TipoMantenimientoDao;
import backend.mantenimiento.model.TipoMantenimiento;
import org.springframework.stereotype.Service;

@Service
public class TipoMantenimientoService {

    private final TipoMantenimientoDao dao;

    public TipoMantenimientoService(TipoMantenimientoDao dao) {
        this.dao = dao;
    }

    public TipoMantenimiento obtenerTipoActivo(String codigo) {
        TipoMantenimiento tipo = dao.buscarPorCodigo(codigo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "codigoTipoMantenimiento: no existe"
                ));

        if (!tipo.activo()) {
            throw new IllegalArgumentException(
                    "codigoTipoMantenimiento: no está activo"
            );
        }

        return tipo;
    }
}