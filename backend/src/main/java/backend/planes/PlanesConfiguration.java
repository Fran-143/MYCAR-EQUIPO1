package backend.planes;

import backend.planes.mercadopago.MercadoPagoTestProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({PlanesProperties.class, MercadoPagoTestProperties.class})
public class PlanesConfiguration {
}
