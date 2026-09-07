package com.healthcare.happimom;

import com.healthcare.happimom.entity.Memory;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.repository.MemoryRepository;
import com.healthcare.happimom.repository.UserRepository;
import com.healthcare.happimom.service.MemoryService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class MemoryPersistenceTest {

    @BeforeAll
    public static void setupEnv() {
        System.setProperty("net.bytebuddy.experimental", "true");
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
                        if ((value.startsWith("\"") && value.endsWith("\"")) ||
                            (value.startsWith("'") && value.endsWith("'"))) {
                            value = value.substring(1, value.length() - 1);
                        }
                        if (System.getProperty(key) == null) {
                            System.setProperty(key, value);
                        }
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    @Autowired
    private MemoryService memoryService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MemoryRepository memoryRepository;

    @Test
    @DisplayName("Verify that Memory entity stores the link of the files in the 'memories' table and can be retrieved & deleted")
    public void testMemoryUploadAndDatabasePersistence() {
        String testEmail = "memory_mom_" + System.currentTimeMillis() + "@happimom.com";

        // 1. Create a User
        User user = new User();
        user.setEmail(testEmail);
        user.setPassword("Secret@123");
        user.setName("Memory Test Mom");
        user.setProfileComplete(true);
        User savedUser = userRepository.save(user);

        assertNotNull(savedUser.getId());

        // 2. Direct database persistence test for Memory entity with file link
        String simulatedCloudinaryUrl = "https://res.cloudinary.com/ehi0amss/image/upload/v172567890/ultrasound_week12.jpg";
        Memory memory = new Memory();
        memory.setUser(savedUser);
        memory.setFileName("ultrasound_week12.jpg");
        memory.setFileType("image/jpeg");
        memory.setFileSize(204800L);
        memory.setFileUrl(simulatedCloudinaryUrl);
        memory.setTopic("Week 12 Ultrasound Scan");
        memory.setUploadedAt(Instant.now().toString());

        Memory savedMemory = memoryRepository.save(memory);

        // 3. Verify Memory Entity & DB fields
        assertNotNull(savedMemory.getId(), "Memory entity record must have generated DB ID");
        assertEquals("ultrasound_week12.jpg", savedMemory.getFileName());
        assertEquals("image/jpeg", savedMemory.getFileType());
        assertEquals(simulatedCloudinaryUrl, savedMemory.getFileUrl(), "File link must be stored on the memory entity");
        assertEquals("Week 12 Ultrasound Scan", savedMemory.getTopic());
        assertNotNull(savedMemory.getUploadedAt());

        // 4. Verify retrieval through memoryService from DB
        List<Memory> memories = memoryService.getMemoriesByUser(savedUser.getId());
        assertFalse(memories.isEmpty(), "User memories must be retrieved from memories entity table");
        assertEquals(simulatedCloudinaryUrl, memories.get(0).getFileUrl());

        // 5. Test delete from DB via memoryService
        boolean deleted = memoryService.deleteMemory(savedMemory.getId(), savedUser.getId());
        assertTrue(deleted, "Memory must be deleted successfully");

        // 6. Confirm deletion in database
        List<Memory> remaining = memoryService.getMemoriesByUser(savedUser.getId());
        assertTrue(remaining.isEmpty(), "Deleted memory must no longer exist in database");
    }
}
