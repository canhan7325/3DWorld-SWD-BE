package com.project.w_3d_world.features.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
public class EmailLinkController {
    @GetMapping(value = "/verify-email", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> verificationPage() {
        return page("verify-email.html");
    }

    @GetMapping(value = "/reset-password", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> resetPage() {
        return page("reset-password.html");
    }

    private ResponseEntity<Resource> page(String name) {
        // Serve the bundled page directly, without depending on a servlet view/forward resolver.
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(new ClassPathResource("static/" + name));
    }
}
