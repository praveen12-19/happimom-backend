package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequestDTO {
    private String message;
    private Long userId;
    private List<ChatMessageDTO> history;
    private String fileBase64;
    private String fileMimeType;
    private String customInstructions;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatMessageDTO {
        private String role; // "user" or "model" / "assistant"
        private String text;
    }
}
