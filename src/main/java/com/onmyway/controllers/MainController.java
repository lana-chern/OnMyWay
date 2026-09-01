package com.onmyway.controllers;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {

    @GetMapping("/hello")
    public String returnHelloWorld() {
        return "Hello, World!";
    }
}
