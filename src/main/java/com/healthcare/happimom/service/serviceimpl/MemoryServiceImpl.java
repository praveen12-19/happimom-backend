package com.healthcare.happimom.service.serviceimpl;

import com.healthcare.happimom.entity.Memory;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.exception.UserNotFoundException;
import com.healthcare.happimom.repository.MemoryRepository;
import com.healthcare.happimom.repository.UserRepository;
import com.healthcare.happimom.service.CloudinaryService;
import com.healthcare.happimom.service.MemoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class MemoryServiceImpl implements MemoryService {

    private static final Logger log = LoggerFactory.getLogger(MemoryServiceImpl.class);

    private final UserRepository userRepository;
    private final MemoryRepository memoryRepository;
    private final CloudinaryService cloudinaryService;

    public MemoryServiceImpl(UserRepository userRepository,
                             MemoryRepository memoryRepository,
                             CloudinaryService cloudinaryService) {
        this.userRepository = userRepository;
        this.memoryRepository = memoryRepository;
        this.cloudinaryService = cloudinaryService;
    }

    @Override
    public Memory uploadMemory(Long userId, MultipartFile file, String topic) throws IOException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + userId));

        // 1. Upload to Cloudinary and acquire secure URL link
        Map<String, Object> uploadResult;
        try {
            uploadResult = cloudinaryService.uploadFile(file, "happimom_memories/user_" + userId);
        } catch (Exception e) {
            log.error("Cloudinary upload failed: {}", e.getMessage(), e);
            throw new IOException("Cloudinary upload failed: " + e.getMessage() + ". Please verify that your Cloudinary API key in .env has 'create' permissions in Cloudinary Settings.", e);
        }

        String fileUrl = (String) uploadResult.get("secure_url");
        if (fileUrl == null) {
            fileUrl = (String) uploadResult.get("url");
        }
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IOException("Cloudinary did not return a valid secure URL for this file.");
        }
        String publicId = (String) uploadResult.get("public_id");
        log.info("Uploaded memory file to Cloudinary: {}, public_id: {}, secure_url: {}", file.getOriginalFilename(), publicId, fileUrl);

        // Determine accurate content type for videos, images, and PDFs
        String fileType = file.getContentType();
        String originalName = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (fileType == null || fileType.isBlank() || "application/octet-stream".equalsIgnoreCase(fileType)) {
            if (originalName.endsWith(".mp4")) fileType = "video/mp4";
            else if (originalName.endsWith(".mov")) fileType = "video/quicktime";
            else if (originalName.endsWith(".webm")) fileType = "video/webm";
            else if (originalName.endsWith(".avi")) fileType = "video/x-msvideo";
            else if (originalName.endsWith(".mkv")) fileType = "video/x-matroska";
            else if (originalName.endsWith(".pdf")) fileType = "application/pdf";
            else if (originalName.endsWith(".jpg") || originalName.endsWith(".jpeg")) fileType = "image/jpeg";
            else if (originalName.endsWith(".png")) fileType = "image/png";
            else if (originalName.endsWith(".gif")) fileType = "image/gif";
            else fileType = "application/octet-stream";
        }

        // 2. Persist directly into MySQL database under the 'memories' entity
        Memory memory = new Memory();
        memory.setUser(user);
        memory.setFileUrl(fileUrl); // Store the Cloudinary link of the file
        memory.setPublicId(publicId); // Store the Cloudinary publicId for removal
        memory.setFileName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "memory_file");
        memory.setFileType(fileType);
        memory.setFileSize(file.getSize());
        memory.setTopic(topic != null && !topic.isBlank() ? topic.trim() : "Pregnancy Memory");
        memory.setUploadedAt(Instant.now().toString());

        return memoryRepository.save(memory);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Memory> getMemoriesByUser(Long userId) {
        return memoryRepository.findByUserIdOrderByIdDesc(userId);
    }

    @Override
    public boolean deleteMemory(Long memoryId, Long userId) {
        Optional<Memory> memoryOpt = memoryRepository.findByIdAndUserId(memoryId, userId);
        if (memoryOpt.isPresent()) {
            Memory memory = memoryOpt.get();

            // 1. Delete asset from Cloudinary storage
            try {
                boolean cldDeleted = cloudinaryService.deleteMedia(
                        memory.getPublicId(),
                        memory.getFileUrl(),
                        memory.getFileType()
                );
                log.info("Cloudinary removal for memory id {}: result={}", memoryId, cldDeleted);
            } catch (Exception e) {
                log.warn("Error deleting file from Cloudinary for memory id {}: {}", memoryId, e.getMessage());
            }

            // 2. Delete memory record from MySQL database
            memoryRepository.delete(memory);
            log.info("Deleted memory id {} from memories entity for user {}", memoryId, userId);
            return true;
        }
        return false;
    }
}
