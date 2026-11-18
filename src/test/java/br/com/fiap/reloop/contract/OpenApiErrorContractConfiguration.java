package br.com.fiap.reloop.contract;

import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/** Checks every error returned by the existing PostgreSQL and bootstrap MockMvc suites. */
@TestConfiguration(proxyBeanMethods = false)
public class OpenApiErrorContractConfiguration {
    @Bean
    MockMvcBuilderCustomizer documentedErrors() {
        return builder -> builder.alwaysDo(OpenApiContract::assertDocumentedError);
    }
}
