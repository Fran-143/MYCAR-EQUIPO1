package backend.mfa;

import backend.crypto.DatosSensiblesCrypto;

import java.util.Base64;

/** AES-GCM dedicado a secreto TOTP; AAD mfa-totp:{idUsuario}. Fail-closed sin clave. */
public final class MfaCrypto {
    private final DatosSensiblesCrypto crypto;
    private final boolean configured;

    private MfaCrypto(DatosSensiblesCrypto crypto,boolean configured){
        this.crypto=crypto;this.configured=configured;
    }
    public static MfaCrypto deshabilitado(){return new MfaCrypto(null,false);}
    public static MfaCrypto deClave(byte[] aesKey,String keyId){
        // Blind key distinta solo para satisfacer DatosSensiblesCrypto; no se usa índice ciego en MFA.
        byte[] blind=new byte[32];
        for(int i=0;i<32;i++)blind[i]=(byte)(aesKey[i]^0x5A);
        return new MfaCrypto(new DatosSensiblesCrypto(aesKey,blind,keyId),true);
    }
    public void exigir(){
        if(!configured || crypto==null)throw MfaFailure.unavailable();
    }
    public String keyId(){
        exigir();
        return crypto.keyId();
    }
    public String cifrarSecreto(long idUsuario,String secretoBase32){
        exigir();
        return crypto.sellar(secretoBase32,aad(idUsuario));
    }
    public String descifrarSecreto(long idUsuario,String sellado){
        exigir();
        return crypto.abrir(sellado,aad(idUsuario));
    }
    private static String aad(long idUsuario){return "mfa-totp:"+idUsuario;}
    public static byte[] decodeKey(String b64){
        if(b64==null || b64.isBlank())throw new IllegalArgumentException("clave MFA vacía");
        byte[] key=Base64.getDecoder().decode(b64.trim());
        if(key.length!=32)throw new IllegalArgumentException("clave MFA debe ser 256 bits");
        return key;
    }
}
