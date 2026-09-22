package com.healthcare.happimom.service;

import com.healthcare.happimom.dto.AppointmentDTO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AppointmentService {

    List<AppointmentDTO> getUserAppointments(Long userId);

    AppointmentDTO getAppointmentById(Long id, Long userId);

    AppointmentDTO createAppointment(AppointmentDTO dto);

    AppointmentDTO updateAppointment(Long id, AppointmentDTO dto);

    void deleteAppointment(Long id, Long userId);

    AppointmentDTO uploadPrescriptionAndSchedule(
            MultipartFile file,
            Long userId,
            String notes,
            String appointmentDate,
            String appointmentTime,
            String doctorName,
            String purpose
    );

    AppointmentDTO uploadDoctorReportAndExplain(
            Long appointmentId,
            MultipartFile file,
            Long userId,
            String notes
    );
}
