package com.healthcare.happimom.controller;

import com.healthcare.happimom.dto.AppointmentDTO;
import com.healthcare.happimom.service.AppointmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/appointments")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:3000", "http://127.0.0.1:5173"})
public class AppointmentController {

    private static final Logger log = LoggerFactory.getLogger(AppointmentController.class);

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = appointmentService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<AppointmentDTO>> getUserAppointments(@PathVariable Long userId) {
        List<AppointmentDTO> list = appointmentService.getUserAppointments(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAppointmentById(
            @PathVariable Long id,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        AppointmentDTO dto = appointmentService.getAppointmentById(id, userId);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<?> createAppointment(@RequestBody AppointmentDTO dto) {
        try {
            AppointmentDTO created = appointmentService.createAppointment(dto);
            return ResponseEntity.ok(created);
        } catch (Exception e) {
            log.error("Failed to create appointment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAppointment(@PathVariable Long id, @RequestBody AppointmentDTO dto) {
        try {
            AppointmentDTO updated = appointmentService.updateAppointment(id, dto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update appointment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAppointment(
            @PathVariable Long id,
            @RequestParam(value = "userId", required = false) Long userId
    ) {
        try {
            appointmentService.deleteAppointment(id, userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Appointment deleted successfully"));
        } catch (Exception e) {
            log.error("Failed to delete appointment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/upload-prescription")
    public ResponseEntity<?> uploadPrescriptionAndSchedule(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = true) Long userId,
            @RequestParam(value = "notes", required = false) String notes,
            @RequestParam(value = "appointmentDate", required = false) String appointmentDate,
            @RequestParam(value = "appointmentTime", required = false) String appointmentTime,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "purpose", required = false) String purpose
    ) {
        try {
            AppointmentDTO scheduled = appointmentService.uploadPrescriptionAndSchedule(
                    file, userId, notes, appointmentDate, appointmentTime, doctorName, purpose
            );
            return ResponseEntity.ok(scheduled);
        } catch (Exception e) {
            log.error("Failed to upload prescription and schedule appointment: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/upload-report")
    public ResponseEntity<?> uploadDoctorReportAndExplain(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", required = false) Long userId,
            @RequestParam(value = "notes", required = false) String notes
    ) {
        try {
            AppointmentDTO updated = appointmentService.uploadDoctorReportAndExplain(
                    id, file, userId, notes
            );
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to upload doctor report: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}
