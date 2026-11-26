package backend.catalogos.service;

import backend.catalogos.CatalogoException;
import backend.catalogos.dao.AseguradoraSoatDao;
import backend.catalogos.dao.AseguradoraSoatDao.AseguradoraFila;
import backend.catalogos.dao.CatalogoAuditoriaDao;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class AseguradoraSoatAdminService {
    private static final Pattern NO_ALNUM = Pattern.compile("[^A-Z0-9 ]+");
    private final AseguradoraSoatDao dao;
    private final CatalogoAuditoriaDao auditoria;
    private final AseguradoraCatalogoService catalogo;

    public AseguradoraSoatAdminService(AseguradoraSoatDao dao, CatalogoAuditoriaDao auditoria,
            AseguradoraCatalogoService catalogo) {
        this.dao = dao;
        this.auditoria = auditoria;
        this.catalogo = catalogo;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> listar(String q, String estado) {
        var items = dao.listarAdmin(q, estado).stream().map(dao::toMap).toList();
        return Map.of("items", items, "total", items.size());
    }

    @Transactional
    public Map<String, Object> crear(long idAdmin, String nombreRaw) {
        String nombre = exigirNombre(nombreRaw);
        if (dao.existeNombreNorm(nombre, null)) {
            throw CatalogoException.conflict("Ya existe una aseguradora con ese nombre.");
        }
        String codigo = generarCodigo(nombre);
        if (dao.existeCodigo(codigo, null)) {
            codigo = codigo + " " + (System.currentTimeMillis() % 100000);
        }
        try {
            AseguradoraFila fila = dao.insertar(codigo, nombre, idAdmin);
            catalogo.invalidate();
            auditoria.registrar("ASEGURADORA_SOAT", fila.id(), "CREAR", null,
                    fila.codigo() + "|" + fila.nombre(), idAdmin, "OK");
            return dao.toMap(fila);
        } catch (DataIntegrityViolationException ex) {
            throw CatalogoException.conflict("Ya existe una aseguradora con ese nombre o código.");
        }
    }

    @Transactional
    public Map<String, Object> editar(long idAdmin, long id, String nombreRaw) {
        AseguradoraFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Aseguradora no encontrada."));
        if (actual.permiteNombreLibre() || "OTRA".equalsIgnoreCase(actual.codigo())) {
            throw CatalogoException.estadoInvalido("La opción OTRA no se puede editar.");
        }
        String nombre = exigirNombre(nombreRaw);
        if (dao.existeNombreNorm(nombre, id)) {
            throw CatalogoException.conflict("Ya existe una aseguradora con ese nombre.");
        }
        String antes = actual.nombre();
        AseguradoraFila fila = dao.actualizarNombre(id, nombre, idAdmin);
        catalogo.invalidate();
        auditoria.registrar("ASEGURADORA_SOAT", id, "EDITAR", antes, fila.nombre(), idAdmin, "OK");
        return dao.toMap(fila);
    }

    @Transactional
    public Map<String, Object> desactivar(long idAdmin, long id) {
        AseguradoraFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Aseguradora no encontrada."));
        if (actual.permiteNombreLibre() || "OTRA".equalsIgnoreCase(actual.codigo())) {
            throw CatalogoException.estadoInvalido("La opción OTRA no se puede desactivar.");
        }
        if (!actual.activo()) {
            throw CatalogoException.estadoInvalido("La aseguradora ya está inactiva.");
        }
        AseguradoraFila fila = dao.setActivo(id, false, idAdmin);
        catalogo.invalidate();
        auditoria.registrar("ASEGURADORA_SOAT", id, "DESACTIVAR", "ACTIVO", "INACTIVO", idAdmin, "OK");
        return dao.toMap(fila);
    }

    @Transactional
    public Map<String, Object> reactivar(long idAdmin, long id) {
        AseguradoraFila actual = dao.porId(id)
                .orElseThrow(() -> CatalogoException.notFound("Aseguradora no encontrada."));
        if (actual.activo()) {
            throw CatalogoException.estadoInvalido("La aseguradora ya está activa.");
        }
        AseguradoraFila fila = dao.setActivo(id, true, idAdmin);
        catalogo.invalidate();
        auditoria.registrar("ASEGURADORA_SOAT", id, "REACTIVAR", "INACTIVO", "ACTIVO", idAdmin, "OK");
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
                .replaceAll("\\s+", " ");
        base = NO_ALNUM.matcher(base).replaceAll("").strip();
        if (base.isBlank()) base = "ASEGURADORA";
        if (base.length() > 80) base = base.substring(0, 80);
        return base;
    }
}
