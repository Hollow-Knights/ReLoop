package br.com.fiap.reloop.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import br.com.fiap.reloop.disposal.repository.DisposalRepository;
import br.com.fiap.reloop.support.TestDocuments;
import br.com.fiap.reloop.upload.repository.PhotoStorage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

/** Identical form scenarios against the real services of both profiles. */
public final class DisposalFormScenarios {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final MockMvc mvc;
    private final PhotoStorage storage;
    private final DisposalRepository disposals;
    private final Path root;
    private final boolean postgres;
    private final java.util.function.IntSupplier metadata;
    private final int metadataBefore;
    public record Account(long id, String bearer) { }

    public DisposalFormScenarios(MockMvc mvc, PhotoStorage storage, DisposalRepository disposals, Path root, boolean postgres, java.util.function.IntSupplier metadata) {
        this.mvc=mvc; this.storage=storage; this.disposals=disposals; this.root=root; this.postgres=postgres; this.metadata=metadata; this.metadataBefore=metadata.getAsInt();
    }
    public Account account() throws Exception {
        String email=UUID.randomUUID()+"@example.test";
        var registered=JSON.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("name","Form Teste","email",email,"password","FormTest123!","document",TestDocuments.next()))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        var login=JSON.readTree(mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(JSON.writeValueAsBytes(Map.of("email",email,"password","FormTest123!"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        return new Account(registered.path("id").asLong(),"Bearer "+login.path("accessToken").asText());
    }
    public static byte[] image(String format) throws Exception {
        var bytes=new ByteArrayOutputStream();
        assertThat(ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),format,bytes)).isTrue();
        return bytes.toByteArray();
    }
    public static MockMultipartHttpServletRequestBuilder form(MockMultipartFile file) {
        var request=multipart("/api/v1/disposals").file(file);
        request.param("deviceId","COL-001").param("categoryId","1").param("itemName","Celular");
        return request;
    }
    public long files() throws Exception {
        try(var entries=Files.walk(root)) { return entries.filter(Files::isRegularFile).count(); }
    }
    private void noChanges(Account account, long before) throws Exception {
        assertThat(files()).isEqualTo(before);
        assertThat(metadata.getAsInt()).isEqualTo(metadataBefore);
        assertThat(disposals.findByUserId(account.id())).isEmpty();
    }
    private void safe(org.springframework.test.web.servlet.ResultActions result,int status,String code) throws Exception {
        result.andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
                .andExpect(r->assertThat(r.getResponse().getContentAsString())
                        .doesNotContain(root.toString(),"org.postgresql","jdbc:","private-path","stackTrace"));
    }
    public void valid(String format) throws Exception {
        var account=account();byte[] contents=image(format);long before=files();
        var result=mvc.perform(form(new MockMultipartFile("file","claim.txt","application/octet-stream",contents))
                .header("Authorization",account.bearer())).andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("WAITING_WEIGHT"))
                .andExpect(jsonPath("$.weightKg").isEmpty()).andExpect(jsonPath("$.estimatedPoints").isEmpty())
                .andExpect(jsonPath("$.awardedPoints").value(0)).andReturn();
        JsonNode created=JSON.readTree(result.getResponse().getContentAsByteArray());
        var photo=storage.findById(UUID.fromString(created.path("photoId").asText())).orElseThrow();
        assertThat(photo.userId()).isEqualTo(account.id());
        assertThat(photo.contentType()).isEqualTo("image/"+format);
        assertThat(files()).isEqualTo(before+1);
        assertThat(metadata.getAsInt()).isEqualTo(metadataBefore+1);
        assertThat(java.net.URI.create(created.path("photoUrl").asText()).isAbsolute()).isTrue();
        assertThat(disposals.findByIdAndUserId(created.path("id").asLong(),account.id()).orElseThrow().photoId()).isEqualTo(photo.id());
        mvc.perform(get("/api/v1/uploads/"+photo.id()).header("Authorization",account.bearer()))
                .andExpect(status().isOk()).andExpect(content().bytes(contents)).andExpect(content().contentType("image/"+format));
        mvc.perform(get("/api/v1/disposals/"+created.path("id").asLong()).header("Authorization",account.bearer()))
                .andExpect(status().isOk()).andExpect(r->assertThat(JSON.readTree(r.getResponse().getContentAsByteArray())).isEqualTo(created));
    }
    public void invalidPhoto(String scenario) throws Exception {
        var account=account();long before=files();
        byte[] bytes=switch(scenario) {
            case "empty" -> new byte[0];
            case "fake" -> "not a photo".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            case "truncated" -> Arrays.copyOf(image("png"),24);
            case "oversized" -> new byte[10*1024*1024+1];
            case "gif" -> image("gif");
            default -> throw new IllegalArgumentException(scenario);
        };
        int status=switch(scenario) {case "empty","truncated" -> 400;case "oversized" -> 413;default -> 415;};
        String code=switch(status) {case 400 -> "BAD_REQUEST";case 413 -> "FILE_TOO_LARGE";default -> "UNSUPPORTED_MEDIA_TYPE";};
        safe(mvc.perform(form(new MockMultipartFile("file","image.png","image/png",bytes)).header("Authorization",account.bearer())),status,code);
        noChanges(account,before);
    }
    public void invalidField(String scenario) throws Exception {
        var account=account();long before=files();
        var request=multipart("/api/v1/disposals");
        if(!scenario.equals("file")) request.file(new MockMultipartFile("file","item.png","image/png",image("png")));
        var fields=new HashMap<>(Map.of("deviceId","COL-001","categoryId","1","itemName","Celular"));
        switch(scenario) {
            case "deviceId","categoryId","itemName" -> fields.remove(scenario);
            case "blank-device" -> fields.put("deviceId"," ");
            case "zero-category" -> fields.put("categoryId","0");
            case "fractional-category" -> fields.put("categoryId","1.5");
            case "blank-name" -> fields.put("itemName"," ");
            case "long-name" -> fields.put("itemName","x".repeat(121));
            case "file" -> { }
            default -> throw new IllegalArgumentException(scenario);
        }
        fields.forEach(request::param);
        safe(mvc.perform(request.header("Authorization",account.bearer())),400,"BAD_REQUEST");
        noChanges(account,before);
    }
    public void targetError(String scenario) throws Exception {
        var account=account();long before=files();
        int status;String code;
        var fields=new HashMap<>(Map.of("deviceId","COL-001","categoryId","1","itemName","Celular"));
        switch(scenario) {
            case "device-missing" -> {fields.put("deviceId","UNKNOWN");status=404;code="RESOURCE_NOT_FOUND";}
            case "category-missing" -> {fields.put("categoryId","999");status=404;code="RESOURCE_NOT_FOUND";}
            case "inactive" -> {fields.put("deviceId",postgres?"COL-003":"COL-002");status=409;code="DEVICE_UNAVAILABLE";}
            case "not-accepted" -> {fields.put("deviceId",postgres?"COL-001":"COL-003");fields.put("categoryId",postgres?"4":"2");status=422;code="CATEGORY_NOT_ACCEPTED";}
            default -> throw new IllegalArgumentException(scenario);
        }
        var changed=multipart("/api/v1/disposals").file(new MockMultipartFile("file","item.png","image/png",image("png")));
        fields.forEach(changed::param);
        safe(mvc.perform(changed.header("Authorization",account.bearer())),status,code);
        noChanges(account,before);
    }
    public void jwt(String kind) throws Exception {
        long before=files();var request=form(new MockMultipartFile("file","item.png","image/png",image("png")));
        if(kind.equals("invalid"))request.header("Authorization","Bearer bad-token");
        safe(mvc.perform(request),401,kind.equals("missing")?"AUTHENTICATION_REQUIRED":"INVALID_TOKEN");
        assertThat(files()).isEqualTo(before);
    }
    public void storageFailure(String stage) throws Exception {
        var account=account();long before=files();byte[] bytes=image("png");
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var file=new MockMultipartFile("file","item.png","image/png",bytes) {
            @Override public InputStream getInputStream() {
                if(calls.incrementAndGet()==1)return new ByteArrayInputStream(bytes); // validation succeeds first
                if(stage.equals("close"))return new ByteArrayInputStream(bytes) {
                    @Override public void close() throws IOException {throw new IOException("private-path close failure");}
                };
                return new InputStream() {
                    int offset;
                    @Override public int read() throws IOException {
                        if(offset>=bytes.length/2)throw new IOException("private-path write failure");
                        return bytes[offset++] & 255;
                    }
                };
            }
        };
        safe(mvc.perform(form(file).header("Authorization",account.bearer())),500,"INTERNAL_SERVER_ERROR");
        noChanges(account,before);
    }
    public void competingForms() throws Exception {
        var account=account();long before=files();byte[] bytes=image("png");var gate=new CyclicBarrier(4);
        List<Callable<Integer>> work=java.util.stream.IntStream.range(0,4).<Callable<Integer>>mapToObj(i->()->{
            gate.await(10,TimeUnit.SECONDS);
            return mvc.perform(form(new MockMultipartFile("file","item.png","image/png",bytes)).header("Authorization",account.bearer()))
                    .andReturn().getResponse().getStatus();
        }).toList();
        try(var executor=Executors.newFixedThreadPool(4)) {
            var results=new ArrayList<Integer>();
            for(var result:executor.invokeAll(work,30,TimeUnit.SECONDS))results.add(result.get(2,TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(201,409,409,409);
        }
        assertThat(files()).isEqualTo(before+1);
        assertThat(metadata.getAsInt()).isEqualTo(metadataBefore+1);
        assertThat(disposals.findByUserId(account.id())).hasSize(1);
    }
    public void isolationAndForgedPhotoId() throws Exception {
        var a=account();var b=account();byte[] bytes=image("png");
        var created=JSON.readTree(mvc.perform(form(new MockMultipartFile("file","a.png","image/png",bytes))
                .header("Authorization",a.bearer())).andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        String photo=created.path("photoId").asText();long id=created.path("id").asLong();
        for(String path:List.of("/api/v1/uploads/"+photo,"/api/v1/disposals/"+id)) {
            safe(mvc.perform(get(path).header("Authorization",b.bearer())),404,"RESOURCE_NOT_FOUND");
        }
        safe(mvc.perform(delete("/api/v1/disposals/"+id).header("Authorization",b.bearer())),404,"RESOURCE_NOT_FOUND");
        mvc.perform(delete("/api/v1/disposals/"+id).header("Authorization",a.bearer())).andExpect(status().isNoContent());
        var own=JSON.readTree(mvc.perform(form(new MockMultipartFile("file","b.png","image/png",bytes))
                .param("photoId",photo).param("userId",Long.toString(a.id())).header("Authorization",b.bearer()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
        assertThat(own.path("photoId").asText()).isNotEqualTo(photo);
        assertThat(storage.findById(UUID.fromString(own.path("photoId").asText())).orElseThrow().userId()).isEqualTo(b.id());
        safe(mvc.perform(get("/api/v1/uploads/"+own.path("photoId").asText()).header("Authorization",a.bearer())),404,"RESOURCE_NOT_FOUND");
    }
}
