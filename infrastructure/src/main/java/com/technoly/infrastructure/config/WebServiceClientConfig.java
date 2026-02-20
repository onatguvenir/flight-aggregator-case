package com.technoly.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * Spring-WS WebServiceTemplate Konfigürasyonu
 *
 * WebServiceTemplate: Spring-WS'nin SOAP client'ı.
 * RestTemplate'ın SOAP karşılığıdır (HTTP yerine SOAP/XML).
 *
 * Jaxb2Marshaller:
 * - Java nesnelerini SOAP XML'e serialize eder (marshalling)
 * - SOAP XML'i Java nesnelerine deserialize eder (unmarshalling)
 *
 * contextPath: JAXB'nin Java nesnelerini nerede arayacağını belirtir.
 * Hem ProviderA hem de ProviderB POJO paketleri dahil edilir.
 *
 * Paylaşılan WebServiceTemplate: Her iki client da bu template'i kullanır.
 * Provider URL'si client seviyesinde set edilir (marshalSendAndReceive
 * parametresi).
 */
@Configuration
public class WebServiceClientConfig {

    /**
     * Jaxb2Marshaller: SOAP XML ↔ Java dönüşümü için.
     *
     * contextPath: Nokta ile ayrılmış paket adları.
     * Spring-WS bu paketlerdeki @XmlRootElement sınıfları otomatik bulur.
     * Her iki sağlayıcının request/response sınıfları dahil edilmiştir.
     */
    @Bean
    public Jaxb2Marshaller jaxb2Marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath(
                "com.technoly.infrastructure.client.soap.providera" +
                        ":com.technoly.infrastructure.client.soap.providerb");
        return marshaller;
    }

    /**
     * WebServiceTemplate: SOAP HTTP çağrıları için.
     *
     * marshaller = unmarshaller = jaxb2Marshaller:
     * Aynı JAXB marshaller hem serialize hem deserialize için kullanılır.
     * Bu Spring-WS convention'ına uygun yaklaşımdır.
     *
     * defaultUri: Fallback URI (her çağrıda override edilebilir).
     * Aslında client'lardan ayrı ayrı URL geçildiği için bu değer
     * override edilir, ancak Spring-WS bean validation için gereklidir.
     */
    @Bean
    public WebServiceTemplate webServiceTemplate(Jaxb2Marshaller jaxb2Marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setMarshaller(jaxb2Marshaller);
        template.setUnmarshaller(jaxb2Marshaller);
        return template;
    }
}
