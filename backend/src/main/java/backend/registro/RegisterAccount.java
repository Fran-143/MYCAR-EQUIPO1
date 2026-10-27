package backend.registro;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class RegisterAccount {
    private final RegistrationValidator validator;
    private final RegistrationPrivacyPolicy privacyPolicy;
    private final RegistrationStore store;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public RegisterAccount(RegistrationValidator validator, RegistrationPrivacyPolicy privacyPolicy,
            RegistrationStore store, @Qualifier("registrationPasswordEncoder") PasswordEncoder encoder,
            @Qualifier("registrationClock") Clock clock) {
        this.validator = validator;
        this.privacyPolicy = privacyPolicy;
        this.store = store;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Transactional
    public long register(RegistrationCommand command) {
        RegistrationData data = validator.validate(command);
        privacyPolicy.requireAcceptedVersion(data.versionPrivacidad());
        if (store.emailExists(data.correo())) throw RegistrationException.emailUnavailable();
        String hash = encoder.encode(command.contrasena());
        // No session/token/OTP is created. Return is internal, not an HTTP response.
        return store.createAccount(data, hash, clock.instant());
    }
}
