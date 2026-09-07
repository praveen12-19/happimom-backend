package com.healthcare.happimom.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    private final Cloudinary cloudinary;
    private final String uploadPreset;

    public CloudinaryService(
            @Value("${CLOUDINARY_CLOUD_NAME:ehi0amss}") String cloudName,
            @Value("${CLOUDINARY_API_KEY:168881922637239}") String apiKey,
            @Value("${CLOUDINARY_API_SECRET:xwM3RLp9lp1zij42iRwKv7dRL4A}") String apiSecret,
            @Value("${CLOUDINARY_UPLOAD_PRESET:}") String uploadPreset
    ) {
        // Fallback checks from System properties if not injected
        if (cloudName == null || cloudName.startsWith("${")) {
            cloudName = System.getProperty("CLOUDINARY_CLOUD_NAME", "ehi0amss");
        }
        if (apiKey == null || apiKey.startsWith("${")) {
            apiKey = System.getProperty("CLOUDINARY_API_KEY", "168881922637239");
        }
        if (apiSecret == null || apiSecret.startsWith("${")) {
            apiSecret = System.getProperty("CLOUDINARY_API_SECRET", "xwM3RLp9lp1zij42iRwKv7dRL4A");
        }
        if (uploadPreset == null || uploadPreset.startsWith("${")) {
            uploadPreset = System.getProperty("CLOUDINARY_UPLOAD_PRESET", "");
        }

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
        this.uploadPreset = uploadPreset;
        log.info("Initialized CloudinaryService for cloud: {}", cloudName);
    }

    public Map<String, Object> uploadFile(MultipartFile file, String folderName) throws IOException {
        String resourceType = "auto";
        String contentType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        if (contentType != null) {
            String lowerType = contentType.toLowerCase();
            if (lowerType.startsWith("video/")) {
                resourceType = "video";
            } else if (lowerType.startsWith("image/")) {
                resourceType = "image";
            } else if (lowerType.contains("pdf")) {
                resourceType = "auto";
            }
        }

        if ("auto".equals(resourceType) && originalFilename != null) {
            String lowerName = originalFilename.toLowerCase();
            if (lowerName.endsWith(".mp4") || lowerName.endsWith(".mov") || lowerName.endsWith(".avi")
                    || lowerName.endsWith(".mkv") || lowerName.endsWith(".webm") || lowerName.endsWith(".3gp")) {
                resourceType = "video";
            } else if (lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png")
                    || lowerName.endsWith(".gif") || lowerName.endsWith(".webp")) {
                resourceType = "image";
            }
        }

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("resource_type", resourceType);
        if (folderName != null && !folderName.isBlank()) {
            params.put("folder", folderName);
        }
        if (uploadPreset != null && !uploadPreset.isBlank()) {
            params.put("upload_preset", uploadPreset);
        }
        // Speed optimizations: disable heavy server-side AI/analysis tasks during upload
        params.put("overwrite", false);
        params.put("unique_filename", true);
        params.put("use_filename", false);
        params.put("colors", false);
        params.put("faces", false);
        params.put("quality_analysis", false);
        params.put("cinemagraph_analysis", false);
        params.put("phash", false);

        @SuppressWarnings("unchecked")
        Map<String, Object> uploadResult;

        // Skip slow disk I/O and Windows Defender temporary file locking for files under 10MB
        if (!"video".equals(resourceType) && file.getSize() <= 10 * 1024 * 1024L) {
            uploadResult = cloudinary.uploader().upload(file.getBytes(), params);
            return uploadResult;
        }

        // For large files or videos, stream via temporary file
        java.io.File tempFile = java.io.File.createTempFile("cld_up_", "_" + (originalFilename != null ? originalFilename.replaceAll("[^a-zA-Z0-9._-]", "_") : "file"));
        try {
            try (java.io.InputStream in = file.getInputStream()) {
                java.nio.file.Files.copy(in, tempFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if ("video".equals(resourceType) || file.getSize() > 15 * 1024 * 1024L) {
                uploadResult = cloudinary.uploader().uploadLarge(tempFile, params);
            } else {
                uploadResult = cloudinary.uploader().upload(tempFile, params);
            }
            return uploadResult;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    public void deleteFile(String publicId) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (Exception e) {
            log.warn("Could not delete file with publicId {} from Cloudinary: {}", publicId, e.getMessage());
        }
    }
}
