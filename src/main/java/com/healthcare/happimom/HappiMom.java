package com.healthcare.happimom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

@SpringBootApplication
public class HappiMom {
    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(HappiMom.class, args);
    }

    private static void loadDotEnv() {
        Path envPath = Paths.get(".env");
        if (!Files.exists(envPath)) {
            Path backendEnv = Paths.get("Backend/.env");
            if (Files.exists(backendEnv)) {
                envPath = backendEnv;
            }
        }
        if (Files.exists(envPath)) {
            try {
                List<String> lines = Files.readAllLines(envPath);
                for (String line : lines) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int equalIdx = line.indexOf('=');
                    if (equalIdx > 0) {
                        String key = line.substring(0, equalIdx).trim();
                        String value = line.substring(equalIdx + 1).trim();
                        // Strip surrounding quotes if present
                        if ((value.startsWith("\"") && value.endsWith("\"")) || 
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}
