package backend.catalogos.service;

import backend.catalogos.dao.AseguradoraSoatDao;
import backend.catalogos.dao.AseguradoraSoatDao.AseguradoraFila;
import backend.soat.service.CatalogoAseguradorasSoat;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fuente viva de aseguradoras SOAT (DB). Fallback al mapa hardcodeado si la tabla aún no existe
 * o está vacía (tests unitarios sin Flyway V44).
 */
@Service
public class AseguradoraCatalogoService {
    public record Entrada(
            long id,
            String codigo,
            String nombre,
            boolean activo,
            boolean permiteNombreLibre,
            String ayudaPoliza,
            String ejemploPoliza
    ) { }

    private final AseguradoraSoatDao dao;
    private final AtomicReference<Map<String, Entrada>> indice = new AtomicReference<>(Map.of());
    private final AtomicReference<List<Entrada>> activas = new AtomicReference<>(List.of());
    private volatile boolean otraActiva;

    public AseguradoraCatalogoService(AseguradoraSoatDao dao) {
        this.dao = dao;
    }

    @PostConstruct
    public void init() {
        try {
            reload();
        } catch (Exception ignored) {
            // Tabla puede no existir en tests antiguos; Validador usará fallback hardcodeado.
        }
        CatalogoAseguradorasSoat.bind(this);
    }

    public void invalidate() {
        reload();
    }

    public synchronized void reload() {
        List<AseguradoraFila> filas = dao.listarTodas();
        Map<String, Entrada> map = new LinkedHashMap<>();
        List<Entrada> act = new java.util.ArrayList<>();
        boolean otra = false;
        for (AseguradoraFila f : filas) {
            Entrada e = new Entrada(f.id(), f.codigo(), f.nombre(), f.activo(), f.permiteNombreLibre(),
                    f.ayudaPoliza(), f.ejemploPoliza());
            indexar(map, e.codigo(), e);
            indexar(map, e.nombre(), e);
            for (String a : f.aliases()) indexar(map, a, e);
            if (e.activo()) {
                act.add(e);
                if (e.permiteNombreLibre()) otra = true;
            }
        }
        indice.set(Map.copyOf(map));
        activas.set(List.copyOf(act));
        otraActiva = otra;
    }

    public boolean tieneDatosDb() {
        return !indice.get().isEmpty();
    }

    public List<Entrada> listarActivas() {
        if (!tieneDatosDb()) {
            return CatalogoAseguradorasSoat.activasFallback().stream()
                    .map(e -> new Entrada(0, e.codigo(), e.nombre(), true, false,
                            e.perfil() == null ? null : e.perfil().mensajeAyuda(),
                            e.perfil() == null ? null : e.perfil().ejemploIlustrativo()))
                    .toList();
        }
        return activas.get();
    }

    public Entrada resolver(String codigoONombre) {
        String n = normalizar(codigoONombre);
        if (n == null) return null;
        if (tieneDatosDb()) return indice.get().get(n);
        var fb = CatalogoAseguradorasSoat.resolver(codigoONombre);
        if (fb == null) return null;
        return new Entrada(0, fb.codigo(), fb.nombre(), fb.activo(), false,
                fb.perfil() == null ? null : fb.perfil().mensajeAyuda(),
                fb.perfil() == null ? null : fb.perfil().ejemploIlustrativo());
    }

    public boolean esActiva(String codigoONombre) {
        Entrada e = resolver(codigoONombre);
        return e != null && e.activo() && !e.permiteNombreLibre();
    }

    public boolean esReconocida(String codigoONombre) {
        return resolver(codigoONombre) != null;
    }

    public boolean esOtraActiva() {
        if (tieneDatosDb()) return otraActiva;
        return false;
    }

    /**
     * Para alta/renovar: código canónico de activa, o nombre libre si OTRA está activa
     * y el valor no coincide con un catálogo (activo o histórico).
     */
    public String codigoParaAlta(String valor) {
        Entrada e = resolver(valor);
        if (e != null) {
            if (e.permiteNombreLibre()) {
                return null; // el llamador debe enviar el nombre libre, no "OTRA"
            }
            if (!e.activo()) return null;
            return e.codigo();
        }
        if (!esOtraActiva()) return null;
        if (valor == null || valor.isBlank()) return null;
        return valor.strip();
    }

    /** Corregir: activas, históricas o nombre libre (si OTRA activa). */
    public String codigoParaCorregir(String valor) {
        Entrada e = resolver(valor);
        if (e != null) {
            if (e.permiteNombreLibre()) return null;
            return e.codigo();
        }
        if (!esOtraActiva()) return null;
        if (valor == null || valor.isBlank()) return null;
        return valor.strip();
    }

    public CatalogoAseguradorasSoat.PerfilValidacionPoliza perfil(String codigoONombre) {
        Entrada e = resolver(codigoONombre);
        if (e == null) return CatalogoAseguradorasSoat.perfil(codigoONombre);
        return new CatalogoAseguradorasSoat.PerfilValidacionPoliza(
                e.codigo(), e.nombre(),
                e.ayudaPoliza() == null ? "Validación general." : e.ayudaPoliza(),
                e.ejemploPoliza(), "mycar.aseguradora_soat", null, null, null);
    }

    public static String normalizar(String texto) {
        if (texto == null || texto.isBlank()) return null;
        return Normalizer.normalize(texto.strip(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private static void indexar(Map<String, Entrada> map, String clave, Entrada e) {
        String n = normalizar(clave);
        if (n != null) map.putIfAbsent(n, e);
    }
}
