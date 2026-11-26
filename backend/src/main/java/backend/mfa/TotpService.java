package backend.mfa;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import org.springframework.stereotype.Service;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidKeyException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
@Service
public class TotpService {
    private static final Duration PERIOD=Duration.ofSeconds(30);
    private final TimeBasedOneTimePasswordGenerator totp;
    private final SecureRandom random=new SecureRandom();

    public TotpService(){
        this.totp=new TimeBasedOneTimePasswordGenerator(PERIOD,6);
    }

    public String newSecretBase32(){
        try{
            KeyGenerator keyGen=KeyGenerator.getInstance(totp.getAlgorithm());
            keyGen.init(160,random);
            return base32Encode(keyGen.generateKey().getEncoded());
        }catch(java.security.NoSuchAlgorithmException ex){
            throw new IllegalStateException("Generador TOTP no disponible",ex);
        }
    }

    public String otpauthUri(String issuer,String account,String secretBase32){
        String label=url(issuer)+":"+url(account);
        return "otpauth://totp/"+label
                +"?secret="+secretBase32
                +"&issuer="+url(issuer)
                +"&algorithm=SHA1&digits=6&period=30";
    }

    /** Valida con tolerancia ±1 paso. Devuelve el paso aceptado o empty. */
    public OptionalStep verify(String secretBase32,String code,Instant now){
        if(code==null || !code.matches("\\d{6}"))return OptionalStep.none();
        SecretKey key=new SecretKeySpec(base32Decode(secretBase32),totp.getAlgorithm());
        long currentStep=now.getEpochSecond()/PERIOD.getSeconds();
        for(long delta=-1;delta<=1;delta++){
            long step=currentStep+delta;
            Instant at=Instant.ofEpochSecond(step*PERIOD.getSeconds());
            try{
                String expected=String.format("%06d",totp.generateOneTimePassword(key,at));
                if(MessageDigestCompat.constantEquals(expected,code))return OptionalStep.of(step);
            }catch(InvalidKeyException ex){
                throw MfaFailure.unavailable();
            }
        }
        return OptionalStep.none();
    }

    public record OptionalStep(boolean present,long step){
        static OptionalStep none(){return new OptionalStep(false,0);}
        static OptionalStep of(long step){return new OptionalStep(true,step);}
    }

    private static String url(String v){
        return java.net.URLEncoder.encode(v,java.nio.charset.StandardCharsets.UTF_8).replace("+","%20");
    }
    private static String base32Encode(byte[] data){
        final String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        StringBuilder out=new StringBuilder((data.length*8+4)/5);
        int buffer=0,bitsLeft=0;
        for(byte b:data){
            buffer=(buffer<<8)|(b&0xff);
            bitsLeft+=8;
            while(bitsLeft>=5){
                out.append(alphabet.charAt((buffer>>(bitsLeft-5))&31));
                bitsLeft-=5;
            }
        }
        if(bitsLeft>0)out.append(alphabet.charAt((buffer<<(5-bitsLeft))&31));
        return out.toString();
    }
    private static byte[] base32Decode(String encoded){
        String s=encoded.trim().toUpperCase().replace("=","");
        final String alphabet="ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        byte[] out=new byte[s.length()*5/8];
        int buffer=0,bitsLeft=0,index=0;
        for(int i=0;i<s.length();i++){
            int val=alphabet.indexOf(s.charAt(i));
            if(val<0)throw MfaFailure.invalid(java.util.Map.of("secreto","Secreto TOTP inválido."));
            buffer=(buffer<<5)|val;
            bitsLeft+=5;
            if(bitsLeft>=8){
                out[index++]=(byte)((buffer>>(bitsLeft-8))&0xff);
                bitsLeft-=8;
            }
        }
        if(index!=out.length){
            byte[] trimmed=new byte[index];
            System.arraycopy(out,0,trimmed,0,index);
            return trimmed;
        }
        return out;
    }
    /** Evita dependencia extra; comparación constante. */
    private static final class MessageDigestCompat{
        static boolean constantEquals(String a,String b){
            if(a==null || b==null || a.length()!=b.length())return false;
            int r=0;
            for(int i=0;i<a.length();i++)r|=a.charAt(i)^b.charAt(i);
            return r==0;
        }
    }
}
