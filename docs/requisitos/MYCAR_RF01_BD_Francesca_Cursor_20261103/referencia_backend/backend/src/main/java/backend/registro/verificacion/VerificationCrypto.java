package backend.registro.verificacion;

import backend.registro.RegistrationCommand;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** No default keys, Spring bean or development OTP. Keys must be supplied externally. */
public final class VerificationCrypto {
    private final byte[] otpKey, fingerprintKey, indexKey, encryptionKey;
    private final SecureRandom random = new SecureRandom();
    public VerificationCrypto(byte[] otpKey, byte[] fingerprintKey, byte[] indexKey, byte[] encryptionKey) {
        this.otpKey=key(otpKey); this.fingerprintKey=key(fingerprintKey);
        this.indexKey=key(indexKey); this.encryptionKey=key(encryptionKey);
        Set<String> distinct=new HashSet<>();
        for(byte[] k:List.of(this.otpKey,this.fingerprintKey,this.indexKey,this.encryptionKey)) distinct.add(Base64.getEncoder().encodeToString(k));
        if(distinct.size()!=4) throw new IllegalArgumentException("Use four separate keys.");
    }
    private byte[] key(byte[] k) { if(k==null||k.length!=32) throw new IllegalArgumentException("A 256-bit key is required."); return k.clone(); }
    public String generateCode() { return String.format(Locale.ROOT,"%06d",random.nextInt(1_000_000)); }
    public byte[] credential(String secret) {
        try {
            if(secret==null||Base64.getUrlDecoder().decode(secret).length!=32) throw new IllegalArgumentException();
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch(Exception ex) { throw new IllegalArgumentException("Invalid protected credential."); }
    }
    public byte[] subject(String type,String value) { return mac(indexKey,canonical("REGISTRO",type,value)); }
    private byte[] context(UUID operation,UUID emission,String email,long generation) {
        return canonical("REGISTRO",operation.toString(),emission.toString(),email,Long.toString(generation));
    }
    public byte[] verifier(UUID operation,UUID emission,String email,long generation,String code) {
        return mac(otpKey,join(context(operation,emission,email,generation),canonical(code)));
    }
    public boolean matches(byte[] a,byte[] b) { return a!=null&&b!=null&&MessageDigest.isEqual(a,b); }
    public byte[] fingerprint(String action,long revision,UUID emission,RegistrationCommand f,String code) {
        byte[] form=f==null?canonical("NO_FORM"):canonical(f.nombre(),f.apellido(),f.correo(),
            f.fechaNacimiento()==null?null:f.fechaNacimiento().toString(),f.contrasena(),f.confirmacionContrasena(),
            String.valueOf(f.privacidadAceptada()),f.versionPrivacidad());
        byte[] input=join(canonical("REGISTRO",action,Long.toString(revision),emission==null?null:emission.toString()),form,canonical(code));
        try { return mac(fingerprintKey,input); } finally { Arrays.fill(input,(byte)0); Arrays.fill(form,(byte)0); }
    }
    public Sealed seal(UUID operation,UUID emission,String email,long generation,String code) {
        byte[] nonce=new byte[12];random.nextBytes(nonce);
        byte[] plain=canonical(email,code);
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context(operation,emission,email,generation));
            return new Sealed(cipher.doFinal(plain),nonce);
        } catch(GeneralSecurityException ex) { throw new IllegalStateException("Encryption unavailable."); }
        finally { Arrays.fill(plain,(byte)0); }
    }
    public MailGateway.Message open(UUID operation,UUID emission,String email,long generation,byte[] encrypted,byte[] nonce,String providerKey) {
        byte[] plain=null;
        try {
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(encryptionKey,"AES"),new GCMParameterSpec(128,nonce));
            cipher.updateAAD(context(operation,emission,email,generation));plain=cipher.doFinal(encrypted);
            var in=new DataInputStream(new ByteArrayInputStream(plain));
            return new MailGateway.Message(read(in),read(in),providerKey);
        } catch(Exception ex) { throw new IllegalStateException("Protected delivery content unavailable."); }
        finally { if(plain!=null) Arrays.fill(plain,(byte)0); }
    }
    private String read(DataInputStream in) throws IOException { int size=in.readInt();if(size<0||size>8192)throw new IOException();return new String(in.readNBytes(size),StandardCharsets.UTF_8); }
    private byte[] mac(byte[] key,byte[] data) {
        try { Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(data); }
        catch(GeneralSecurityException ex) { throw new IllegalStateException("HMAC unavailable."); }
    }
    private byte[] canonical(String... values) {
        try { var out=new ByteArrayOutputStream();var d=new DataOutputStream(out);
            for(String s:values) { if(s==null)d.writeInt(-1);else{byte[] b=s.getBytes(StandardCharsets.UTF_8);d.writeInt(b.length);d.write(b);} }
            return out.toByteArray();
        } catch(IOException ex) { throw new IllegalStateException("Canonical encoding unavailable."); }
    }
    private byte[] join(byte[]... arrays) { var out=new ByteArrayOutputStream();for(byte[] a:arrays)out.writeBytes(a);return out.toByteArray(); }
    public record Sealed(byte[] ciphertext,byte[] nonce) { @Override public String toString(){return "Sealed[REDACTED]";} }
}
