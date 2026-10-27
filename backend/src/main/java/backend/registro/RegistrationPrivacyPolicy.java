package backend.registro;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RegistrationPrivacyPolicy {
    private final String currentVersion;

    public RegistrationPrivacyPolicy(@Value("${mycar.registro.privacidad.version-vigente:}") String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public void requireAcceptedVersion(String acceptedVersion) {
        if (currentVersion == null || currentVersion.isBlank()
                || currentVersion.codePointCount(0, currentVersion.length()) > 40) {
            throw RegistrationException.unavailable();
        }
        if (!currentVersion.equals(acceptedVersion)) {
            throw RegistrationException.invalid(Map.of("versionPrivacidad",
                    "Debes aceptar la versión vigente de la política de privacidad."));
        }
    }
}
