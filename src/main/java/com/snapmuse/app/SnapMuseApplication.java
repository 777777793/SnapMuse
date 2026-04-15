package com.snapmuse.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SnapMuseApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(SnapMuseApplication.class);
        app.setHeadless(false);
        app.run(args);
    }
}
