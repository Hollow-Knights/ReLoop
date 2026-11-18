package br.com.fiap.reloop.postgres;

import br.com.fiap.reloop.support.TestDocuments;

import static org.assertj.core.api.Assertions.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import br.com.fiap.reloop.ReLoopApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

/** Real TCP requests with the first application fully stopped before the second starts. */
@Testcontainers
class PostgresRestartHttpTest {
    // JUnit/Testcontainers owns the lifecycle of this static @Container.
    @SuppressWarnings("resource")
    @Container static final PostgreSQLContainer<?> DB=new PostgreSQLContainer<>("postgres:16.11-alpine3.23")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("database/schema/database.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/01-hiago.sql")
            .withCopyFileToContainer(MountableFile.forHostPath(Path.of("database/migrations/V001__add_user_document.sql").toAbsolutePath()),
                    "/docker-entrypoint-initdb.d/02-user-document.sql");
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final ObjectMapper mapper=new ObjectMapper();
    private static final String PASSWORD="Senha123!";
    private String base;
    private String key;
    private ConfigurableApplicationContext start(Map<String,Object> properties) {
        var context=new SpringApplicationBuilder(ReLoopApplication.class).profiles("postgres")
                .initializers(c->c.getEnvironment().getPropertySources().addFirst(new MapPropertySource("restart-http-test",properties))).run();
        base="http://127.0.0.1:"+((WebServerApplicationContext)context).getWebServer().getPort();
        return context;
    }
    private JsonNode send(String method,String path,String token,String deviceKey,String idempotency,String contentType,byte[] bytes,int expected) throws Exception {
        var request=HttpRequest.newBuilder(URI.create(base+path)).timeout(java.time.Duration.ofSeconds(20));
        if(token!=null) request.header("Authorization","Bearer "+token);
        if(deviceKey!=null) request.header("X-Device-Key",deviceKey);
        if(idempotency!=null) request.header("Idempotency-Key",idempotency);
        if(contentType!=null) request.header("Content-Type",contentType);
        var response=http.send(request.method(method,bytes==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
        assertThat(response.statusCode()).isEqualTo(expected);
        return response.body().length==0?null:mapper.readTree(response.body());
    }
    private JsonNode json(String path,String token,String idempotency,Object payload,int expected) throws Exception {
        return send("POST",path,token,null,idempotency,"application/json",payload==null?null:mapper.writeValueAsBytes(payload),expected);
    }
    private String login(String email) throws Exception {
        return json("/api/v1/auth/login",null,null,Map.of("email",email,"password",PASSWORD),200).get("accessToken").asText();
    }
    private JsonNode event(long id,String eventId,String type,String weight) throws Exception {
        var payload=new HashMap<String,Object>();
        payload.put("eventId",eventId);payload.put("deviceId","COL-001");payload.put("disposalId",id);payload.put("eventType",type);
        if(weight!=null) payload.put("weightKg",new java.math.BigDecimal(weight));
        return send("POST","/api/v1/iot/events",null,key,null,"application/json",mapper.writeValueAsBytes(payload),200);
    }
    @Test void committedFlowSurvivesFullShutdownAndRestartOverRealHttp(@TempDir Path files) throws Exception {
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        key=UUID.randomUUID().toString();
        var properties=new HashMap<String,Object>();
        properties.put("spring.datasource.url",DB.getJdbcUrl());properties.put("spring.datasource.username",DB.getUsername());
        properties.put("spring.datasource.password",DB.getPassword());properties.put("reloop.jwt.secret",Base64.getEncoder().encodeToString(secret));
        properties.put("reloop.iot.device-key",key);properties.put("reloop.iot.device-id","COL-001");
        properties.put("reloop.photo.storage-dir",files.toString());properties.put("reloop.photo.base-url","http://localhost:8080");
        properties.put("server.address","127.0.0.1");properties.put("server.port",0);properties.put("spring.main.banner-mode","off");
        properties.put("logging.level.root","WARN");
        String document=TestDocuments.next();
        String email=UUID.randomUUID()+"@example.test";long id;String photo;JsonNode originalCoupon;
        byte[] image;
        try(var application=start(properties)) {
            json("/api/v1/auth/register",null,null,Map.of("name","Restart","email",email,"password",PASSWORD,"document",document),201);
            String token=login(email);
            var output=new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",output);
            image=output.toByteArray();
            String boundary="reloop-"+UUID.randomUUID();
            var multipart=new java.io.ByteArrayOutputStream();
            multipart.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"notebook.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            multipart.write(image);multipart.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
            photo=send("POST","/api/v1/uploads",token,null,null,"multipart/form-data; boundary="+boundary,multipart.toByteArray(),201).get("photoId").asText();
            var tooLarge=new java.io.ByteArrayOutputStream();
            tooLarge.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"large.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            tooLarge.write(new byte[10*1024*1024+1]);
            tooLarge.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
            assertThat(send("POST","/api/v1/uploads",token,null,null,"multipart/form-data; boundary="+boundary,
                    tooLarge.toByteArray(),413).get("code").asText()).isEqualTo("FILE_TOO_LARGE");
            id=json("/api/v1/disposals",token,null,Map.of("deviceId","COL-001","categoryId",2,"itemName","Notebook","photoId",photo),201).get("id").asLong();
            assertThat(event(id,"weight","WEIGHT_RECORDED","2.300").get("estimatedPoints").asInt()).isEqualTo(83);
            event(id,"open","DOOR_OPENED",null);event(id,"close","DOOR_CLOSED",null);
            originalCoupon=json("/api/v1/rewards/2/redeem",token,"restart-key",null,201);
            assertThat(originalCoupon.get("remainingPoints").asInt()).isEqualTo(3);
        }
        // The previous server, entity manager factory and connection pool have all been closed.
        try(var restarted=start(properties)) {
            String token=login(email);
            assertThat(restarted.getBean(org.springframework.jdbc.core.JdbcTemplate.class)
                    .queryForObject("SELECT document FROM users WHERE email=?", String.class, email)).isEqualTo(document);
            var conflict=json("/api/v1/auth/register",null,null,Map.of("name","Duplicate","email","duplicate@example.test",
                    "password",PASSWORD,"document",TestDocuments.formatted(document)),409);
            assertThat(conflict.path("code").asText()).isEqualTo("DOCUMENT_ALREADY_IN_USE");
            assertThat(conflict.toString()).doesNotContain(document,TestDocuments.formatted(document));

            var download=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/uploads/"+photo))
                    .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);assertThat(download.body()).isEqualTo(image);
            assertThat(event(id,"close","DOOR_CLOSED",null).get("awardedPoints").asInt()).isEqualTo(83);
            assertThat(event(id,"weight","WEIGHT_RECORDED","2.300").get("status").asText()).isEqualTo("WAITING_DEPOSIT");
            assertThat(json("/api/v1/rewards/2/redeem",token,"restart-key",null,201)).isEqualTo(originalCoupon);
            var wallet=send("GET","/api/v1/users/me/wallet",token,null,null,null,null,200);
            assertThat(wallet.get("availablePoints").asInt()).isEqualTo(3);
            assertThat(wallet.get("transactions").size()).isEqualTo(2);
        }
    }    private byte[] multipartBody(String boundary,byte[] image) throws Exception {
        var bytes=new java.io.ByteArrayOutputStream();
        for(var entry:Map.of("deviceId","COL-001","categoryId","1","itemName","Celular multipart").entrySet()) {
            bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\""+entry.getKey()+"\"\r\n\r\n"+entry.getValue()+"\r\n").getBytes(StandardCharsets.UTF_8));
        }
        bytes.write(("--"+boundary+"\r\nContent-Disposition: form-data; name=\"file\"; filename=\"item.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        bytes.write(image);bytes.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.US_ASCII));
        return bytes.toByteArray();
    }

    @Test void multipartDisposalAndPhotoSurviveRealHttpRestart(@TempDir Path files) throws Exception {
        byte[] secret=new byte[32];new java.security.SecureRandom().nextBytes(secret);
        var properties=new HashMap<String,Object>();
        properties.put("spring.datasource.url",DB.getJdbcUrl());properties.put("spring.datasource.username",DB.getUsername());
        properties.put("spring.datasource.password",DB.getPassword());properties.put("reloop.jwt.secret",Base64.getEncoder().encodeToString(secret));
        properties.put("reloop.iot.device-key",UUID.randomUUID().toString());properties.put("reloop.iot.device-id","COL-001");
        properties.put("reloop.photo.storage-dir",files.toString());properties.put("reloop.photo.base-url","https://photos.example.test");
        properties.put("server.address","127.0.0.1");properties.put("server.port",0);properties.put("logging.level.root","WARN");properties.put("spring.main.banner-mode","off");
        String email=UUID.randomUUID()+"@example.test";String photo;long id;
        byte[] contents=br.com.fiap.reloop.contract.DisposalFormScenarios.image("png");JsonNode created;
        try(var application=start(properties)) {
            json("/api/v1/auth/register",null,null,Map.of("name","Form restart","email",email,"password",PASSWORD,"document",TestDocuments.next()),201);
            String token=login(email);String boundary="reloop-"+UUID.randomUUID();
            // Exercise the actual servlet multipart size limit, not just MockMultipartFile.getSize().
            var oversized=send("POST","/api/v1/disposals",token,null,null,"multipart/form-data; boundary="+boundary,
                    multipartBody(boundary,new byte[10*1024*1024+1]),413);
            assertThat(oversized.path("code").asText()).isEqualTo("FILE_TOO_LARGE");
            created=send("POST","/api/v1/disposals",token,null,null,"multipart/form-data; boundary="+boundary,multipartBody(boundary,contents),201);
            id=created.path("id").asLong();photo=created.path("photoId").asText();
            assertThat(created.path("status").asText()).isEqualTo("WAITING_WEIGHT");
            assertThat(created.path("photoUrl").asText()).isEqualTo("https://photos.example.test/api/v1/uploads/"+photo);
        }
        try(var restarted=start(properties)) {
            String token=login(email);
            assertThat(send("GET","/api/v1/disposals/"+id,token,null,null,null,null,200)).isEqualTo(created);
            var download=http.send(HttpRequest.newBuilder(URI.create(base+"/api/v1/uploads/"+photo))
                    .header("Authorization","Bearer "+token).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            assertThat(download.statusCode()).isEqualTo(200);assertThat(download.body()).isEqualTo(contents);
            var database=restarted.getBean(org.springframework.jdbc.core.JdbcTemplate.class);
            assertThat(database.queryForObject("SELECT count(*) FROM disposals WHERE id=? AND photo_id=?::uuid",Integer.class,id,photo)).isEqualTo(1);
            assertThat(database.queryForObject("SELECT count(*) FROM uploaded_files WHERE id=?::uuid",Integer.class,photo)).isEqualTo(1);
            // Release this test's collector; the older JSON restart test shares the disposable container.
            send("DELETE","/api/v1/disposals/"+id,token,null,null,null,null,204);
        }
    }

}
