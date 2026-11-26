package backend.catalogos.service;

import backend.catalogos.CatalogoException;
import backend.catalogos.dao.CatalogoAuditoriaDao;
import backend.catalogos.dao.TallerGlobalAdminDao;
import backend.catalogos.dao.TallerGlobalAdminDao.TallerFila;
import backend.talleres.service.ValidadorTaller;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class TallerGlobalAdminService {
    private final TallerGlobalAdminDao dao;
    private final CatalogoAuditoriaDao auditoria;
    private final ValidadorTaller validador;

    public TallerGlobalAdminService(TallerGlobalAdminDao dao, CatalogoAuditoriaDao auditoria,
            ValidadorTaller validador) {
        this.dao = dao;
        this.auditoria = auditoria;
        this.validador = validador;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(String q, String estado) {
        var items = dao.listar(q, estado).stream().map(dao::toMap).toList();
        return Map.of("items", items, "total", items.size());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detalle(long id) {
        return dao.toMap(dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Taller global no encontrado.")));
    }

    @Transactional
    public Map<String, Object> crear(long idAdmin, Map<String, Object> body) {
        Campos c = parseCampos(body, true);
        if (dao.existeNombreActivoNorm(c.nombre(), null)) {
            throw CatalogoException.conflict("Ya existe un taller activo con ese nombre.");
        }
        try {
            TallerFila fila = dao.insertar(c.nombre(), c.telefono(), c.correo(), c.direccion(),
                    c.tieneWhatsapp(), c.contactoAutorizado(), c.notasPublicas(), idAdmin);
            auditoria.registrar("TALLER_GLOBAL", fila.id(), "CREAR", null, fila.nombre(), idAdmin, "OK");
            return dao.toMap(fila);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogoException.conflict("No se pudo crear el taller (posible duplicado).");
        } catch (IllegalArgumentException ex) {
            throw CatalogoException.validation(ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> editar(long idAdmin, long id, Map<String, Object> body) {
        TallerFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Taller global no encontrado."));
        Campos c = parseCampos(body, true);
        if (dao.existeNombreActivoNorm(c.nombre(), id)) {
            throw CatalogoException.conflict("Ya existe un taller activo con ese nombre.");
        }
        String antes = actual.nombre();
        try {
            TallerFila fila = dao.actualizar(id, c.nombre(), c.telefono(), c.correo(), c.direccion(),
                    c.tieneWhatsapp(), c.contactoAutorizado(), c.notasPublicas(), idAdmin);
            auditoria.registrar("TALLER_GLOBAL", id, "EDITAR", antes, fila.nombre(), idAdmin, "OK");
            return dao.toMap(fila);
        } catch (IllegalArgumentException ex) {
            throw CatalogoException.validation(ex.getMessage());
        }
    }

    @Transactional
    public Map<String, Object> desactivar(long idAdmin, long id) {
        TallerFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Taller global no encontrado."));
        if ("INACTIVO".equalsIgnoreCase(actual.estado())) {
            throw CatalogoException.estadoInvalido("El taller ya está inactivo.");
        }
        TallerFila fila = dao.setEstado(id, "INACTIVO", idAdmin);
        auditoria.registrar("TALLER_GLOBAL", id, "DESACTIVAR", "ACTIVO", "INACTIVO", idAdmin, "OK");
        return dao.toMap(fila);
    }

    @Transactional
    public Map<String, Object> reactivar(long idAdmin, long id) {
        TallerFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Taller global no encontrado."));
        if ("ACTIVO".equalsIgnoreCase(actual.estado())) {
            throw CatalogoException.estadoInvalido("El taller ya está activo.");
        }
        if (dao.existeNombreActivoNorm(actual.nombre(), id)) {
            throw CatalogoException.conflict("Ya existe un taller activo con ese nombre.");
        }
        TallerFila fila = dao.setEstado(id, "ACTIVO", idAdmin);
        auditoria.registrar("TALLER_GLOBAL", id, "REACTIVAR", "INACTIVO", "ACTIVO", idAdmin, "OK");
        return dao.toMap(fila);
    }

    private Campos parseCampos(Map<String, Object> body, boolean nombreObligatorio) {
        if (body == null) throw CatalogoException.validation("Solicitud vacía.");
        String nombre = str(body.get("nombre"));
        Boolean waFlag = body.get("tieneWhatsapp") instanceof Boolean b ? b : null;
        Boolean authFlag = body.get("contactoAutorizado") instanceof Boolean b ? b : null;
        try {
            String nom = nombreObligatorio ? validador.nombreObligatorio(nombre) : nombre;
            String tel = validador.telefonoOpcional(str(body.get("telefono")));
            boolean wa = validador.whatsappCoherente(tel, waFlag);
            boolean autorizado = Boolean.TRUE.equals(authFlag);
            if (autorizado && (tel == null || !wa)) {
                throw new IllegalArgumentException(
                        "contactoAutorizado requiere teléfono válido y tieneWhatsapp=true");
            }
            String notas = str(body.get("notasPublicas"));
            if (notas != null && notas.codePointCount(0, notas.length()) > 500) {
                throw new IllegalArgumentException("notasPublicas demasiado largo.");
            }
            return new Campos(
                    nom,
                    tel,
                    validador.correoOpcional(str(body.get("correo"))),
                    validador.direccionOpcional(str(body.get("direccion"))),
                    wa,
                    autorizado,
                    notas == null || notas.isBlank() ? null : notas.strip()
            );
        } catch (IllegalArgumentException ex) {
            throw CatalogoException.validation(ex.getMessage());
        }
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private record Campos(
            String nombre, String telefono, String correo, String direccion,
            boolean tieneWhatsapp, boolean contactoAutorizado, String notasPublicas
    ) { }
}
