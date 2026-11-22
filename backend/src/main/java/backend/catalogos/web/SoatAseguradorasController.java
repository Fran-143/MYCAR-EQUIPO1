package backend.catalogos.web;

import backend.catalogos.dao.AseguradoraSoatDao;
import backend.catalogos.service.AseguradoraCatalogoService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/soat")
public class SoatAseguradorasController {
    private final AseguradoraCatalogoService catalogo;
    private final AseguradoraSoatDao dao;

    public SoatAseguradorasController(AseguradoraCatalogoService catalogo, AseguradoraSoatDao dao) {
        this.catalogo = catalogo;
        this.dao = dao;
    }

    @GetMapping(path = "/aseguradoras", produces = "application/json")
    public ResponseEntity<Map<String, Object>> listarActivas() {
        List<Map<String, Object>> items;
        if (catalogo.tieneDatosDb()) {
            items = dao.listarActivasUsuario().stream().map(dao::toMapUsuario).toList();
        } else {
            items = catalogo.listarActivas().stream().map(e -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.id());
                m.put("codigo", e.codigo());
                m.put("nombre", e.nombre());
                m.put("permiteNombreLibre", e.permiteNombreLibre());
                m.put("ayudaPoliza", e.ayudaPoliza() == null ? "" : e.ayudaPoliza());
                m.put("ejemploPoliza", e.ejemploPoliza() == null ? "" : e.ejemploPoliza());
                return m;
            }).toList();
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("items", items, "total", items.size()));
    }
}
