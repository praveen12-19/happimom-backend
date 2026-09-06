package com.healthcare.happimom;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class HappiMom {
    public static void main(String[] args) {
        String envDir = Files.exists(Path.of(".env")) ? "./" : (Files.exists(Path.of("Backend/.env")) ? "./Backend" : "./");
        Dotenv dotenv = Dotenv.configure()
                .directory(envDir)
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry -> {
            System.setProperty(entry.getKey(), entry.getValue());
        });

        SpringApplication.run(HappiMom.class, args);
    }
}
