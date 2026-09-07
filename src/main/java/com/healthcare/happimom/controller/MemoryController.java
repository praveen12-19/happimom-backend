package com.healthcare.happimom.controller;

import com.healthcare.happimom.entity.Memory;
import com.healthcare.happimom.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/memories")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    /**
     * Upload a pregnancy memory file directly to Cloudinary and store its link in the memories entity.
     */
    @PostMapping("/upload")
    public ResponseEntity<?> uploadMemory(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId,
            @RequestParam(value = "topic", required = false) String topic
    ) {
        if (file == null || file.isEmpty() || file.getSize() == 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "The uploaded file is empty (0 bytes). Please choose a valid image, video, or PDF file."));
        }

        try {
            Memory savedMemory = memoryService.uploadMemory(userId, file, topic);
            return ResponseEntity.status(HttpStatus.CREATED).body(savedMemory);
        } catch (Exception e) {
            log.error("Failed to upload memory: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to upload file: " + e.getMessage()));
        }
    }

    /**
     * Retrieve all memories stored for a user in the memories entity table.
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Memory>> getMemories(@PathVariable Long userId) {
        List<Memory> list = memoryService.getMemoriesByUser(userId);
        return ResponseEntity.ok(list);
    }

    /**
     * Delete a memory from the database.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMemory(
            @PathVariable Long id,
            @RequestParam("userId") Long userId
    ) {
        boolean deleted = memoryService.deleteMemory(id, userId);
        if (deleted) {
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("message", "Memory deleted successfully from database");
            return ResponseEntity.ok(resp);
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Memory not found or does not belong to user"));
        }
    }
}
