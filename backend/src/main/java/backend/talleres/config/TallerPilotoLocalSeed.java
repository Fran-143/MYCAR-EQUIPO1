package backend.talleres.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Upsert local del taller piloto ASOCIADO/demo. El teléfono NO va en Git:
 * solo {@code MYCAR_TALLER_PILOTO_WHATSAPP} / {@code mycar.taller-piloto.whatsapp}.
 * Requiere {@code MYCAR_TALLER_PILOTO_USUARIO_ID} &gt; 0 y WhatsApp PE válido.
 * {@code contacto_autorizado} solo si se configura explícitamente (default false).
 * Nunca habilitar contra Neon producción sin autorización.
 */
@Component
@ConditionalOnProperty(prefix = "mycar.taller-piloto", name = "habilitado", havingValue = "true")
public class TallerPilotoLocalSeed implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TallerPilotoLocalSeed.class);

    private final JdbcTemplate jdbc;
    private final String nombre;
    private final String whatsappRaw;
    private final long usuarioId;
    private final boolean contactoAutorizado;

    public TallerPilotoLocalSeed(
            JdbcTemplate jdbc,
            @Value("${mycar.taller-piloto.nombre:Taller Piloto MyCar}") String nombre,
            @Value("${mycar.taller-piloto.whatsapp:}") String whatsappRaw,
            @Value("${mycar.taller-piloto.usuario-id:0}") long usuarioId,
            @Value("${mycar.taller-piloto.contacto-autorizado:false}") boolean contactoAutorizado) {
        this.jdbc = jdbc;
        this.nombre = nombre;
        this.whatsappRaw = whatsappRaw;
        this.usuarioId = usuarioId;
        this.contactoAutorizado = contactoAutorizado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (usuarioId <= 0) {
            throw new IllegalStateException(
                    "mycar.taller-piloto.habilitado=true requiere MYCAR_TALLER_PILOTO_USUARIO_ID > 0");
        }
        String e164 = normalizarPeru(whatsappRaw);
        if (e164 == null) {
            throw new IllegalStateException(
                    "mycar.taller-piloto.habilitado=true requiere MYCAR_TALLER_PILOTO_WHATSAPP válido (PE 9 dígitos / 51…)");
        }
        boolean autorizar = contactoAutorizado;
        if (autorizar) {
            // Constraint: contacto_autorizado implica tiene_whatsapp + teléfono (ya garantizado por e164).
            log.info("Taller piloto: contacto_autorizado=true (prueba autorizada explícita)");
        }
        Integer exists = jdbc.queryForObject("""
                SELECT count(*)::int FROM mycar.taller_global
                WHERE lower(btrim(nombre)) = lower(btrim(?)) AND estado = 'ACTIVO'
                """, Integer.class, nombre);
        if (exists != null && exists > 0) {
            jdbc.update("""
                    UPDATE mycar.taller_global
                    SET telefono = ?, tiene_whatsapp = true, es_demo = true,
                        contacto_autorizado = ?, estado = 'ACTIVO'
                    WHERE lower(btrim(nombre)) = lower(btrim(?)) AND estado = 'ACTIVO'
                    """, e164, autorizar, nombre);
            log.info("Taller piloto actualizado (es_demo) visible para usuario-id={}", usuarioId);
            return;
        }
        jdbc.update("""
                INSERT INTO mycar.taller_global
                    (nombre, telefono, correo, direccion, tiene_whatsapp, contacto_autorizado, es_demo, estado)
                VALUES (?, ?, NULL, 'Catálogo demo — no es un negocio asociado real', true, ?, true, 'ACTIVO')
                """, nombre, e164, autorizar);
        log.info("Taller piloto creado (es_demo) visible para usuario-id={}", usuarioId);
    }

    static String normalizarPeru(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String d = raw.replaceAll("\\D", "");
        if (d.length() == 9 && d.startsWith("9")) return "51" + d;
        if (d.length() == 11 && d.startsWith("51") && d.charAt(2) == '9') return d;
        if (d.length() == 10 && d.startsWith("09")) return "51" + d.substring(1);
        return null;
    }
}
