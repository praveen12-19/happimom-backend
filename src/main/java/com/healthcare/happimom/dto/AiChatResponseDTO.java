package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatResponseDTO {
    private String reply;
    private String modelUsed;
}
