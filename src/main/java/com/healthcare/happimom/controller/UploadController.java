package com.healthcare.happimom.controller;

import com.healthcare.happimom.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final CloudinaryService cloudinaryService;

    public UploadController(CloudinaryService cloudinaryService) {
        this.cloudinaryService = cloudinaryService;
    }

    @PostMapping("/medical-file")
    public ResponseEntity<?> uploadMedicalFile(@RequestParam("file") MultipartFile file) {
        try {
            Map<String, Object> uploadResult = cloudinaryService.uploadFile(file, "happimom_medical_docs");
            String secureUrl = (String) uploadResult.get("secure_url");
            if (secureUrl == null) {
                secureUrl = (String) uploadResult.get("url");
            }

            String publicId = (String) uploadResult.get("public_id");

            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            resp.put("url", secureUrl);
            resp.put("publicId", publicId);
            resp.put("fileName", file.getOriginalFilename());
            resp.put("size", file.getSize());
            resp.put("type", file.getContentType());
            resp.put("storage", "cloudinary");

            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Failed to upload medical file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/file")
    public ResponseEntity<?> deleteFile(
        
            @RequestParam(value = "publicId", required = false) String publicId,
            @RequestParam(value = "url", required = false) String url
    ) {
        try {
            boolean deleted = cloudinaryService.deleteMedia(publicId, url, null);
            return ResponseEntity.ok(Map.of("success", true, "deleted", deleted));
        } catch (Exception e) {
            log.error("Failed to delete file from Cloudinary: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
