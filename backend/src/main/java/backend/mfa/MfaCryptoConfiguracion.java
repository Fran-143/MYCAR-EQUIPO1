package backend.mfa;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class MfaCryptoConfiguracion {
    @Bean
    MfaCrypto mfaCrypto(
            @Value("${mycar.mfa.enabled:true}") boolean enabled,
            @Value("${mycar.mfa.key-id:v1}") String keyId,
            @Value("${mycar.mfa.aes-key:}") String aesB64){
        if(!enabled || aesB64==null || aesB64.isBlank()){
            return MfaCrypto.deshabilitado();
        }
        return MfaCrypto.deClave(MfaCrypto.decodeKey(aesB64),keyId==null||keyId.isBlank()?"v1":keyId.trim());
    }
}
