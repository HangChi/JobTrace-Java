package com.jobtrace.identityaccess.web;

import com.jobtrace.identityaccess.application.UploadAvatar;
import com.jobtrace.identityaccess.application.UploadAvatar.InvalidAvatarException;
import java.io.IOException;
import com.jobtrace.identityaccess.infrastructure.TencentCosAvatarStore.StorageUnavailableException;
import java.net.URI;
import java.security.Principal;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public final class AvatarController {
    public static final String PATH = "/api/profile/avatar";
    private final UploadAvatar upload;

    public AvatarController(UploadAvatar upload) {
        this.upload = upload;
    }

    @PostMapping(value = PATH, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file,
            Principal principal) {
        String owner = BridgePrincipalOwner.require(principal);
        if (file.getSize() <= 0 || file.getSize() > UploadAvatar.MAX_BYTES) {
            throw new InvalidAvatarException();
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidAvatarException();
        }
        String url = upload.execute(owner, new UploadAvatar.Input(file.getContentType(), bytes));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(Map.of("url", url));
    }

    @ExceptionHandler({InvalidAvatarException.class, StorageUnavailableException.class})
    public ResponseEntity<ProblemDetail> failure(RuntimeException exception) {
        boolean invalid = exception instanceof InvalidAvatarException;
        HttpStatus status = invalid ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        String code = invalid ? "validation" : "storage";
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("urn:jobtrace:problem:" + code));
        problem.setProperty("code", code);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(problem);
    }
}
