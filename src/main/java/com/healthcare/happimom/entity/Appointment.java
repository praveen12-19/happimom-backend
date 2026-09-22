package com.healthcare.happimom.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "user")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference
    private User user;

    private String doctorName;
    private String clinicName;

    // Date formatted as YYYY-MM-DD
    @Column(nullable = false)
    private String appointmentDate;

    // e.g. 10:30 AM
    private String appointmentTime;

    private String purpose;

    // SCHEDULED, COMPLETED, CANCELLED
    @Column(nullable = false)
    private String status = "SCHEDULED";

    // Cloudinary upload for prescription or appointment slip
    @Column(columnDefinition = "TEXT")
    private String prescriptionFileUrl;
    private String prescriptionPublicId;

    @Column(columnDefinition = "TEXT")
    private String prescriptionAnalysis;

    // Cloudinary upload for post-appointment doctor consultation report
    @Column(columnDefinition = "TEXT")
    private String reportFileUrl;
    private String reportPublicId;

    // AI generated explanation of what the doctor said/advised
    @Column(columnDefinition = "LONGTEXT")
    private String reportAnalysis;

    private String reportUploadedAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
}
