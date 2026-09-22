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

    public boolean deleteFile(String publicId) {
        return deleteMedia(publicId, null, null);
    }

    public boolean deleteFile(String publicId, String resourceType) {
        if (publicId == null || publicId.isBlank()) return false;
        return tryDestroy(publicId, resourceType != null ? resourceType : "image");
    }

    /**
     * Delete media asset from Cloudinary using either publicId or fileUrl,
     * automatically detecting and falling back across resource types (image, video, raw).
     */
    public boolean deleteMedia(String publicId, String fileUrl, String fileType) {
        String finalPublicId = publicId;
        if (finalPublicId == null || finalPublicId.isBlank()) {
            finalPublicId = extractPublicId(fileUrl);
        }

        if (finalPublicId == null || finalPublicId.isBlank()) {
            log.warn("Cannot delete file from Cloudinary: no publicId or valid URL provided");
            return false;
        }

        // Determine primary resource type
        String primaryType = "image";
        if (fileType != null) {
            String lower = fileType.toLowerCase();
            if (lower.startsWith("video/") || lower.endsWith(".mp4") || lower.endsWith(".mov")
                    || lower.endsWith(".webm") || lower.endsWith(".avi") || lower.endsWith(".mkv")) {
                primaryType = "video";
            } else if (lower.contains("pdf") || lower.contains("raw") || lower.contains("document")) {
                primaryType = "raw";
            }
        }
        if (fileUrl != null) {
            String lowerUrl = fileUrl.toLowerCase();
            if (lowerUrl.contains("/video/upload/")) {
                primaryType = "video";
            } else if (lowerUrl.contains("/raw/upload/")) {
                primaryType = "raw";
            }
        }

        // 1. Try deleting with primary resolved resource type
        if (tryDestroy(finalPublicId, primaryType)) {
            return true;
        }

        // 2. Try other resource types in case Cloudinary stored under different type (e.g. PDF as image)
        String[] types = {"image", "video", "raw"};
        for (String type : types) {
            if (!type.equals(primaryType)) {
                if (tryDestroy(finalPublicId, type)) {
                    return true;
                }
            }
        }

        // 3. For raw files or files with extension in public ID, try with extension
        if (fileUrl != null) {
            String rawId = extractPublicIdWithExtension(fileUrl);
            if (rawId != null && !rawId.equals(finalPublicId)) {
                for (String type : new String[]{"raw", "image", "video"}) {
                    if (tryDestroy(rawId, type)) {
                        return true;
                    }
                }
            }
        }

        log.warn("Cloudinary file deletion finished: publicId {} (url: {}) could not be found or was already deleted", finalPublicId, fileUrl);
        return false;
    }

    private boolean tryDestroy(String publicId, String resourceType) {
        try {
            Map<String, Object> params = ObjectUtils.asMap(
                    "resource_type", resourceType,
                    "invalidate", true
            );
            @SuppressWarnings("unchecked")
            Map<String, Object> result = cloudinary.uploader().destroy(publicId, params);
            if (result != null) {
                String resStr = (String) result.get("result");
                if ("ok".equalsIgnoreCase(resStr)) {
                    log.info("Successfully deleted file from Cloudinary: publicId={}, resourceType={}", publicId, resourceType);
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Cloudinary destroy attempt failed for publicId={}, resourceType={}: {}", publicId, resourceType, e.getMessage());
        }
        return false;
    }

    /**
     * Extract Cloudinary publicId (without file extension) from URL.
     */
    public String extractPublicId(String fileUrl) {
        String fullPath = extractPublicIdWithExtension(fileUrl);
        if (fullPath == null) return null;
        int dotIdx = fullPath.lastIndexOf('.');
        if (dotIdx > 0 && dotIdx > fullPath.lastIndexOf('/')) {
            return fullPath.substring(0, dotIdx);
        }
        return fullPath;
    }

    /**
     * Extract Cloudinary public path (including file extension) from URL.
     */
    public String extractPublicIdWithExtension(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) return null;
        try {
            if (!fileUrl.contains("cloudinary.com") || !fileUrl.contains("/upload/")) {
                return null;
            }

            int uploadIdx = fileUrl.indexOf("/upload/");
            String afterUpload = fileUrl.substring(uploadIdx + "/upload/".length());

            int queryIdx = afterUpload.indexOf('?');
            if (queryIdx != -1) {
                afterUpload = afterUpload.substring(0, queryIdx);
            }

            // If it starts with known application prefix
            int appFolderIdx = afterUpload.indexOf("happimom_");
            if (appFolderIdx != -1) {
                return afterUpload.substring(appFolderIdx);
            }

            // Otherwise, strip version prefix if present, e.g. v172567890/
            String[] parts = afterUpload.split("/");
            int start = 0;
            for (int i = 0; i < parts.length; i++) {
                if (parts[i].matches("^v\\d+$")) {
                    start = i + 1;
                    break;
                }
            }

            StringBuilder sb = new StringBuilder();
            for (int i = start; i < parts.length; i++) {
                if (sb.length() > 0) sb.append("/");
                sb.append(parts[i]);
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Failed to extract publicId from URL {}: {}", fileUrl, e.getMessage());
            return null;
        }
    }
}
