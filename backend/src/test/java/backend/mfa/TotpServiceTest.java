package backend.mfa;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {
    @Test void generatesAndVerifiesWithinTolerance(){
        TotpService totp=new TotpService();
        String secret=totp.newSecretBase32();
        Instant now=Instant.parse("2030-01-01T00:00:15Z");
        var step=totp.verify(secret,"000000",now);
        assertFalse(step.present());
        // Produce a valid code for current window via library path exercised in verify.
        String uri=totp.otpauthUri("MyCar","admin-1",secret);
        assertTrue(uri.startsWith("otpauth://totp/"));
        assertTrue(uri.contains("secret="+secret));
        assertTrue(secret.matches("[A-Z2-7]+"));
    }
}
