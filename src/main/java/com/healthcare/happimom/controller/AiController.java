package com.healthcare.happimom.controller;

import com.healthcare.happimom.dto.AiChatRequestDTO;
import com.healthcare.happimom.dto.AiChatResponseDTO;
import com.healthcare.happimom.dto.AiPrescriptionTextRequestDTO;
import com.healthcare.happimom.dto.AiSymptomRequestDTO;
import com.healthcare.happimom.service.AiService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/chat")
    public ResponseEntity<AiChatResponseDTO> chat(@RequestBody AiChatRequestDTO request) {
        AiChatResponseDTO response = aiService.chat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<ResponseBodyEmitter> chatStream(@RequestBody AiChatRequestDTO request) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(180000L); // 3 minutes timeout
        aiService.chatStream(request, emitter);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    @PostMapping("/analyze-symptom")
    public ResponseEntity<Map<String, Object>> analyzeSymptom(@RequestBody AiSymptomRequestDTO request) {
        Map<String, Object> response = aiService.analyzeSymptom(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/analyze-prescription-text")
    public ResponseEntity<Map<String, Object>> analyzePrescriptionText(@RequestBody AiPrescriptionTextRequestDTO request) {
        Map<String, Object> response = aiService.analyzePrescriptionText(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/upload-prescription")
    public ResponseEntity<Map<String, Object>> uploadPrescription(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        Map<String, Object> response = aiService.uploadPrescription(file, userId, notes);
        return ResponseEntity.ok(response);
    }
}
