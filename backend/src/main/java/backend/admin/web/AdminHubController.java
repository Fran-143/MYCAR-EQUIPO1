package backend.admin.web;

import org.springframework.beans.factory.annotation.Value;
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

    private final boolean suscripcionesLocal;

    public AdminHubController(
            @Value("${mycar.local-test.enabled:false}") boolean localTest,
            @Value("${mycar.planes.sandbox.enabled:false}") boolean sandboxPlanes) {
        // RF57: Suscripciones habilitadas en local sandbox; en Neon/Render quedan Próximamente
        // hasta que exista migración remota (fuera de alcance de este hotfix).
        this.suscripcionesLocal = localTest || sandboxPlanes;
    }

    @GetMapping(value="/hub",produces="application/json")
    public ResponseEntity<Map<String,Object>> hub(){
        Seccion suscripciones = suscripcionesLocal
                ? new Seccion("SUSCRIPCIONES","Suscripciones",true,"DISPONIBLE")
                : new Seccion("SUSCRIPCIONES","Suscripciones",false,"PROXIMAMENTE");
        List<Seccion> secciones=List.of(
                new Seccion("USUARIOS","Usuarios",true,"DISPONIBLE"),
                suscripciones,
                new Seccion("INCIDENCIAS","Incidencias",true,"DISPONIBLE"),
                new Seccion("CATALOGOS","Catálogos",true,"DISPONIBLE")
        );
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("titulo","Administración","secciones",secciones));
    }
}
