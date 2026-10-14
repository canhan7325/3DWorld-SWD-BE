package com.project.w_3d_world.features.auth.controller;

import io.swagger.v3.oas.annotations.Hidden;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Hidden
@Controller
public class EmailLinkController {
    @GetMapping("/verify-email")
    public String verificationPage() {
        return "forward:/verify-email.html";
    }

    @GetMapping("/reset-password")
    public String resetPage() {
        return "forward:/reset-password.html";
    }
}
