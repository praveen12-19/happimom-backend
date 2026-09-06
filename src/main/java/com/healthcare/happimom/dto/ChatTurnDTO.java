package com.healthcare.happimom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatTurnDTO {
    /**
     * Role in the conversation: "user" or "model" / "assistant".
     */
    private String role;

    /**
     * Text message for this turn.
     */
    private String text;
}
