package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiSymptomRequestDTO {
    private String symptomText;
    private Long userId;
    private List<String> candidateLabels;
}
