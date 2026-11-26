package backend.planes;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mycar.planes")
public class PlanesProperties {
    private final Sandbox sandbox = new Sandbox();
    private final PruebaGratuita pruebaGratuita = new PruebaGratuita();

    public Sandbox getSandbox() {
        return sandbox;
    }

    public PruebaGratuita getPruebaGratuita() {
        return pruebaGratuita;
    }

    public static class Sandbox {
        private boolean enabled = true;
        /** Duration ISO-8601 para Premium pago sandbox. Vacío = CONFIGURACION_PENDIENTE. */
        private String vigencia = "";
        /**
         * Si true, el pago simulado académico activa Premium en la misma operación
         * (sin esperar aprobación admin). Sigue sin cobro real.
         */
        private boolean activacionInmediata = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVigencia() {
            return vigencia;
        }

        public void setVigencia(String vigencia) {
            this.vigencia = vigencia;
        }

        public boolean isActivacionInmediata() {
            return activacionInmediata;
        }

        public void setActivacionInmediata(boolean activacionInmediata) {
            this.activacionInmediata = activacionInmediata;
        }

        public Duration vigenciaOrThrow() {
            return vigenciaOpcional()
                    .orElseThrow(() -> new backend.planes.model.ErrorPlanes(
                            "CONFIGURACION_PENDIENTE",
                            "mycar.planes.sandbox.vigencia es ILIMITADA; no hay duración fija."));
        }

        /**
         * Vacío / ILIMITADA / NONE → Optional.empty() (sin vencimiento automático).
         * Duration ISO-8601 → Optional.of(duration).
         */
        public Optional<Duration> vigenciaOpcional() {
            if (vigencia == null || vigencia.isBlank()) {
                return Optional.empty();
            }
            String raw = vigencia.strip();
            String upper = raw.toUpperCase(Locale.ROOT);
            if ("ILIMITADA".equals(upper) || "NONE".equals(upper) || "INFINITA".equals(upper)
                    || "0".equals(raw)) {
                return Optional.empty();
            }
            return Optional.of(parseDuration(raw, "mycar.planes.sandbox.vigencia"));
        }

        public boolean esIlimitada() {
            return vigenciaOpcional().isEmpty();
        }
    }

    public static class PruebaGratuita {
        /** false hasta que configures elegibilidad/duración. */
        private boolean enabled = false;
        /** Duration ISO-8601. Vacío = no conceder. */
        private String vigencia = "";
        /**
         * Máximo de pruebas gratuitas APROBADAS por usuario.
         * null/ausente = CONFIGURACION_PENDIENTE (no conceder).
         */
        private Integer maxPorUsuario;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getVigencia() {
            return vigencia;
        }

        public void setVigencia(String vigencia) {
            this.vigencia = vigencia;
        }

        public Integer getMaxPorUsuario() {
            return maxPorUsuario;
        }

        public void setMaxPorUsuario(Integer maxPorUsuario) {
            this.maxPorUsuario = maxPorUsuario;
        }

        public Duration vigenciaOrThrow() {
            return parseDuration(vigencia, "mycar.planes.prueba-gratuita.vigencia");
        }

        public int maxPorUsuarioOrThrow() {
            if (maxPorUsuario == null || maxPorUsuario < 1) {
                throw new backend.planes.model.ErrorPlanes(
                        "CONFIGURACION_PENDIENTE",
                        "Falta mycar.planes.prueba-gratuita.max-por-usuario (>=1). Define MYCAR_PLANES_PRUEBA_MAX.");
            }
            return maxPorUsuario;
        }
    }

    static Duration parseDuration(String raw, String key) {
        if (raw == null || raw.isBlank()) {
            throw new backend.planes.model.ErrorPlanes(
                    "CONFIGURACION_PENDIENTE",
                    "Falta " + key + " (Duration ISO-8601).");
        }
        try {
            Duration d = Duration.parse(raw.strip());
            if (d.isZero() || d.isNegative()) {
                throw new IllegalArgumentException("no positiva");
            }
            return d;
        } catch (backend.planes.model.ErrorPlanes e) {
            throw e;
        } catch (Exception e) {
            throw new backend.planes.model.ErrorPlanes(
                    "CONFIGURACION_PENDIENTE",
                    key + " inválida (usa Duration ISO-8601, ej. PT72H).");
        }
    }
}
