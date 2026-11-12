package backend.alertas.seguridad;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mycar.cron")
public class CronSecretProperties {
    /**
     * Secreto compartido Worker ↔ backend (env {@code MYCAR_CRON_SECRET}).
     * Independiente de JWT de usuarios. Vacío ⇒ endpoint rechaza todo.
     */
    private String secret = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret == null ? "" : secret;
    }
}
