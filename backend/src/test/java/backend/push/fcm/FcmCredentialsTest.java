package backend.push.fcm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class FcmCredentialsTest {

    @Test
    void resuelveJsonInline() throws Exception {
        byte[] got = FcmCredentials.resolver("{\"type\":\"service_account\"}", "", "");
        assertArrayEquals("{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8), got);
    }

    @Test
    void resuelveBase64ConComillasYEspacios() throws Exception {
        String raw = Base64.getEncoder().encodeToString("abc".getBytes(StandardCharsets.UTF_8));
        byte[] got = FcmCredentials.resolver("", "  \"" + raw + "\"  ", "");
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), got);
    }

    @Test
    void ausenteDevuelveNull() throws Exception {
        assertNull(FcmCredentials.resolver("", "", ""));
        assertFalse(FcmCredentials.presentes("", "", "/no/existe/sa.json"));
        assertTrue(FcmCredentials.presentes("{}", "", ""));
        assertNotNull(FcmCredentials.resolver("{}", "", ""));
    }
}
