package backend.notificaciones.service;

import backend.notificaciones.api.dto.ContratoPreferenciasNotificacion;
import backend.notificaciones.dao.PreferenciaNotificacionJdbcDao;
import backend.notificaciones.model.ErrorNotificaciones;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF48: consultar/guardar preferencias de envío externo.
 * No crea notificaciones, no modifica alertas, no silencia bandeja RF46.
 */
@Service
public class PreferenciaNotificacionService {
    private static final Set<String> PERMISOS = Set.of("NO_SOLICITADO", "CONCEDIDO", "DENEGADO");

    private final PreferenciaNotificacionJdbcDao dao;

    public PreferenciaNotificacionService(PreferenciaNotificacionJdbcDao dao) {
        this.dao = dao;
    }

    @Transactional
    public ContratoPreferenciasNotificacion.PreferenciasResponse consultar(long usuario) {
        dao.asegurarFila(usuario);
        return dao.consultar(usuario).orElseThrow(() -> new ErrorNotificaciones(
                "PREFERENCIAS_NO_DISPONIBLES", "No pudimos cargar tus preferencias."));
    }

    @Transactional
    public ContratoPreferenciasNotificacion.PreferenciasResponse guardar(
            long usuario, ContratoPreferenciasNotificacion.PreferenciasRequest req) {
        if (req == null
                || req.mantenimiento() == null
                || req.soat() == null
                || req.kilometraje() == null) {
            throw new ErrorNotificaciones(
                    "DATOS_INVALIDOS", "Indica mantenimiento, SOAT y kilometraje (ON/OFF).");
        }
        String permiso = normalizarPermiso(req.permisoDispositivo());
        return dao.actualizar(
                usuario, req.mantenimiento(), req.soat(), req.kilometraje(), permiso);
    }

    /** EA04: reconciliar permiso OS sin cambiar categorías. */
    @Transactional
    public ContratoPreferenciasNotificacion.PreferenciasResponse actualizarPermisoDispositivo(
            long usuario, String permisoRaw) {
        String permiso = normalizarPermiso(permisoRaw);
        return dao.actualizarSoloPermiso(usuario, permiso);
    }

    private static String normalizarPermiso(String raw) {
        if (raw == null || raw.isBlank()) {
            return "NO_SOLICITADO";
        }
        String p = raw.trim().toUpperCase(Locale.ROOT);
        if (!PERMISOS.contains(p)) {
            throw new ErrorNotificaciones("DATOS_INVALIDOS", "Permiso de dispositivo no válido.");
        }
        return p;
    }
}
