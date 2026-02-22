package com.technoly.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.WebServiceTemplate;

@Configuration
public class SoapClientConfig {

    @Value("${PROVIDER_A_URL:http://localhost:8081/ws}")
    private String providerAUrl;

    @Value("${PROVIDER_B_URL:http://localhost:8082/ws}")
    private String providerBUrl;

    @Bean
    public Jaxb2Marshaller marshaller() {
        Jaxb2Marshaller marshaller = new Jaxb2Marshaller();
        marshaller.setContextPath("com.flightprovider.wsdl");
        return marshaller;
    }

    @Bean(name = "webServiceTemplateA")
    public WebServiceTemplate webServiceTemplateA(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setDefaultUri(providerAUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }

    @Bean(name = "webServiceTemplateB")
    public WebServiceTemplate webServiceTemplateB(Jaxb2Marshaller marshaller) {
        WebServiceTemplate template = new WebServiceTemplate();
        template.setDefaultUri(providerBUrl);
        template.setMarshaller(marshaller);
        template.setUnmarshaller(marshaller);
        return template;
    }
}
