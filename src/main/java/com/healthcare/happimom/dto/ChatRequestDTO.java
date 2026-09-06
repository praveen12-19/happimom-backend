package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequestDTO {
    /**
     * The current message typed by the user.
     */
    private String message;

    /**
     * Optional Base64 data of an attached image or prescription.
     */
    private String fileBase64;

    /**
     * MIME type of the attached file, e.g. "image/jpeg", "image/png", "application/pdf".
     */
    private String fileMimeType;

    /**
     * Optional conversation turns for multi-turn conversational context.
     */
    private List<ChatTurnDTO> history;
}
