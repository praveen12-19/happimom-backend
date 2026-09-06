package com.healthcare.happimom.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.healthcare.happimom.dto.ChatRequestDTO;
import com.healthcare.happimom.dto.ChatResponseDTO;
import com.healthcare.happimom.dto.ChatTurnDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Service
public class ChatAiService {

    private static final Logger log = LoggerFactory.getLogger(ChatAiService.class);

    public static final String SYSTEM_PROMPT =
            "You are 'HappiMoM Assistant', a warm, supportive AI companion inside a pregnancy and\n" +
            "new-parent healthcare app. Your job covers four things:\n" +
            "1. Casual conversation: greet warmly, chat naturally, keep a caring, friendly tone.\n" +
            "2. General advice & suggestions: pregnancy/postpartum wellness tips, nutrition, sleep,\n" +
            "   light exercise, emotional support — practical and encouraging.\n" +
            "3. Health condition guidance: when the user describes a symptom or health concern,\n" +
            "   explain in simple terms what it might relate to, give sensible self-care\n" +
            "   suggestions, and clearly state when they should see a doctor. NEVER give a\n" +
            "   definitive diagnosis, a specific drug dosage, or claim certainty about a medical\n" +
            "   condition.\n" +
            "4. File explanation: when a prescription, lab report, or medical document is shared,\n" +
            "   summarize it in plain, simple language a non-medical person can understand — what\n" +
            "   the medicines/tests are generally for — without replacing the advice of their\n" +
            "   doctor or pharmacist.\n" +
            "\n" +
            "Rules:\n" +
            "- Keep replies concise (3-6 sentences) unless the user asks for detail.\n" +
            "- Always add a short safety note when discussing symptoms or medication.\n" +
            "- If the user describes severe symptoms (heavy bleeding, severe pain, no fetal\n" +
            "  movement, difficulty breathing, chest pain), tell them to seek emergency care\n" +
            "  immediately, first and clearly, before anything else.\n" +
            "- Never claim to be a licensed doctor. You are an informational assistant only.\n" +
            "- Match the user's tone — light for casual chat, gentle and clear for health topics.";

    public static final String MEDICAL_DISCLAIMER =
            "Disclaimer: HappiMoM Assistant provides general informational and wellness support. It does not replace professional medical advice, clinical diagnosis, or treatment. Always consult your OB/GYN or healthcare provider for personal medical concerns.";

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.model-url:https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent}")
    private String geminiModelUrl;

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model-url:https://api.groq.com/openai/v1/chat/completions}")
    private String groqModelUrl;

    @Value("${groq.model:qwen/qwen3.8-27b}")
    private String groqModel;

    private final ObjectMapper objectMapper;
    private final WebClient webClient;

    public ChatAiService() {
        this.objectMapper = new ObjectMapper();
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();
    }

    /**
     * Reactive streaming chat endpoint returning Flux<String> tokens as Server-Sent Events.
     * Prioritizes Groq for plain text messages, and Gemini for files/images.
     */
    public Flux<String> chatStream(ChatRequestDTO request) {
        if (request == null || ((request.getMessage() == null || request.getMessage().trim().isEmpty())
                && (request.getFileBase64() == null || request.getFileBase64().trim().isEmpty()))) {
            return Flux.error(new IllegalArgumentException("Message or file attachment cannot be empty."));
        }

        String userMessage = request.getMessage() != null ? request.getMessage().trim() : "";
        String fileBase64 = request.getFileBase64();
        String fileMimeType = request.getFileMimeType() != null ? request.getFileMimeType() : "image/jpeg";
        List<ChatTurnDTO> history = request.getHistory() != null ? request.getHistory() : Collections.emptyList();

        boolean hasFile = (fileBase64 != null && !fileBase64.isBlank());
        String groqKey = getEffectiveGroqKey();
        String geminiKey = getEffectiveGeminiKey();

        if ((groqKey == null || groqKey.isBlank()) && (geminiKey == null || geminiKey.isBlank())) {
            return Flux.error(new IllegalStateException(
                    "Conversational AI requires an API key. Please add GEMINI_API_KEY (from aistudio.google.com) or GROQ_API_KEY to your Backend/.env file."));
        }

        if (hasFile) {
            // 1. File Attached: Google Gemini is primary (multimodal vision capabilities)
            if (geminiKey != null && !geminiKey.isBlank()) {
                log.info("Streaming conversational AI (File attached): Invoking primary Google Gemini...");
                Flux<String> geminiStream = streamGemini(geminiKey, userMessage, fileBase64, fileMimeType, history);
                if (groqKey != null && !groqKey.isBlank()) {
                    return geminiStream.onErrorResume(ex -> {
                        log.warn("Gemini streaming failed ({}). Attempting fallback to Groq...", ex.getMessage());
                        return streamGroq(groqKey, userMessage, history);
                    });
                }
                return geminiStream;
            } else if (groqKey != null && !groqKey.isBlank()) {
                log.info("Gemini key not configured; falling back to Groq stream for text prompt...");
                return streamGroq(groqKey, userMessage, history);
            }
        } else {
            // 2. Plain Text: Groq is primary (ultra-fast sub-second responses)
            if (groqKey != null && !groqKey.isBlank()) {
                log.info("Streaming conversational AI (Plain text): Invoking primary Groq ({})...", getEffectiveGroqModel());
                Flux<String> groqStream = streamGroq(groqKey, userMessage, history);
                if (geminiKey != null && !geminiKey.isBlank()) {
                    return groqStream.onErrorResume(ex -> {
                        log.warn("Groq streaming failed ({}). Attempting fallback to Gemini...", ex.getMessage());
                        return streamGemini(geminiKey, userMessage, null, null, history);
                    });
                }
                return groqStream;
            } else if (geminiKey != null && !geminiKey.isBlank()) {
                log.info("Groq key not configured; using Google Gemini stream as primary...");
                return streamGemini(geminiKey, userMessage, null, null, history);
            }
        }

        return Flux.error(new RuntimeException("Unable to establish conversational AI stream. Please verify your API keys in Backend/.env."));
    }

    /**
     * Backward-compatible synchronous/non-streaming chat method returning ChatResponseDTO.
     */
    public ChatResponseDTO chat(ChatRequestDTO request) {
        if (request == null || ((request.getMessage() == null || request.getMessage().trim().isEmpty())
                && (request.getFileBase64() == null || request.getFileBase64().trim().isEmpty()))) {
            throw new IllegalArgumentException("Message or file attachment cannot be empty.");
        }

        String userMessage = request.getMessage() != null ? request.getMessage().trim() : "";
        String fileBase64 = request.getFileBase64();
        String fileMimeType = request.getFileMimeType() != null ? request.getFileMimeType() : "image/jpeg";
        List<ChatTurnDTO> history = request.getHistory() != null ? request.getHistory() : Collections.emptyList();

        boolean hasFile = (fileBase64 != null && !fileBase64.isBlank());
        String groqKey = getEffectiveGroqKey();
        String geminiKey = getEffectiveGeminiKey();

        if (hasFile) {
            // Primary: Gemini for file/image analysis
            if (geminiKey != null && !geminiKey.isBlank()) {
                try {
                    log.info("Invoking primary conversational AI (file attached): Google Gemini...");
                    String reply = callGemini(geminiKey, userMessage, fileBase64, fileMimeType, history);
                    return ChatResponseDTO.builder()
                            .reply(reply)
                            .modelUsed("Google Gemini")
                            .disclaimer(MEDICAL_DISCLAIMER)
                            .build();
                } catch (Exception ex) {
                    log.warn("Gemini call failed ({}). Attempting fallback to Groq...", ex.getMessage());
                }
            }
            if (groqKey != null && !groqKey.isBlank()) {
                try {
                    log.info("Invoking fallback conversational AI: Groq ({})...", getEffectiveGroqModel());
                    String reply = callGroq(groqKey, userMessage, history);
                    return ChatResponseDTO.builder()
                            .reply(reply)
                            .modelUsed("Groq (" + getEffectiveGroqModel() + ")")
                            .disclaimer(MEDICAL_DISCLAIMER)
                            .build();
                } catch (Exception ex) {
                    log.error("Groq fallback call also failed: {}", ex.getMessage());
                    throw new RuntimeException("Conversational AI service temporarily unavailable: " + ex.getMessage());
                }
            }
        } else {
            // Primary: Groq for plain text chat
            if (groqKey != null && !groqKey.isBlank()) {
                try {
                    log.info("Invoking primary conversational AI (plain text): Groq ({})...", getEffectiveGroqModel());
                    String reply = callGroq(groqKey, userMessage, history);
                    return ChatResponseDTO.builder()
                            .reply(reply)
                            .modelUsed("Groq (" + getEffectiveGroqModel() + ")")
                            .disclaimer(MEDICAL_DISCLAIMER)
                            .build();
                } catch (Exception ex) {
                    log.warn("Groq call failed ({}). Attempting fallback to Gemini...", ex.getMessage());
                }
            }
            if (geminiKey != null && !geminiKey.isBlank()) {
                try {
                    log.info("Invoking fallback conversational AI: Google Gemini...");
                    String reply = callGemini(geminiKey, userMessage, null, null, history);
                    return ChatResponseDTO.builder()
                            .reply(reply)
                            .modelUsed("Google Gemini")
                            .disclaimer(MEDICAL_DISCLAIMER)
                            .build();
                } catch (Exception ex) {
                    log.error("Gemini fallback call also failed: {}", ex.getMessage());
                    throw new RuntimeException("Conversational AI service temporarily unavailable: " + ex.getMessage());
                }
            }
        }

        throw new IllegalStateException("Conversational AI requires an API key. Please add GEMINI_API_KEY (free from aistudio.google.com) or GROQ_API_KEY to your Backend/.env file.");
    }

    public ChatResponseDTO chatNonStreaming(ChatRequestDTO request) {
        return chat(request);
    }

    // =========================================================================
    // Groq Handlers (Streaming & Non-Streaming via WebClient)
    // =========================================================================

    private Flux<String> streamGroq(String apiKey, String message, List<ChatTurnDTO> history) {
        String endpoint = getEffectiveGroqUrl();
        Map<String, Object> payload = buildGroqPayload(message, history, true);

        return webClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.ALL)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("Groq API error (" + resp.statusCode() + "): " + body))))
                .bodyToFlux(String.class)
                .flatMap(raw -> Flux.fromArray(raw.split("\r?\n")))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith(":"))
                .map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
                .takeWhile(data -> !"[DONE]".equals(data))
                .mapNotNull(this::extractGroqDelta)
                .filter(text -> !text.isEmpty());
    }

    private String extractGroqDelta(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode content = root.at("/choices/0/delta/content");
            if (!content.isMissingNode() && !content.isNull()) {
                return content.asText();
            }
        } catch (Exception e) {
            log.debug("Skipping non-JSON Groq chunk: {}", json);
        }
        return null;
    }

    private String callGroq(String apiKey, String message, List<ChatTurnDTO> history) {
        String endpoint = getEffectiveGroqUrl();
        Map<String, Object> payload = buildGroqPayload(message, history, false);

        String responseBody = webClient.post()
                .uri(endpoint)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("Groq API error (" + resp.statusCode() + "): " + body))))
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(20));

        if (responseBody != null) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode textNode = root.at("/choices/0/message/content");
                if (!textNode.isMissingNode() && !textNode.isNull()) {
                    return textNode.asText().trim();
                }
            } catch (Exception e) {
                log.error("Failed to parse Groq response: {}", e.getMessage());
            }
        }
        throw new RuntimeException("Unexpected empty response from Groq API");
    }

    private Map<String, Object> buildGroqPayload(String message, List<ChatTurnDTO> history, boolean stream) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        for (ChatTurnDTO turn : history) {
            if (turn.getText() == null || turn.getText().isBlank()) continue;
            String role = "model".equalsIgnoreCase(turn.getRole()) || "assistant".equalsIgnoreCase(turn.getRole())
                    ? "assistant"
                    : "user";
            messages.add(Map.of("role", role, "content", turn.getText()));
        }

        messages.add(Map.of("role", "user", "content", message != null && !message.isBlank() ? message : "Hello!"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", getEffectiveGroqModel());
        payload.put("messages", messages);
        payload.put("temperature", 0.7);
        payload.put("max_tokens", 800);
        if (stream) {
            payload.put("stream", true);
        }
        return payload;
    }

    // =========================================================================
    // Gemini Handlers (Streaming & Non-Streaming via WebClient)
    // =========================================================================

    private Flux<String> streamGemini(String apiKey, String message, String fileBase64, String mimeType, List<ChatTurnDTO> history) {
        String endpoint = getEffectiveGeminiStreamUrl(apiKey);
        Map<String, Object> payload = buildGeminiPayload(message, fileBase64, mimeType, history);

        return webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM, MediaType.ALL)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("Gemini API error (" + resp.statusCode() + "): " + body))))
                .bodyToFlux(String.class)
                .flatMap(raw -> Flux.fromArray(raw.split("\r?\n")))
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith(":"))
                .map(line -> line.startsWith("data:") ? line.substring(5).trim() : line)
                .mapNotNull(this::extractGeminiDelta)
                .filter(text -> !text.isEmpty());
    }

    private String extractGeminiDelta(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (!textNode.isMissingNode() && !textNode.isNull()) {
                return textNode.asText();
            }
        } catch (Exception e) {
            log.debug("Skipping non-JSON Gemini chunk: {}", json);
        }
        return null;
    }

    private String callGemini(String apiKey, String message, String fileBase64, String mimeType, List<ChatTurnDTO> history) {
        String endpoint = getEffectiveGeminiUrlWithKey(apiKey);
        Map<String, Object> payload = buildGeminiPayload(message, fileBase64, mimeType, history);

        String responseBody = webClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .bodyValue(payload)
                .retrieve()
                .onStatus(HttpStatusCode::isError, resp ->
                        resp.bodyToMono(String.class).flatMap(body ->
                                Mono.error(new RuntimeException("Gemini API error (" + resp.statusCode() + "): " + body))))
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(25));

        if (responseBody != null) {
            try {
                JsonNode root = objectMapper.readTree(responseBody);
                JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
                if (!textNode.isMissingNode() && !textNode.isNull()) {
                    return textNode.asText().trim();
                }
            } catch (Exception e) {
                log.error("Failed to parse Gemini response: {}", e.getMessage());
            }
        }
        throw new RuntimeException("Unexpected empty response from Gemini API");
    }

    private Map<String, Object> buildGeminiPayload(String message, String fileBase64, String mimeType, List<ChatTurnDTO> history) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", SYSTEM_PROMPT))
        );

        List<Map<String, Object>> contents = new ArrayList<>();

        for (ChatTurnDTO turn : history) {
            if (turn.getText() == null || turn.getText().isBlank()) continue;
            String role = "model".equalsIgnoreCase(turn.getRole()) || "assistant".equalsIgnoreCase(turn.getRole())
                    ? "model"
                    : "user";
            contents.add(Map.of(
                    "role", role,
                    "parts", List.of(Map.of("text", turn.getText()))
            ));
        }

        List<Map<String, Object>> currentParts = new ArrayList<>();
        if (message != null && !message.isBlank()) {
            currentParts.add(Map.of("text", message));
        }
        if (fileBase64 != null && !fileBase64.isBlank()) {
            String cleanBase64 = fileBase64;
            if (cleanBase64.contains(",")) {
                cleanBase64 = cleanBase64.substring(cleanBase64.indexOf(",") + 1);
            }
            currentParts.add(Map.of(
                    "inline_data", Map.of(
                            "mime_type", mimeType != null ? mimeType : "image/jpeg",
                            "data", cleanBase64
                    )
            ));
        }
        if (currentParts.isEmpty()) {
            currentParts.add(Map.of("text", "Please analyze this medical document."));
        }

        contents.add(Map.of(
                "role", "user",
                "parts", currentParts
        ));

        Map<String, Object> payload = new HashMap<>();
        payload.put("system_instruction", systemInstruction);
        payload.put("contents", contents);
        payload.put("generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", 800
        ));

        return payload;
    }

    // =========================================================================
    // URL & API Key Resolution
    // =========================================================================

    private String getEffectiveGeminiKey() {
        if (geminiApiKey != null && !geminiApiKey.trim().isEmpty() && !geminiApiKey.equalsIgnoreCase("your_gemini_api_key_here")) {
            return geminiApiKey.trim();
        }
        String sysProp = System.getProperty("GEMINI_API_KEY");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv("GEMINI_API_KEY");
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return null;
    }

    private String getEffectiveGeminiUrl() {
        if (geminiModelUrl != null && !geminiModelUrl.trim().isEmpty()) {
            return geminiModelUrl.trim();
        }
        String sysProp = System.getProperty("GEMINI_MODEL_URL");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv("GEMINI_MODEL_URL");
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    }

    private String getEffectiveGeminiStreamUrl(String apiKey) {
        String url = getEffectiveGeminiUrl();
        if (url.contains(":generateContent")) {
            url = url.replace(":generateContent", ":streamGenerateContent");
        } else if (!url.contains(":streamGenerateContent")) {
            url += ":streamGenerateContent";
        }
        if (!url.contains("alt=sse")) {
            url += (url.contains("?") ? "&" : "?") + "alt=sse";
        }
        if (!url.contains("key=")) {
            url += (url.contains("?") ? "&" : "?") + "key=" + apiKey;
        }
        return url;
    }

    private String getEffectiveGeminiUrlWithKey(String apiKey) {
        String url = getEffectiveGeminiUrl();
        if (!url.contains("key=")) {
            url += (url.contains("?") ? "&" : "?") + "key=" + apiKey;
        }
        return url;
    }

    private String getEffectiveGroqKey() {
        if (groqApiKey != null && !groqApiKey.trim().isEmpty() && !groqApiKey.equalsIgnoreCase("your_groq_api_key_here")) {
            return groqApiKey.trim();
        }
        String sysProp = System.getProperty("GROQ_API_KEY");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv("GROQ_API_KEY");
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return null;
    }

    private String getEffectiveGroqUrl() {
        if (groqModelUrl != null && !groqModelUrl.trim().isEmpty()) {
            return groqModelUrl.trim();
        }
        String sysProp = System.getProperty("GROQ_MODEL_URL");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv("GROQ_MODEL_URL");
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return "https://api.groq.com/openai/v1/chat/completions";
    }

    private String getEffectiveGroqModel() {
        if (groqModel != null && !groqModel.trim().isEmpty()) {
            return groqModel.trim();
        }
        String sysProp = System.getProperty("GROQ_MODEL");
        if (sysProp != null && !sysProp.isBlank()) return sysProp.trim();
        String envVar = System.getenv("GROQ_MODEL");
        if (envVar != null && !envVar.isBlank()) return envVar.trim();
        return "qwen/qwen3.8-27b";
    }
}
