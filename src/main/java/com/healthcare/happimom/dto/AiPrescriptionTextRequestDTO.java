package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiPrescriptionTextRequestDTO {
    private String prescriptionText;
    private Long userId;
    private String additionalNotes;
}
