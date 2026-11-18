package br.com.fiap.reloop.postgres;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"logging.level.root=WARN","spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("bootstrap")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class BootstrapRegressionTest {
    @Autowired MockMvc mvc;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        byte[] secret=new byte[32]; new java.security.SecureRandom().nextBytes(secret);
        registry.add("reloop.jwt.secret",()->Base64.getEncoder().encodeToString(secret));
        registry.add("reloop.iot.device-key",()->"bootstrap-test-device-key");
    }
    @Test void memoryProfileRemainsIndependentOfPostgresAndUsesItsOwnCatalog() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/api/v1/electronic-categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4)).andExpect(jsonPath("$[2].name").value("Pequenos eletrodomésticos"));
        String email=UUID.randomUUID()+"@example.test";
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content("{\"name\":\"Bootstrap\",\"email\":\""+email+"\",\"password\":\"Senha123!\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"email\":\""+email+"\",\"password\":\"Senha123!\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void bootstrapAlsoRejectsExcessPrecisionBeforeRepositoryAccess() throws Exception {
        mvc.perform(post("/api/v1/iot/events").header("X-Device-Key","bootstrap-test-device-key").contentType("application/json")
                .content("{\"eventId\":\"precision\",\"deviceId\":\"COL-001\",\"disposalId\":999,\"eventType\":\"WEIGHT_RECORDED\",\"weightKg\":1.0499}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
