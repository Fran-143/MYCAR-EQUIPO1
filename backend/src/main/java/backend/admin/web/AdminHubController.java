package backend.admin.web;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminHubController {
    public record Seccion(String codigo,String nombre,boolean disponible,String estado){ }

    @GetMapping(value="/hub",produces="application/json")
    public ResponseEntity<Map<String,Object>> hub(){
        List<Seccion> secciones=List.of(
                new Seccion("USUARIOS","Usuarios",false,"PROXIMAMENTE"),
                new Seccion("SUSCRIPCIONES","Suscripciones",false,"PROXIMAMENTE"),
                new Seccion("INCIDENCIAS","Incidencias",false,"PROXIMAMENTE"),
                new Seccion("CATALOGOS","Catálogos",false,"PROXIMAMENTE")
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("titulo","Administración","secciones",secciones));
    }
}
