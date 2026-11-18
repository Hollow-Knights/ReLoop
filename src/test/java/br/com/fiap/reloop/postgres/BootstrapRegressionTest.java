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
import org.springframework.context.annotation.Import;
import br.com.fiap.reloop.contract.OpenApiErrorContractConfiguration;
import br.com.fiap.reloop.contract.MobileContractScenarios;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties={"logging.level.root=WARN","spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print=MockMvcPrint.NONE)
@ActiveProfiles("bootstrap")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Import(OpenApiErrorContractConfiguration.class)
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
    @Test void completeMemoryJourneyStillCreditsAndRedeemsIdempotently() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(mapper.writeValueAsBytes(java.util.Map.of("name","Bootstrap","email",email,"password","Senha123!"))))
                .andExpect(status().isCreated());
        var login=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(mapper.writeValueAsBytes(java.util.Map.of("email",email,"password","Senha123!"))))
                .andExpect(status().isOk()).andReturn();
        String token="Bearer "+mapper.readTree(login.getResponse().getContentAsByteArray()).get("accessToken").asText();
        var png=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",png);
        var upload=mvc.perform(multipart("/api/v1/uploads")
                .file(new org.springframework.mock.web.MockMultipartFile("file","phone.png","image/png",png.toByteArray()))
                .header("Authorization",token)).andExpect(status().isCreated()).andReturn();
        String photo=mapper.readTree(upload.getResponse().getContentAsByteArray()).get("photoId").asText();
        mvc.perform(get("/api/v1/uploads/"+photo).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(content().bytes(png.toByteArray()));
        var created=mvc.perform(post("/api/v1/disposals").header("Authorization",token).contentType("application/json")
                .content(mapper.writeValueAsBytes(java.util.Map.of("deviceId","COL-001","categoryId",1,"itemName","Celular","photoId",photo))))
                .andExpect(status().isCreated()).andReturn();
        long id=mapper.readTree(created.getResponse().getContentAsByteArray()).get("id").asLong();
        for(String type:java.util.List.of("WEIGHT_RECORDED","DOOR_OPENED","DOOR_CLOSED","DOOR_CLOSED")) {
            var event=new java.util.HashMap<String,Object>();
            event.put("eventId",type);event.put("deviceId","COL-001");event.put("disposalId",id);event.put("eventType",type);
            if(type.equals("WEIGHT_RECORDED")) event.put("weightKg",new java.math.BigDecimal("0.500"));
            var result=mvc.perform(post("/api/v1/iot/events").header("X-Device-Key","bootstrap-test-device-key")
                    .contentType("application/json").content(mapper.writeValueAsBytes(event))).andExpect(status().isOk());
            if(type.equals("WEIGHT_RECORDED")) result.andExpect(jsonPath("$.estimatedPoints").value(33));
            if(type.equals("DOOR_CLOSED")) result.andExpect(jsonPath("$.awardedPoints").value(33));
        }
        mvc.perform(get("/api/v1/disposals/"+id).header("Authorization",token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED"));
        var purchase=mvc.perform(post("/api/v1/rewards/10/redeem").header("Authorization",token)
                .header("Idempotency-Key","bootstrap-reward")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.remainingPoints").value(13)).andReturn();
        String coupon=mapper.readTree(purchase.getResponse().getContentAsByteArray()).get("couponCode").asText();
        mvc.perform(post("/api/v1/rewards/10/redeem").header("Authorization",token).header("Idempotency-Key","bootstrap-reward"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.couponCode").value(coupon));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization",token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(13)).andExpect(jsonPath("$.transactions.length()").value(2));
    }

    @Test void mobileProfileAliasesShareReadAndUpdate() throws Exception {
        new MobileContractScenarios(mvc).profileAliasesReadAndUpdateTheSameAccount();
    }
    @Test void mobileProfileAliasesRequireJwt() throws Exception {
        new MobileContractScenarios(mvc).bothProfileAliasesRequireBearerJwt();
    }
    @Test void mobileProfileIdentityAndValidationAreIsolated() throws Exception {
        new MobileContractScenarios(mvc).profileIdentityAndValidationStayIsolated();
    }
    @Test void mobilePointDistancesAreNumericStableAndConsistent() throws Exception {
        new MobileContractScenarios(mvc).everyPointHasTheSameStableNumericDistanceInListAndDetail();
    }

}
