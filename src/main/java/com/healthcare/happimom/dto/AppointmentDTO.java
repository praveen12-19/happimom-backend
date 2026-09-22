package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppointmentDTO {

    private Long id;
    private Long userId;
    private String doctorName;
    private String clinicName;
    private String appointmentDate; // YYYY-MM-DD
    private String appointmentTime; // e.g. 10:30 AM
    private String purpose;
    private String status; // SCHEDULED, COMPLETED, CANCELLED

    private String prescriptionFileUrl;
    private String prescriptionPublicId;
    private String prescriptionAnalysis;

    private String reportFileUrl;
    private String reportPublicId;
    private String reportAnalysis;
    private String reportUploadedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
