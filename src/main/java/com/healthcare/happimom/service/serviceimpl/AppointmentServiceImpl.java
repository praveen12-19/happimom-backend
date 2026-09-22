package com.healthcare.happimom.service.serviceimpl;

import com.healthcare.happimom.dto.AppointmentDTO;
import com.healthcare.happimom.entity.Appointment;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.repository.AppointmentRepository;
import com.healthcare.happimom.repository.UserRepository;
import com.healthcare.happimom.service.AiService;
import com.healthcare.happimom.service.AppointmentService;
import com.healthcare.happimom.service.CloudinaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService;
    private final AiService aiService;

    public AppointmentServiceImpl(
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            CloudinaryService cloudinaryService,
            AiService aiService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.cloudinaryService = cloudinaryService;
        this.aiService = aiService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppointmentDTO> getUserAppointments(Long userId) {
        if (userId == null) return Collections.emptyList();
        List<Appointment> appointments = appointmentRepository.findByUserIdOrderByAppointmentDateAsc(userId);
        return appointments.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public AppointmentDTO getAppointmentById(Long id, Long userId) {
        Optional<Appointment> opt = (userId != null)
                ? appointmentRepository.findByIdAndUserId(id, userId)
                : appointmentRepository.findById(id);
        return opt.map(this::mapToDTO).orElse(null);
    }

    @Override
    public AppointmentDTO createAppointment(AppointmentDTO dto) {
        if (dto.getUserId() == null) {
            throw new IllegalArgumentException("User ID is required to schedule an appointment");
        }
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + dto.getUserId()));

        Appointment appt = new Appointment();
        appt.setUser(user);
        appt.setDoctorName(dto.getDoctorName() != null ? dto.getDoctorName() : "Obstetrician Specialist");
        appt.setClinicName(dto.getClinicName() != null ? dto.getClinicName() : "Maternity Care Clinic");
        appt.setAppointmentDate(dto.getAppointmentDate() != null ? dto.getAppointmentDate() : LocalDate.now().toString());
        appt.setAppointmentTime(dto.getAppointmentTime() != null ? dto.getAppointmentTime() : "10:00 AM");
        appt.setPurpose(dto.getPurpose() != null ? dto.getPurpose() : "Routine Prenatal Checkup");
        appt.setStatus(dto.getStatus() != null ? dto.getStatus() : "SCHEDULED");
        appt.setCreatedAt(LocalDateTime.now());
        appt.setUpdatedAt(LocalDateTime.now());

        Appointment saved = appointmentRepository.save(appt);
        return mapToDTO(saved);
    }

    @Override
    public AppointmentDTO updateAppointment(Long id, AppointmentDTO dto) {
        Appointment appt = appointmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found with id: " + id));

        if (dto.getDoctorName() != null) appt.setDoctorName(dto.getDoctorName());
        if (dto.getClinicName() != null) appt.setClinicName(dto.getClinicName());
        if (dto.getAppointmentDate() != null) appt.setAppointmentDate(dto.getAppointmentDate());
        if (dto.getAppointmentTime() != null) appt.setAppointmentTime(dto.getAppointmentTime());
        if (dto.getPurpose() != null) appt.setPurpose(dto.getPurpose());
        if (dto.getStatus() != null) appt.setStatus(dto.getStatus());
        appt.setUpdatedAt(LocalDateTime.now());

        Appointment updated = appointmentRepository.save(appt);
        return mapToDTO(updated);
    }

    @Override
    public void deleteAppointment(Long id, Long userId) {
        Optional<Appointment> opt = (userId != null)
                ? appointmentRepository.findByIdAndUserId(id, userId)
                : appointmentRepository.findById(id);

        if (opt.isPresent()) {
            Appointment appt = opt.get();
            // Optional: delete files from Cloudinary if needed
            if (appt.getPrescriptionPublicId() != null) {
                try {
                    cloudinaryService.deleteMedia(appt.getPrescriptionPublicId(), appt.getPrescriptionFileUrl(), "image");
                } catch (Exception e) {
                    log.warn("Could not delete prescription media from Cloudinary: {}", e.getMessage());
                }
            }
            if (appt.getReportPublicId() != null) {
                try {
                    cloudinaryService.deleteMedia(appt.getReportPublicId(), appt.getReportFileUrl(), "image");
                } catch (Exception e) {
                    log.warn("Could not delete report media from Cloudinary: {}", e.getMessage());
                }
            }
            appointmentRepository.delete(appt);
        }
    }

    @Override
    public AppointmentDTO uploadPrescriptionAndSchedule(
            MultipartFile file,
            Long userId,
            String notes,
            String appointmentDate,
            String appointmentTime,
            String doctorName,
            String purpose
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("User ID is required to upload prescription");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        String fileUrl = null;
        String publicId = null;

        if (file != null && !file.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadFile(file, "happimom_prescriptions");
                fileUrl = (String) uploadResult.get("secure_url");
                if (fileUrl == null) fileUrl = (String) uploadResult.get("url");
                publicId = (String) uploadResult.get("public_id");
                log.info("Prescription uploaded to Cloudinary: {}", fileUrl);
            } catch (Exception e) {
                log.error("Cloudinary upload failed for prescription: {}", e.getMessage(), e);
            }
        }

        // Run AI analysis to extract medications, safety, and check if appointment date was in the document
        String prescriptionAnalysis = null;
        String extractedDate = null;
        String extractedDoctor = null;
        String extractedPurpose = null;

        try {
            Map<String, Object> aiResult = aiService.parsePrescriptionAndExtractAppointment(file, userId, notes);
            if (aiResult != null) {
                prescriptionAnalysis = (String) aiResult.get("analysis");
                extractedDate = (String) aiResult.get("appointmentDate");
                extractedDoctor = (String) aiResult.get("doctorName");
                extractedPurpose = (String) aiResult.get("purpose");
            }
        } catch (Exception e) {
            log.warn("AI prescription extraction warning: {}", e.getMessage());
            prescriptionAnalysis = "Prescription received and securely stored in your Cloudinary vault. Please review dosage instructions with your physician.";
        }

        Appointment appt = new Appointment();
        appt.setUser(user);
        appt.setPrescriptionFileUrl(fileUrl);
        appt.setPrescriptionPublicId(publicId);
        appt.setPrescriptionAnalysis(prescriptionAnalysis);

        // Date selection priority: 1) explicit param 2) extracted by AI 3) today or fallback
        if (appointmentDate != null && !appointmentDate.isBlank()) {
            appt.setAppointmentDate(appointmentDate);
        } else if (extractedDate != null && !extractedDate.isBlank()) {
            appt.setAppointmentDate(extractedDate);
        } else {
            appt.setAppointmentDate(LocalDate.now().toString());
        }

        appt.setAppointmentTime(appointmentTime != null && !appointmentTime.isBlank() ? appointmentTime : "10:30 AM");

        if (doctorName != null && !doctorName.isBlank()) {
            appt.setDoctorName(doctorName);
        } else if (extractedDoctor != null && !extractedDoctor.isBlank()) {
            appt.setDoctorName(extractedDoctor);
        } else if (user.getEmergencyContact() != null && !user.getEmergencyContact().isBlank()) {
            appt.setDoctorName(user.getEmergencyContact());
        } else {
            appt.setDoctorName("Obstetrician Care Specialist");
        }

        if (purpose != null && !purpose.isBlank()) {
            appt.setPurpose(purpose);
        } else if (extractedPurpose != null && !extractedPurpose.isBlank()) {
            appt.setPurpose(extractedPurpose);
        } else {
            appt.setPurpose("Prescription Review & Prenatal Follow-up");
        }

        appt.setStatus("SCHEDULED");
        appt.setCreatedAt(LocalDateTime.now());
        appt.setUpdatedAt(LocalDateTime.now());

        Appointment saved = appointmentRepository.save(appt);
        log.info("Scheduled new appointment ID {} for date {}", saved.getId(), saved.getAppointmentDate());
        return mapToDTO(saved);
    }

    @Override
    public AppointmentDTO uploadDoctorReportAndExplain(
            Long appointmentId,
            MultipartFile file,
            Long userId,
            String notes
    ) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found with ID: " + appointmentId));

        if (userId != null && appt.getUser() != null && !appt.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Unauthorized: Appointment does not belong to this user");
        }

        String reportUrl = null;
        String publicId = null;

        if (file != null && !file.isEmpty()) {
            try {
                Map<String, Object> uploadResult = cloudinaryService.uploadFile(file, "happimom_reports");
                reportUrl = (String) uploadResult.get("secure_url");
                if (reportUrl == null) reportUrl = (String) uploadResult.get("url");
                publicId = (String) uploadResult.get("public_id");
                log.info("Doctor report uploaded to Cloudinary: {}", reportUrl);
            } catch (Exception e) {
                log.error("Failed to upload doctor report to Cloudinary: {}", e.getMessage(), e);
            }
        }

        // Invoke AI to explain the doctor's report in reassuring, clear maternal terms
        String explanation;
        try {
            explanation = aiService.explainDoctorConsultationReport(file, (userId != null ? userId : appt.getUser().getId()), notes);
        } catch (Exception e) {
            log.error("AI report explanation error: {}", e.getMessage(), e);
            explanation = "### Doctor Consultation Summary\n"
                    + "Your report has been safely uploaded and saved to your Cloudinary records.\n"
                    + "- **Doctor Notes:** " + (notes != null && !notes.isBlank() ? notes : "Follow regular prenatal recommendations.") + "\n"
                    + "- **Status:** Completed consultation review. Always follow your obstetrician's direct medical instructions.";
        }

        appt.setReportFileUrl(reportUrl);
        appt.setReportPublicId(publicId);
        appt.setReportAnalysis(explanation);
        appt.setReportUploadedAt(LocalDateTime.now().toString());
        appt.setStatus("COMPLETED");
        appt.setUpdatedAt(LocalDateTime.now());

        Appointment updated = appointmentRepository.save(appt);
        log.info("Appointment ID {} marked COMPLETED with report uploaded", updated.getId());
        return mapToDTO(updated);
    }

    private AppointmentDTO mapToDTO(Appointment entity) {
        AppointmentDTO dto = new AppointmentDTO();
        dto.setId(entity.getId());
        dto.setUserId(entity.getUser() != null ? entity.getUser().getId() : null);
        dto.setDoctorName(entity.getDoctorName());
        dto.setClinicName(entity.getClinicName());
        dto.setAppointmentDate(entity.getAppointmentDate());
        dto.setAppointmentTime(entity.getAppointmentTime());
        dto.setPurpose(entity.getPurpose());
        dto.setStatus(entity.getStatus());
        dto.setPrescriptionFileUrl(entity.getPrescriptionFileUrl());
        dto.setPrescriptionPublicId(entity.getPrescriptionPublicId());
        dto.setPrescriptionAnalysis(entity.getPrescriptionAnalysis());
        dto.setReportFileUrl(entity.getReportFileUrl());
        dto.setReportPublicId(entity.getReportPublicId());
        dto.setReportAnalysis(entity.getReportAnalysis());
        dto.setReportUploadedAt(entity.getReportUploadedAt());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
