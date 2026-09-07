package com.healthcare.happimom.service;

import com.healthcare.happimom.entity.Memory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface MemoryService {

    Memory uploadMemory(Long userId, MultipartFile file, String topic) throws IOException;

    List<Memory> getMemoriesByUser(Long userId);

    boolean deleteMemory(Long memoryId, Long userId);
}
