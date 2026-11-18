package br.com.fiap.reloop.postgres;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import br.com.fiap.reloop.ReLoopApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

@SpringBootTest(classes = ReLoopApplication.class, properties = {"logging.level.root=WARN", "spring.main.banner-mode=off"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("postgres")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PostgresIntegrationTest {
    private static final String PASSWORD = "Senha123!";
    private static final String KEY = UUID.randomUUID().toString();
    private static final String SECRET = secret();
    private static final Path PHOTO_ROOT = photoRoot();
    private static final Path SQL = Path.of("database/schema/database.sql").toAbsolutePath();
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16.11-alpine3.23")
            .withCopyFileToContainer(MountableFile.forHostPath(SQL), "/docker-entrypoint-initdb.d/01-hiago.sql");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private static String secret() {
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
    private static Path photoRoot() {
        try { return Files.createTempDirectory("reloop-postgres-photos-test-"); }
        catch (java.io.IOException failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("reloop.jwt.secret", () -> SECRET);
        registry.add("reloop.iot.device-key", () -> KEY);
        registry.add("reloop.iot.device-id", () -> "COL-001");
        registry.add("reloop.photo.base-url", () -> "https://photos.example.test");
        registry.add("reloop.photo.storage-dir", PHOTO_ROOT::toString);
    }
    @BeforeEach void resetOnlyTheTestcontainersDatabase() throws Exception {
        // The only JDBC target is the disposable Testcontainers instance, never DB_URL from a developer's shell.
        jdbc.execute("TRUNCATE point_transactions, redemptions, wallets, rewards, iot_events, disposals, uploaded_files, "
                + "collection_point_categories, collection_devices, electronic_categories, collection_points, users RESTART IDENTITY CASCADE");
        String sql = Files.readString(SQL);
        jdbc.execute(sql.substring(sql.indexOf("INSERT INTO users"), sql.indexOf("COMMIT;")));
        cleanPhotos(false);
    }
    @AfterAll static void cleanup() throws Exception { cleanPhotos(true); }
    private static void cleanPhotos(boolean rootToo) throws Exception {
        try (var paths = Files.walk(PHOTO_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (rootToo || !path.equals(PHOTO_ROOT)) Files.deleteIfExists(path);
            }
        }
    }
    private JsonNode body(MvcResult result) throws Exception { return mapper.readTree(result.getResponse().getContentAsByteArray()); }
    private String json(Object value) throws Exception { return mapper.writeValueAsString(value); }
    private String register() throws Exception { return register(UUID.randomUUID() + "@example.test"); }
    private String register(String email) throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Teste", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.passwordHash").doesNotExist());
        return login(mvc, email);
    }
    private String login(MockMvc client, String email) throws Exception {
        return body(client.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn()).get("accessToken").asText();
    }
    private String auth(String token) { return "Bearer " + token; }
    private byte[] png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return output.toByteArray();
    }
    private String upload(String token) throws Exception {
        return body(mvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", "item.png", "image/png", png()))
                .header("Authorization", auth(token))).andExpect(status().isCreated()).andReturn()).get("photoId").asText();
    }
    private ResultActions create(String token, String photo) throws Exception {
        return mvc.perform(post("/api/v1/disposals").header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("deviceId", "COL-001", "categoryId", 2, "itemName", "Notebook", "photoId", photo))));
    }
    private long start(String token) throws Exception { return body(create(token, upload(token)).andExpect(status().isCreated()).andReturn()).get("id").asLong(); }
    private ResultActions event(MockMvc client, long id, String key, String type, String weight) throws Exception {
        return client.perform(post("/api/v1/iot/events").header("X-Device-Key", KEY).contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"" + key + "\",\"deviceId\":\"COL-001\",\"disposalId\":" + id
                        + ",\"eventType\":\"" + type + "\"" + (weight == null ? "" : ",\"weightKg\":" + weight) + "}"));
    }
    private void complete(long id) throws Exception {
        event(mvc, id, "w-" + id, "WEIGHT_RECORDED", "2.300").andExpect(status().isOk());
        event(mvc, id, "o-" + id, "DOOR_OPENED", null).andExpect(status().isOk());
        event(mvc, id, "c-" + id, "DOOR_CLOSED", null).andExpect(status().isOk());
    }
    private ResultActions redeem(MockMvc client, String token, String key, long reward) throws Exception {
        return client.perform(post("/api/v1/rewards/" + reward + "/redeem").header("Authorization", auth(token)).header("Idempotency-Key", key));
    }
    private int count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class); }
    private static <T> List<T> together(int n, Callable<T> action) throws Exception {
        var gate = new CyclicBarrier(n);
        var executor = Executors.newFixedThreadPool(n, Thread.ofPlatform().daemon(true).factory());
        try {
            List<Callable<T>> tasks = java.util.stream.IntStream.range(0, n).<Callable<T>>mapToObj(i -> () -> {
                gate.await(15, TimeUnit.SECONDS); return action.call();
            }).toList();
            var results = new ArrayList<T>();
            for (var future : executor.invokeAll(tasks, 45, TimeUnit.SECONDS)) results.add(future.get());
            return results;
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test void schemaAndCatalogAreTheActualHiagoSeed() throws Exception {
        assertThat(count("users")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_type='BASE TABLE'", Integer.class)).isEqualTo(12);
        mvc.perform(get("/api/v1/electronic-categories")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5)).andExpect(jsonPath("$[1].basePoints").value(60))
                .andExpect(jsonPath("$[1].pointsPerKg").value(10)).andExpect(jsonPath("$[2].name").value("Baterias e pilhas"));
        mvc.perform(get("/api/v1/collection-points")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
        mvc.perform(get("/api/v1/collection-points/1")).andExpect(status().isOk()).andExpect(jsonPath("$.devices.length()").value(2));
        mvc.perform(get("/api/v1/collection-points/999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
    }
    @Test void registrationProfileAndInvalidDemoPasswordsPreserveMainContract() throws Exception {
        String email = "NEW@EXAMPLE.TEST";
        String token = register(email);
        assertThat(count("users")).isEqualTo(6);
        assertThat(count("wallets")).isEqualTo(6);
        mvc.perform(get("/api/v1/users/me").header("Authorization", auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email.toLowerCase())).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.userPoints").doesNotExist());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("name", "Duplicate", "email", email.toLowerCase(), "password", PASSWORD))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"));
        for (String account : List.of(email, "ana.souza@email.com", "unknown@example.test"))
            mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content(json(Map.of("email", account, "password", "WrongPassword!"))))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        mvc.perform(put("/api/v1/users/me").header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Atualizado\",\"email\":\"changed@example.test\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Atualizado"));
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE id=6", String.class)).isEqualTo("changed@example.test");
        mvc.perform(put("/api/v1/users/me").header("Authorization", auth(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Duplicate\",\"email\":\"ANA.SOUZA@EMAIL.COM\"}"))
                .andExpect(status().isConflict());
    }
    @Test void uploadIsDurablePrivateAndCancellationRespectsTheSqlConstraint() throws Exception {
        String token = register(); String other = register(); String photo = upload(token);
        String url = "/api/v1/uploads/" + photo;
        assertThat(jdbc.queryForObject("SELECT size_bytes FROM uploaded_files WHERE id=?::uuid", Long.class, photo)).isEqualTo((long)png().length);
        mvc.perform(get(url).header("Authorization", auth(token))).andExpect(status().isOk()).andExpect(content().bytes(png()));
        mvc.perform(get(url).header("Authorization", auth(other))).andExpect(status().isNotFound());
        long id = body(create(token, photo).andExpect(status().isCreated()).andExpect(jsonPath("$.photoId").value(photo))
                .andExpect(jsonPath("$.photoUrl").value("https://photos.example.test" + url))
                .andExpect(jsonPath("$.status").value("WAITING_WEIGHT")).andReturn()).get("id").asLong();
        create(token, photo).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PHOTO_ALREADY_LINKED"));
        create(token, upload(token)).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DEVICE_UNAVAILABLE"));
        mvc.perform(delete("/api/v1/disposals/" + id).header("Authorization", auth(other))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/disposals/" + id).header("Authorization", auth(token))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/disposals/" + id).header("Authorization", auth(token))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT weight_kg IS NULL AND estimated_points IS NULL FROM disposals WHERE id=?", Boolean.class,id)).isTrue();
        assertThat(start(token)).isGreaterThan(id);
    }
    @Test void completeNotebookJourneyUses83PointsAnd80PointReward() throws Exception {
        String token = register(); long id = start(token); complete(id);
        mvc.perform(get("/api/v1/disposals/" + id).header("Authorization", auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.awardedPoints").value(83));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization", auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(83));
        var coupon = body(redeem(mvc,token,"one",2).andExpect(status().isCreated()).andExpect(jsonPath("$.pointsSpent").value(80))
                .andExpect(jsonPath("$.remainingPoints").value(3)).andReturn());
        assertThat(coupon.get("couponCode").asText()).startsWith("RELOOP-");
        assertThat(body(redeem(mvc,token,"one",2).andExpect(status().isCreated()).andReturn())).isEqualTo(coupon);
        redeem(mvc,token,"one",1).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization", auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(3)).andExpect(jsonPath("$.transactions.length()").value(2));
        mvc.perform(get("/api/v1/users/me/disposals").header("Authorization", auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoryId").value(2));
    }
    @ParameterizedTest @CsvSource({"0.800,68", "1.049,70", "1.050,71", "5.000,100"})
    void acceptedWeightsUseOriginalFormulaAndInclusiveBounds(String weight, int points) throws Exception {
        String token = register(); long id = start(token);
        event(mvc,id,"w","WEIGHT_RECORDED",weight).andExpect(status().isOk()).andExpect(jsonPath("$.estimatedPoints").value(points));
        mvc.perform(get("/api/v1/users/me/wallet").header("Authorization",auth(token))).andExpect(status().isOk())
                .andExpect(jsonPath("$.availablePoints").value(0));
        mvc.perform(delete("/api/v1/disposals/" + id).header("Authorization",auth(token))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT weight_kg FROM disposals WHERE id=?",java.math.BigDecimal.class,id))
                .isEqualByComparingTo(weight);
    }
    @ParameterizedTest @CsvSource({"0.799,422", "5.001,422", "0.8001,400", "1.2300,400", "0,400", "-1,400", "1.0499,400"})
    void invalidWeightsAreRejectedWithoutRoundingOrConsumingEventId(String weight, int expected) throws Exception {
        String token = register(); long id = start(token);
        event(mvc,id,"w","WEIGHT_RECORDED",weight).andExpect(status().is(expected));
        assertThat(jdbc.queryForObject("SELECT status FROM disposals WHERE id=?",String.class,id)).isEqualTo("WAITING_WEIGHT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM iot_events WHERE event_id='w'",Integer.class)).isZero();
        event(mvc,id,"w","WEIGHT_RECORDED","2.3").andExpect(status().isOk());
    }
    @Test void seedEventSnapshotsAndReplayRemainCompatibleAfterLaterTransitions() throws Exception {
        event(mvc,1,"evt-0001","WEIGHT_RECORDED","0.180").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_DEPOSIT")).andExpect(jsonPath("$.awardedPoints").value(0))
                .andExpect(jsonPath("$.estimatedPoints").value(31));
        event(mvc,1,"evt-0002","DOOR_OPENED",null).andExpect(status().isOk()).andExpect(jsonPath("$.awardedPoints").value(0));
        String token = register(); long id = start(token); complete(id);
        event(mvc,id,"w-"+id,"WEIGHT_RECORDED","2.300").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WAITING_DEPOSIT")).andExpect(jsonPath("$.awardedPoints").value(0));
        event(mvc,id,"w-"+id,"WEIGHT_RECORDED","2.301").andExpect(status().isConflict());
        event(mvc,id,"fresh-close","DOOR_CLOSED",null).andExpect(status().isConflict());
    }
    @Test void simultaneousClosesAndRedemptionsHaveExactlyOneEffect() throws Exception {
        String token = register(); long id = start(token);
        event(mvc,id,"w","WEIGHT_RECORDED","2.3").andExpect(status().isOk());
        event(mvc,id,"o","DOOR_OPENED",null).andExpect(status().isOk());
        var closes = together(6, () -> body(event(mvc,id,"c","DOOR_CLOSED",null).andExpect(status().isOk()).andReturn()));
        assertThat(closes).allSatisfy(r -> assertThat(r).isEqualTo(closes.getFirst()));
        var purchases = together(6, () -> body(redeem(mvc,token,"shared",2).andExpect(status().isCreated()).andReturn()));
        assertThat(purchases).allSatisfy(r -> assertThat(r).isEqualTo(purchases.getFirst()));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE disposal_id=?",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM redemptions WHERE user_id=6",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT available_points FROM wallets WHERE user_id=6",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM rewards WHERE id=2",Integer.class)).isEqualTo(28);
    }
    @Test void simultaneousDifferentKeysCannotOverdrawBalance() throws Exception {
        String token = register(); complete(start(token));
        var results = together(4, () -> redeem(mvc,token,UUID.randomUUID().toString(),2).andReturn().getResponse().getStatus());
        assertThat(results.stream().filter(s -> s==201).count()).isEqualTo(1);
        assertThat(results.stream().filter(s -> s==409).count()).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT available_points FROM wallets WHERE user_id=6",Integer.class)).isEqualTo(3);
    }
    @Test void differentUsersCompetingForLastStockHaveOneWinner() throws Exception {
        String a = register(); complete(start(a)); String b = register(); complete(start(b));
        jdbc.update("UPDATE rewards SET available_quantity=1 WHERE id=2");
        var next = new java.util.concurrent.atomic.AtomicInteger();
        var results = together(2, () -> redeem(mvc,next.getAndIncrement()==0?a:b,"last",2).andReturn().getResponse().getStatus());
        assertThat(results).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("SELECT available_quantity FROM rewards WHERE id=2",Integer.class)).isZero();
        assertThat(jdbc.queryForList("SELECT available_points FROM wallets WHERE user_id IN (6,7)",Integer.class))
                .containsExactlyInAnyOrder(3,83);
    }
    @Test void simultaneousCollectorReservationsHaveOneWinner() throws Exception {
        String token = register(); var photos = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        for(int i=0;i<4;i++) photos.add(upload(token));
        var results = together(4, () -> create(token,photos.remove()).andReturn().getResponse().getStatus());
        assertThat(results.stream().filter(s -> s==201).count()).isEqualTo(1);
        assertThat(results.stream().filter(s -> s==409).count()).isEqualTo(3);
    }
    private void rejectWrites(String name, String table, String condition) {
        jdbc.execute("CREATE FUNCTION " + name + "() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER " + name + " BEFORE INSERT OR UPDATE ON " + table
                + " FOR EACH ROW WHEN (" + condition + ") EXECUTE FUNCTION " + name + "()");
    }
    private void removeRejection(String name, String table) {
        jdbc.execute("DROP TRIGGER " + name + " ON " + table);
        jdbc.execute("DROP FUNCTION " + name + "()");
    }
    @Test void failedCreditRollsBackDisposalEventWalletAndAllowsSameEventRetry() throws Exception {
        String token = register(); long id = start(token);
        event(mvc,id,"w","WEIGHT_RECORDED","2.3").andExpect(status().isOk());
        event(mvc,id,"o","DOOR_OPENED",null).andExpect(status().isOk());
        rejectWrites("test_reject_credit","point_transactions","NEW.type = 'CREDIT'");
        try {
            event(mvc,id,"c","DOOR_CLOSED",null).andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"));
            assertThat(jdbc.queryForObject("SELECT status FROM disposals WHERE id=?",String.class,id)).isEqualTo("DEPOSIT_IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT available_points FROM wallets WHERE user_id=6",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM iot_events WHERE event_id='c'",Integer.class)).isZero();
        } finally { removeRejection("test_reject_credit","point_transactions"); }
        event(mvc,id,"c","DOOR_CLOSED",null).andExpect(status().isOk()).andExpect(jsonPath("$.awardedPoints").value(83));
    }
    @Test void failedStockWriteRollsBackRedemptionDebitAndKey() throws Exception {
        String token = register(); complete(start(token));
        rejectWrites("test_reject_stock","rewards","NEW.available_quantity < 29");
        try {
            redeem(mvc,token,"retry",2).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT available_points FROM wallets WHERE user_id=6",Integer.class)).isEqualTo(83);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM redemptions WHERE user_id=6",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM point_transactions WHERE wallet_id=6 AND type='DEBIT'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT available_quantity FROM rewards WHERE id=2",Integer.class)).isEqualTo(29);
        } finally { removeRejection("test_reject_stock","rewards"); }
        redeem(mvc,token,"retry",2).andExpect(status().isCreated());
    }
    @Test void rejectedUploadMetadataRemovesThePhysicalFile() throws Exception {
        String token = register();
        rejectWrites("test_reject_file","uploaded_files","NEW.user_id = 6");
        try {
            mvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file","item.png","image/png",png()))
                    .header("Authorization",auth(token))).andExpect(status().isInternalServerError());
            try(var paths=Files.walk(PHOTO_ROOT)) { assertThat(paths.filter(Files::isRegularFile).count()).isZero(); }
            assertThat(count("uploaded_files")).isEqualTo(10);
        } finally { removeRejection("test_reject_file","uploaded_files"); }
    }
    @Test void freshApplicationContextRecoversFilesCreditCouponAndOriginalSnapshots() throws Exception {
        String email = "restart@example.test"; String token=register(email);
        String photo=upload(token); long id=body(create(token,photo).andExpect(status().isCreated()).andReturn()).get("id").asLong();
        complete(id);
        var purchase=body(redeem(mvc,token,"persistent-key",2).andExpect(status().isCreated()).andReturn());
        var overrides=new HashMap<String,Object>();
        overrides.put("spring.datasource.url",DB.getJdbcUrl()); overrides.put("spring.datasource.username",DB.getUsername());
        overrides.put("spring.datasource.password",DB.getPassword()); overrides.put("reloop.jwt.secret",SECRET);
        overrides.put("reloop.iot.device-key",KEY); overrides.put("reloop.iot.device-id","COL-001");
        overrides.put("reloop.photo.storage-dir",PHOTO_ROOT.toString()); overrides.put("reloop.photo.base-url","https://photos.example.test");
        overrides.put("server.port",0); overrides.put("logging.level.root","WARN"); overrides.put("spring.main.banner-mode","off");
        try(var second=new org.springframework.boot.builder.SpringApplicationBuilder(ReLoopApplication.class)
                .profiles("postgres").initializers(context -> context.getEnvironment().getPropertySources()
                        .addFirst(new org.springframework.core.env.MapPropertySource("isolated-restart-test",overrides))).run()) {
            var client=org.springframework.test.web.servlet.setup.MockMvcBuilders
                    .webAppContextSetup((org.springframework.web.context.WebApplicationContext)second)
                    .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
            String renewed=login(client,email);
            client.perform(get("/api/v1/uploads/"+photo).header("Authorization",auth(renewed)))
                    .andExpect(status().isOk()).andExpect(content().bytes(png()));
            event(client,id,"c-"+id,"DOOR_CLOSED",null).andExpect(status().isOk()).andExpect(jsonPath("$.awardedPoints").value(83));
            event(client,id,"w-"+id,"WEIGHT_RECORDED","2.3").andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("WAITING_DEPOSIT")).andExpect(jsonPath("$.awardedPoints").value(0));
            assertThat(body(redeem(client,renewed,"persistent-key",2).andExpect(status().isCreated()).andReturn())).isEqualTo(purchase);
            client.perform(get("/api/v1/users/me/wallet").header("Authorization",auth(renewed))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.availablePoints").value(3)).andExpect(jsonPath("$.transactions.length()").value(2));
            // Two independent application contexts/connections cannot bypass a persisted key.
            var across=together(4, () -> body(redeem(client,renewed,"persistent-key",2).andExpect(status().isCreated()).andReturn()));
            assertThat(across).allSatisfy(r -> assertThat(r).isEqualTo(purchase));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM redemptions WHERE user_id=6",Integer.class)).isEqualTo(1);
    }
}
