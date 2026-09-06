package com.healthcare.happimom.controller;

import com.healthcare.happimom.dto.ChatRequestDTO;
import com.healthcare.happimom.dto.ChatResponseDTO;
import com.healthcare.happimom.service.ChatAiService;
import com.healthcare.happimom.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final ChatAiService chatAiService;
    private final CloudinaryService cloudinaryService;

    public AiController(ChatAiService chatAiService, CloudinaryService cloudinaryService) {
        this.chatAiService = chatAiService;
        this.cloudinaryService = cloudinaryService;
    }

    /**
     * Conversational AI Assistant SSE Token Streaming (Groq Primary for Plain Text, Gemini for Vision/Files).
     * Returns a Flux<String> as text/event-stream for progressive rendering on frontend.
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@RequestBody ChatRequestDTO request) {
        log.info("Incoming request to /api/ai/chat/stream (hasFile: {})",
                (request != null && request.getFileBase64() != null && !request.getFileBase64().isBlank()));
        try {
            return chatAiService.chatStream(request);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("Invalid chatStream request: {}", e.getMessage());
            return Flux.error(e);
        } catch (Exception e) {
            log.error("Error initiating chatStream: {}", e.getMessage(), e);
            return Flux.error(new RuntimeException("Unable to initiate AI stream: " + e.getMessage()));
        }
    }

    /**
     * Conversational AI Assistant (Google Gemini + Groq Fallback).
     * Non-streaming endpoint kept for backward compatibility.
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequestDTO request) {
        try {
            ChatResponseDTO response = chatAiService.chat(request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage(), "needsConfig", true));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Medical Prescription & Document Upload via Cloudinary.
     */
    @PostMapping(value = "/upload-prescription", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadPrescription(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "notes", required = false) String notes) {
        try {
            Map<String, Object> result = cloudinaryService.uploadFile(file);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Cloudinary prescription upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Cloudinary upload failed: " + e.getMessage()));
        }
    }
}
