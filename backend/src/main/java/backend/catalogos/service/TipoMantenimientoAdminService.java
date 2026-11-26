package backend.catalogos.service;

import backend.catalogos.CatalogoException;
import backend.catalogos.dao.CatalogoAuditoriaDao;
import backend.catalogos.dao.TipoMantenimientoAdminDao;
import backend.catalogos.dao.TipoMantenimientoAdminDao.TipoFila;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class TipoMantenimientoAdminService {
    private static final Pattern NO_ALNUM = Pattern.compile("[^A-Z0-9_]+");
    private final TipoMantenimientoAdminDao dao;
    private final CatalogoAuditoriaDao auditoria;

    public TipoMantenimientoAdminService(TipoMantenimientoAdminDao dao, CatalogoAuditoriaDao auditoria) {
        this.dao = dao;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(String q, String estado) {
        var items = dao.listar(q, estado).stream().map(dao::toMap).toList();
        return Map.of("items", items, "total", items.size());
    }

    @Transactional
    public Map<String, Object> crear(long idAdmin, String nombreRaw) {
        String nombre = exigirNombre(nombreRaw);
        if (dao.existeNombreNorm(nombre, null)) {
            throw CatalogoException.conflict("Ya existe un tipo con ese nombre.");
        }
        String codigo = generarCodigo(nombre);
        if (dao.existeCodigo(codigo)) {
            codigo = codigo + "_" + System.currentTimeMillis() % 100000;
        }
        try {
            TipoFila fila = dao.insertar(codigo, nombre, idAdmin);
            auditoria.registrar("TIPO_MANTENIMIENTO", fila.id(), "CREAR", null,
                    fila.codigo() + "|" + fila.nombre(), idAdmin, "OK");
            return dao.toMap(fila);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogoException.conflict("Ya existe un tipo con ese nombre o código.");
        }
    }

    @Transactional
    public Map<String, Object> editar(long idAdmin, long id, String nombreRaw) {
        TipoFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Tipo de mantenimiento no encontrado."));
        if ("OTRO".equalsIgnoreCase(actual.codigo())) {
            throw CatalogoException.estadoInvalido("El tipo OTRO no se puede editar.");
        }
        String nombre = exigirNombre(nombreRaw);
        if (dao.existeNombreNorm(nombre, id)) {
            throw CatalogoException.conflict("Ya existe un tipo con ese nombre.");
        }
        String antes = actual.nombre();
        TipoFila fila = dao.actualizarNombre(id, nombre, idAdmin);
        auditoria.registrar("TIPO_MANTENIMIENTO", id, "EDITAR", antes, fila.nombre(), idAdmin, "OK");
        return dao.toMap(fila);
    }

    @Transactional
    public Map<String, Object> desactivar(long idAdmin, long id) {
        TipoFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Tipo de mantenimiento no encontrado."));
        if ("OTRO".equalsIgnoreCase(actual.codigo())) {
            throw CatalogoException.estadoInvalido("El tipo OTRO no se puede desactivar.");
        }
        if (!actual.activo()) {
            throw CatalogoException.estadoInvalido("El tipo ya está inactivo.");
        }
        TipoFila fila = dao.setActivo(id, false, idAdmin);
        auditoria.registrar("TIPO_MANTENIMIENTO", id, "DESACTIVAR", "ACTIVO", "INACTIVO", idAdmin, "OK");
        return dao.toMap(fila);
    }

    @Transactional
    public Map<String, Object> reactivar(long idAdmin, long id) {
        TipoFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Tipo de mantenimiento no encontrado."));
        if (actual.activo()) {
            throw CatalogoException.estadoInvalido("El tipo ya está activo.");
        }
        TipoFila fila = dao.setActivo(id, true, idAdmin);
        auditoria.registrar("TIPO_MANTENIMIENTO", id, "REACTIVAR", "INACTIVO", "ACTIVO", idAdmin, "OK");
        return dao.toMap(fila);
    }

    private static String exigirNombre(String raw) {
        if (raw == null || raw.isBlank()) {
            throw CatalogoException.validation(Map.of("nombre", "Nombre obligatorio."));
        }
        String n = raw.strip().replaceAll("\\s+", " ");
        if (n.codePointCount(0, n.length()) < 2 || n.codePointCount(0, n.length()) > 120) {
            throw CatalogoException.validation(Map.of("nombre", "Nombre entre 2 y 120 caracteres."));
        }
        return n;
    }

    static String generarCodigo(String nombre) {
        String base = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_');
        base = NO_ALNUM.matcher(base).replaceAll("");
        if (base.isBlank()) base = "TIPO";
        if (base.length() > 50) base = base.substring(0, 50);
        return base;
    }
}
