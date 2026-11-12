package backend.talleres.service;

import backend.talleres.dao.TallerDao;
import backend.talleres.model.DatosTaller.*;
import backend.talleres.model.TallerFavoritoFallido;
import backend.talleres.model.TallerNombreDuplicado;
import backend.talleres.model.TallerNoDisponible;
import backend.talleres.model.TallerVersionConflicto;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TallerService {
    private final TallerDao dao;
    private final ValidadorTaller validador;

    public TallerService(TallerDao dao, ValidadorTaller validador) {
        this.dao = dao;
        this.validador = validador;
    }

    @Transactional(readOnly = true)
    public ListaTalleres listar(long idUsuario) {
        exigirUsuario(idUsuario);
        return new ListaTalleres(dao.listarActivos(idUsuario));
    }

    @Transactional(readOnly = true)
    public Taller detalle(long idUsuario, long idTaller) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    /** RF34 */
    @Transactional
    public Taller registrar(long idUsuario, RegistrarTallerRequest body) {
        exigirUsuario(idUsuario);
        if (body == null) throw new IllegalArgumentException("Solicitud obligatoria");
        String nombre = validador.nombreObligatorio(body.nombre());
        exigirNombreLibre(idUsuario, nombre, 0);
        try {
            return dao.insertar(idUsuario, nombre,
                    validador.telefonoOpcional(body.telefono()),
                    validador.correoOpcional(body.correo()),
                    validador.direccionOpcional(body.direccion()),
                    validador.notasOpcional(body.notas()));
        } catch (DataIntegrityViolationException ex) {
            throw new TallerNombreDuplicado();
        }
    }

    /** RF35 */
    @Transactional
    public Taller actualizar(long idUsuario, long idTaller, ActualizarTallerRequest body) {
        exigirUsuario(idUsuario);
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        if (body == null) throw new IllegalArgumentException("Solicitud obligatoria");
        var actual = dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
        if (actual.versionFila() != body.versionFila()) throw new TallerVersionConflicto();
        String nombre = validador.nombreObligatorio(body.nombre());
        exigirNombreLibre(idUsuario, nombre, idTaller);
        try {
            if (!dao.actualizar(idUsuario, idTaller, body.versionFila(), nombre,
                    validador.telefonoOpcional(body.telefono()),
                    validador.correoOpcional(body.correo()),
                    validador.direccionOpcional(body.direccion()),
                    validador.notasOpcional(body.notas()))) {
                throw new TallerVersionConflicto();
            }
        } catch (DataIntegrityViolationException ex) {
            throw new TallerNombreDuplicado();
        }
        return dao.propio(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    /** RF36 */
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

    /** RF37 marcar; RF37-EA02: si ya es favorito, no-op. */
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

    /** RF37 desmarcar */
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

    /** RF17/RF20: valida taller ACTIVO propio; null = Sin taller. */
    @Transactional(readOnly = true)
    public Taller exigirActivoPropioSiPresente(long idUsuario, Long idTaller) {
        if (idTaller == null) return null;
        if (idTaller <= 0) throw new IllegalArgumentException("idTaller inválido");
        return dao.propioActivo(idUsuario, idTaller).orElseThrow(TallerNoDisponible::new);
    }

    private void exigirNombreLibre(long idUsuario, String nombre, long idExcluir) {
        if (dao.existeNombreActivo(idUsuario, nombre, idExcluir)) {
            throw new TallerNombreDuplicado();
        }
    }

    private void exigirUsuario(long idUsuario) {
        if (idUsuario <= 0) throw new IllegalArgumentException("Se requiere identidad autenticada");
    }
}
