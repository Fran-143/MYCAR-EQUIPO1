package backend.talleres.service;

import backend.talleres.dao.TallerDao;
import backend.talleres.model.DatosTaller.*;
import backend.talleres.model.TallerAsociadoNoEditable;
import backend.talleres.model.TallerFavoritoFallido;
import backend.talleres.model.TallerNombreDuplicado;
import backend.talleres.model.TallerNoDisponible;
import backend.talleres.model.TallerTelefonoDuplicado;
import backend.talleres.model.TallerVersionConflicto;
import backend.talleres.model.TallerYaVinculado;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TallerService {
    private final TallerDao dao;
    private final ValidadorTaller validador;
    private final long idUsuarioPilotoDemo;

    public TallerService(
            TallerDao dao,
            ValidadorTaller validador,
            @Value("${mycar.taller-piloto.usuario-id:12}") long idUsuarioPilotoDemo) {
        this.dao = dao;
        this.validador = validador;
        this.idUsuarioPilotoDemo = idUsuarioPilotoDemo;
    }

    @Transactional(readOnly = true)
    public ListaTalleres listar(long idUsuario) {
        return listar(idUsuario, "TODOS");
    }

    @Transactional(readOnly = true)
    public ListaTalleres listar(long idUsuario, String filtroOrigen) {
        exigirUsuario(idUsuario);
        String f = normalizarFiltro(filtroOrigen);
        return new ListaTalleres(dao.listarActivos(idUsuario, f));
    }

    @Transactional(readOnly = true)
    public Taller detalle(long idUsuario, long idTaller) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    @Transactional(readOnly = true)
    public ListaTalleresGlobales listarCatalogoGlobal(long idUsuario, String busqueda) {
        exigirUsuario(idUsuario);
        return new ListaTalleresGlobales(
                dao.listarGlobalesActivos(idUsuario, busqueda, idUsuarioPilotoDemo));
    }

    /** RF33 — registrar taller MANUAL (formulario por defecto). */
    @Transactional
    public Taller registrar(long idUsuario, RegistrarTallerRequest body) {
        exigirUsuario(idUsuario);
        if (body == null) throw new IllegalArgumentException("Solicitud obligatoria");
        String nombre = validador.nombreObligatorio(body.nombre());
        exigirNombreLibre(idUsuario, nombre, 0);
        String telefono = validador.telefonoOpcional(body.telefono());
        boolean wa = validador.whatsappCoherente(telefono, body.tieneWhatsapp());
        if (telefono != null && dao.existeTelefonoManualActivo(idUsuario, telefono, 0)) {
            throw new TallerTelefonoDuplicado();
        }
        try {
            return dao.insertarManual(idUsuario, nombre, telefono,
                    validador.correoOpcional(body.correo()),
                    validador.direccionOpcional(body.direccion()),
                    validador.notasOpcional(body.notas()),
                    wa);
        } catch (DataIntegrityViolationException ex) {
            throw mapIntegridadAlta(ex);
        }
    }

    /** RF33-EA07 — vincular taller ASOCIADO del catálogo global. */
    @Transactional
    public Taller vincularAsociado(long idUsuario, VincularAsociadoRequest body) {
        exigirUsuario(idUsuario);
        if (body == null || body.idTallerGlobal() <= 0) {
            throw new IllegalArgumentException("idTallerGlobal inválido");
        }
        var global = dao.globalActivo(body.idTallerGlobal()).orElseThrow(TallerNoDisponible::new);
        if (global.esDemo() && idUsuario != idUsuarioPilotoDemo) {
            throw new TallerNoDisponible();
        }
        if (dao.existeVinculoAsociadoActivo(idUsuario, global.idTallerGlobal())) {
            throw new TallerYaVinculado();
        }
        try {
            return dao.insertarAsociado(
                    idUsuario, global.idTallerGlobal(), global.nombre(), global.telefono(),
                    global.correo(), global.direccion(), global.tieneWhatsapp());
        } catch (DataIntegrityViolationException ex) {
            if (dao.existeVinculoAsociadoActivo(idUsuario, global.idTallerGlobal())) {
                throw new TallerYaVinculado();
            }
            throw new TallerNoDisponible();
        }
    }

    /** RF34 — solo MANUAL. */
    @Transactional
    public Taller actualizar(long idUsuario, long idTaller, ActualizarTallerRequest body) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        if (body == null) throw new IllegalArgumentException("Solicitud obligatoria");
        var actual = dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
        if ("ASOCIADO".equals(actual.origen())) throw new TallerAsociadoNoEditable();
        if (actual.versionFila() != body.versionFila()) throw new TallerVersionConflicto();
        String nombre = validador.nombreObligatorio(body.nombre());
        exigirNombreLibre(idUsuario, nombre, idTaller);
        String telefono = validador.telefonoOpcional(body.telefono());
        boolean wa = validador.whatsappCoherente(telefono,
                body.tieneWhatsapp() != null ? body.tieneWhatsapp() : actual.tieneWhatsapp());
        if (telefono != null && dao.existeTelefonoManualActivo(idUsuario, telefono, idTaller)) {
            throw new TallerTelefonoDuplicado();
        }
        try {
            if (!dao.actualizarManual(idUsuario, idTaller, body.versionFila(), nombre, telefono,
                    validador.correoOpcional(body.correo()),
                    validador.direccionOpcional(body.direccion()),
                    validador.notasOpcional(body.notas()),
                    wa)) {
                throw new TallerVersionConflicto();
            }
        } catch (DataIntegrityViolationException ex) {
            throw mapIntegridadAlta(ex);
        }
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    /** RF35 — retirar MANUAL o vínculo ASOCIADO (no borra ficha global). */
    @Transactional
    public Taller retirar(long idUsuario, long idTaller, RetirarTallerRequest body) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        if (body == null) throw new IllegalArgumentException("Solicitud obligatoria");
        var actual = dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
        if (actual.versionFila() != body.versionFila()) throw new TallerVersionConflicto();
        if (!dao.retirar(idUsuario, idTaller, body.versionFila())) {
            throw new TallerVersionConflicto();
        }
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    @Transactional
    public Taller marcarFavorito(long idUsuario, long idTaller) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        var actual = dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
        if (actual.favorito()) return actual;
        if (!dao.marcarFavoritoAtomico(idUsuario, idTaller)) {
            throw new TallerFavoritoFallido();
        }
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    @Transactional
    public Taller desmarcarFavorito(long idUsuario, long idTaller) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
        if (!dao.desmarcarFavorito(idUsuario, idTaller)) {
            throw new TallerFavoritoFallido();
        }
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    /** RF17/RF20/RF24: valida taller ACTIVO propio; null = Sin taller. */
    @Transactional(readOnly = true)
    public Taller exigirActivoPropioSiPresente(long idUsuario, Long idTaller) {
        if (idTaller == null) return null;
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        return dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    @Transactional(readOnly = true)
    public OptionalTaller favoritoActivo(long idUsuario) {
        exigirUsuario(idUsuario);
        return new OptionalTaller(dao.favoritoActivo(idUsuario).orElse(null));
    }

    public record OptionalTaller(Taller taller) { }

    private void exigirNombreLibre(long idUsuario, String nombre, long idExcluir) {
        if (dao.existeNombreActivo(idUsuario, nombre, idExcluir)) {
            throw new TallerNombreDuplicado();
        }
    }

    private static String normalizarFiltro(String raw) {
        if (raw == null || raw.isBlank()) return "TODOS";
        String f = raw.trim().toUpperCase();
        if (!f.equals("TODOS") && !f.equals("MANUAL") && !f.equals("ASOCIADO")) {
            throw new IllegalArgumentException("Filtro de origen no válido");
        }
        return f;
    }

    private static RuntimeException mapIntegridadAlta(DataIntegrityViolationException ex) {
        String m = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : "";
        if (m != null && m.contains("uq_taller_telefono_manual_activo")) {
            return new TallerTelefonoDuplicado();
        }
        if (m != null && m.contains("uq_taller_vinculo_asociado_activo")) {
            return new TallerYaVinculado();
        }
        return new TallerNombreDuplicado();
    }

    private static void exigirUsuario(long idUsuario) {
        if (idUsuario <= 0) throw new IllegalArgumentException("Usuario inválido");
    }
}
