package br.com.fiap.reloop.upload.controller;

import java.util.UUID;

import br.com.fiap.reloop.upload.dto.UploadResponse;
import br.com.fiap.reloop.upload.service.PhotoService;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/uploads")
@Profile("bootstrap")
public class UploadController {

    private final PhotoService photos;

    public UploadController(PhotoService photos) {
        this.photos = photos;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@RequestPart("file") MultipartFile file) {
        return photos.upload(file);
    }

    @GetMapping("/{photoId}")
    public ResponseEntity<Resource> download(@PathVariable UUID photoId) {
        PhotoService.Download photo = photos.download(photoId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(photo.contentType()).body(photo.resource());
    }
}
