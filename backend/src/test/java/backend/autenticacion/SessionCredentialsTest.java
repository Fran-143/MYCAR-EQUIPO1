package backend.autenticacion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import com.nimbusds.jwt.SignedJWT;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class SessionCredentialsTest {
    static String randomKey(){byte[] key=new byte[32];new SecureRandom().nextBytes(key);return Base64.getEncoder().encodeToString(key);}
    @Test void signsAndVerifiesOnlyExpectedIdentity(){
        var c=new SessionCredentials(randomKey(),"PT5M","P1D");
        var installation=UUID.randomUUID();
        var access=c.issue(9,12,installation,Instant.now(),Instant.now().plusSeconds(1000));
        var claims=c.verify(access.value());
        assertEquals(9,claims.userId());assertEquals(12,claims.sessionId());assertEquals(installation,claims.installation());
        assertFalse(access.toString().contains(access.value()));
    }
    @Test void wrongKeyTamperingAndExpiredAccessAreRejected(){
        var c=new SessionCredentials(randomKey(),"PT5M","P1D");
        var access=c.issue(9,12,UUID.randomUUID(),Instant.now(),Instant.now().plusSeconds(1000));
        assertThrows(AuthenticationFailure.class,()->new SessionCredentials(randomKey(),"PT5M","P1D").verify(access.value()));
        assertThrows(AuthenticationFailure.class,()->c.verify("x"+access.value()));
        var expired=c.issue(9,12,UUID.randomUUID(),Instant.now().minusSeconds(600),Instant.now().plusSeconds(1000));
        assertThrows(AuthenticationFailure.class,()->c.verify(expired.value()));
    }
    @Test void accessNeverOutlivesAbsoluteSessionDeadline(){
        var c=new SessionCredentials(randomKey(),"PT5M","P1D");
        Instant deadline=Instant.now().plusSeconds(60);
        assertFalse(c.issue(9,12,UUID.randomUUID(),Instant.now(),deadline).expiresAt().isAfter(deadline));
    }
    @Test void refreshIsRandomAndOnlyItsSha256IsPersistable(){
        var c=new SessionCredentials(randomKey(),"PT5M","P1D");
        String a=c.newRefresh(),b=c.newRefresh();
        assertNotEquals(a,b);assertEquals(32,Base64.getUrlDecoder().decode(a).length);
        assertTrue(SessionCredentials.hashRefresh(a).matches("[a-f0-9]{64}"));
        assertNotEquals(a,SessionCredentials.hashRefresh(a));
    }
    @ParameterizedTest @ValueSource(strings={"","bad-base64","YQ=="})
    void absentOrWeakSigningKeyFailsClosedWithoutInventingSecret(String key){
        assertEquals(AuthenticationFailure.Reason.UNAVAILABLE,
                assertThrows(AuthenticationFailure.class,()->new SessionCredentials(key,"PT5M","P1D").requireConfigured()).reason());
    }
    @ParameterizedTest @ValueSource(strings={"","not-duration","PT0S","PT-1S","PT0.5S","P2D"})
    void invalidOrAbsentDurationsFailClosed(String duration){
        assertThrows(AuthenticationFailure.class,()->new SessionCredentials(randomKey(),duration,"P1D").requireConfigured());
    }

    @Test void emissionTruncatesClaimsAndReportedExpiryToSeconds()throws Exception{
        var c=new SessionCredentials(randomKey(),"PT1S","PT10S");
        Instant issued=Instant.parse("2030-01-01T00:00:00.900Z");
        var access=c.issue(9,12,UUID.randomUUID(),issued,issued.plusSeconds(10));
        var claims=SignedJWT.parse(access.value()).getJWTClaimsSet();
        assertEquals(issued.truncatedTo(ChronoUnit.SECONDS),claims.getIssueTime().toInstant());
        assertEquals(Instant.parse("2030-01-01T00:00:01Z"),access.expiresAt());
        assertEquals(access.expiresAt(),claims.getExpirationTime().toInstant());
    }

    @Test void emissionCapsAndFloorsFractionalAbsoluteDeadline(){
        var c=new SessionCredentials(randomKey(),"PT5M","P1D");
        Instant issued=Instant.parse("2030-01-01T00:00:00.900Z");
        Instant deadline=Instant.parse("2030-01-01T00:00:02.750Z");
        assertEquals(Instant.parse("2030-01-01T00:00:02Z"),c.issue(9,12,UUID.randomUUID(),issued,deadline).expiresAt());
    }

    @ParameterizedTest @ValueSource(strings={"2030-01-01T00:00:00.950Z","2030-01-01T00:00:00.900Z","2030-01-01T00:00:00Z"})
    void emissionRejectsDeadlineWithoutRepresentableFutureExpiry(String value){
        var c=new SessionCredentials(randomKey(),"PT1S","PT10S");
        assertEquals(AuthenticationFailure.Reason.INVALID_SESSION,assertThrows(AuthenticationFailure.class,
                ()->c.issue(9,12,UUID.randomUUID(),Instant.parse("2030-01-01T00:00:00.900Z"),Instant.parse(value))).reason());
    }
}
