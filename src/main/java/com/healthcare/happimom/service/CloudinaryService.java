package com.healthcare.happimom.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
public class CloudinaryService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryService.class);

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    private Cloudinary cloudinary;

    private Cloudinary getCloudinary() {
        if (cloudinary == null) {
            String name = getEffectiveConfig("CLOUDINARY_CLOUD_NAME", cloudName);
            String key = getEffectiveConfig("CLOUDINARY_API_KEY", apiKey);
            String secret = getEffectiveConfig("CLOUDINARY_API_SECRET", apiSecret);

            if (name == null || key == null || secret == null || name.isBlank() || key.isBlank() || secret.isBlank()) {
                throw new IllegalStateException("Cloudinary credentials not configured in Backend/.env");
            }

            cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", name,
                    "api_key", key,
                    "api_secret", secret,
                    "secure", true
            ));
        }
        return cloudinary;
    }

    public Map<String, Object> uploadFile(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new IllegalArgumentException("File cannot be empty");
        }

        File tempFile = convertMultiPartToFile(multipartFile);
        try {
            Map<?, ?> uploadResult;
            try {
                uploadResult = getCloudinary().uploader().upload(tempFile, ObjectUtils.asMap(
                        "folder", "happimom/prescriptions",
                        "resource_type", "auto"
                ));
            } catch (Exception ex) {
                if (ex.getMessage() != null && ex.getMessage().contains("missing permissions")) {
                    log.warn("Cloudinary upload with folder failed ({}). Retrying without folder...", ex.getMessage());
                    uploadResult = getCloudinary().uploader().upload(tempFile, ObjectUtils.asMap(
                            "resource_type", "auto"
                    ));
                } else {
                    throw ex;
                }
            }

            String url = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");
            log.info("Successfully uploaded prescription file to Cloudinary: {}", url);

            return Map.of(
                    "url", url != null ? url : "",
                    "publicId", publicId != null ? publicId : "",
                    "fileName", multipartFile.getOriginalFilename() != null ? multipartFile.getOriginalFilename() : "document",
                    "size", multipartFile.getSize(),
                    "success", true
            );
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("missing permissions")) {
                throw new IllegalStateException("Cloudinary API Key lacks 'create' permissions. In Cloudinary Console -> Settings -> Access Keys, edit your API key and enable 'Create' (or use your primary Root Access Key).");
            }
            throw e;
        } finally {
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private File convertMultiPartToFile(MultipartFile file) throws IOException {
        File convFile = File.createTempFile("upload-", "-" + file.getOriginalFilename());
        try (FileOutputStream fos = new FileOutputStream(convFile)) {
            fos.write(file.getBytes());
        }
        return convFile;
    }

    private String getEffectiveConfig(String envKey, String propertyValue) {
        if (propertyValue != null && !propertyValue.trim().isEmpty() && !propertyValue.startsWith("${")) {
            return propertyValue.trim();
        }
        String sysProp = System.getProperty(envKey);
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv(envKey);
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return null;
    }
}
