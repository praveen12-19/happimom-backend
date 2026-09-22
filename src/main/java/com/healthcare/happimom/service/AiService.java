package com.healthcare.happimom.service;

import com.healthcare.happimom.dto.AiChatRequestDTO;
import com.healthcare.happimom.dto.AiChatResponseDTO;
import com.healthcare.happimom.dto.AiPrescriptionTextRequestDTO;
import com.healthcare.happimom.dto.AiSymptomRequestDTO;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Map;

public interface AiService {
    AiChatResponseDTO chat(AiChatRequestDTO request);
    void chatStream(AiChatRequestDTO request, ResponseBodyEmitter emitter);
    Map<String, Object> analyzeSymptom(AiSymptomRequestDTO request);
    Map<String, Object> analyzePrescriptionText(AiPrescriptionTextRequestDTO request);
    Map<String, Object> uploadPrescription(MultipartFile file, Long userId, String notes);
    String explainDoctorConsultationReport(MultipartFile file, Long userId, String notes);
    Map<String, Object> parsePrescriptionAndExtractAppointment(MultipartFile file, Long userId, String notes);
}
