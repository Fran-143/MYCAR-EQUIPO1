package backend.perfil;

import java.time.LocalDate;
import java.util.Optional;

public interface ProfileStore {
    Optional<ProfileData> findActiveProfile(long userId);

    Optional<Credential> lockCredential(long userId);

    int updatePersonalData(long userId, String nombre, String apellido, LocalDate fechaNacimiento);

    int updatePasswordHash(long userId, String passwordHash);

    record Credential(long userId, String passwordHash, String estadoCuenta) { }
}
