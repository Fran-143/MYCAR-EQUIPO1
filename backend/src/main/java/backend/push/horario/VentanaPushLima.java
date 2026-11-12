package backend.push.horario;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * Horarios America/Lima para push ordinarios.
 * <ul>
 *   <li>Despacho programado / drain outbox: 05:00–21:59</li>
 *   <li>Inmediato por km: 05:00–21:59</li>
 *   <li>22:00–04:59: sin envíos ordinarios (esperan siguiente ventana)</li>
 * </ul>
 */
@Component
public class VentanaPushLima {
    public static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final LocalTime INICIO = LocalTime.of(5, 0);
    private static final LocalTime FIN_EXCLUSIVO = LocalTime.of(22, 0);

    private final Clock clock;

    public VentanaPushLima() {
        this.clock = Clock.system(ZONA);
    }

    public static VentanaPushLima conReloj(Clock clock) {
        return new VentanaPushLima(clock);
    }

    private VentanaPushLima(Clock clock) {
        this.clock = clock;
    }

    public LocalDate hoyLima() {
        return LocalDate.now(clock.withZone(ZONA));
    }

    /** Despacho cron + drain de outbox (todas las categorías). */
    public boolean enVentanaDespacho() {
        return dentroVentanaDiurna();
    }

    /** Push inmediato tras RF14. */
    public boolean enVentanaInmediataKm() {
        return dentroVentanaDiurna();
    }

    /**
     * @deprecated usar {@link #enVentanaDespacho()} / {@link #enVentanaInmediataKm()}
     */
    @Deprecated
    public boolean enHorarioPermitido() {
        return dentroVentanaDiurna();
    }

    private boolean dentroVentanaDiurna() {
        LocalTime ahora = LocalTime.now(clock.withZone(ZONA));
        return !ahora.isBefore(INICIO) && ahora.isBefore(FIN_EXCLUSIVO);
    }
}
