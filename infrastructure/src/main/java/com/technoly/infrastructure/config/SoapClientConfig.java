package com.technoly.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

/**
 * SOAP Client Bean Configuration.
 *
 * With this configuration:
 * - JAXB marshaller/unmarshaller is set (XML ↔ Java mapping)
 * - Separate {@link WebServiceTemplate} beans are defined for Provider A and
 * Provider B
 *
 * Why two separate WebServiceTemplates?
 * - Each provider may have a different endpoint URL.
 * - Settings like timeout, interceptors, and security can differ per provider.
 * (In this example, only the defaultUri is differentiated.)
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
        // JAXB context path: The package containing the generated classes from
        // XSD/WSDL.
        // marshalSendAndReceive() uses JAXB annotated classes under this package.
        marshaller.setContextPath("com.flightprovider.wsdl");
        return marshaller;
    }

    @Bean(name = "webServiceTemplateA")
    public WebServiceTemplate webServiceTemplateA(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        // Provider A endpoint (can be overridden via env or application.yml)
        template.setDefaultUri(providerAUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }

    @Bean(name = "webServiceTemplateB")
    public WebServiceTemplate webServiceTemplateB(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        // Provider B endpoint (can be overridden via env or application.yml)
        template.setDefaultUri(providerBUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }
}
