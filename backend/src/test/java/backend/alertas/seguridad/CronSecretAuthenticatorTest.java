package backend.alertas.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CronSecretAuthenticatorTest {

    @Test
    void rechazaSecretoVacioConfigurado() {
        var props = new CronSecretProperties();
        props.setSecret("");
        assertThat(new CronSecretAuthenticator(props).acepta("cualquier")).isFalse();
        assertThat(new CronSecretAuthenticator(props).acepta("")).isFalse();
        assertThat(new CronSecretAuthenticator(props).acepta(null)).isFalse();
    }

    @Test
    void aceptaSoloCoincidenciaExacta() {
        var props = new CronSecretProperties();
        props.setSecret("s3creto-de-prueba");
        var auth = new CronSecretAuthenticator(props);
        assertThat(auth.acepta("s3creto-de-prueba")).isTrue();
        assertThat(auth.acepta("s3creto-de-prueba ")).isFalse();
        assertThat(auth.acepta("otro")).isFalse();
    }
}
