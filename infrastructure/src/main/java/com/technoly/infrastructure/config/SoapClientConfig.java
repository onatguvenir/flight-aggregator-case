package com.technoly.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * SOAP Client Bean Konfigürasyonu.
 *
 * Bu konfigürasyon ile:
 * - JAXB marshaller/unmarshaller ayarlanır (XML ↔ Java mapping)
 * - Provider A ve Provider B için ayrı {@link WebServiceTemplate} bean'leri tanımlanır
 *
 * Neden iki ayrı WebServiceTemplate?
 * - Her provider farklı endpoint URL'e sahip olabilir.
 * - Timeout, interceptor, security gibi ayarlar provider bazında farklılaşabilir.
 *   (Bu örnekte sadece defaultUri ayrıştırılmış durumda.)
 */
@Configuration
public class SoapClientConfig {

    @Value("${PROVIDER_A_URL:http://localhost:8081/ws}")
    private String providerAUrl;

    @Value("${PROVIDER_B_URL:http://localhost:8082/ws}")
    private String providerBUrl;

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        // JAXB context path: XSD/WSDL'den üretilen sınıfların bulunduğu paket.
        // marshalSendAndReceive() bu paket altındaki JAXB annotated sınıfları kullanır.
        marshaller.setContextPath("com.flightprovider.wsdl");
        return marshaller;
    }

    @Bean(name = "webServiceTemplateA")
    public WebServiceTemplate webServiceTemplateA(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        // Provider A endpoint'i (env veya application.yml üzerinden override edilebilir)
        template.setDefaultUri(providerAUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }

    @Bean(name = "webServiceTemplateB")
    public WebServiceTemplate webServiceTemplateB(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        // Provider B endpoint'i (env veya application.yml üzerinden override edilebilir)
        template.setDefaultUri(providerBUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }
}
