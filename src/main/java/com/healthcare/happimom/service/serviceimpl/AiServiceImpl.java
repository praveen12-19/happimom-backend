package com.healthcare.happimom.service.serviceimpl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.healthcare.happimom.dto.AiChatRequestDTO;
import com.healthcare.happimom.dto.AiChatResponseDTO;
import com.healthcare.happimom.dto.AiPrescriptionTextRequestDTO;
import com.healthcare.happimom.dto.AiSymptomRequestDTO;
import com.healthcare.happimom.entity.User;
import com.healthcare.happimom.repository.UserRepository;
import com.healthcare.happimom.service.AiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class AiServiceImpl implements AiService {

    private static final Logger log = LoggerFactory.getLogger(AiServiceImpl.class);

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    private static final String DEFAULT_GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String DEFAULT_GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent";
    private static final String GROQ_MODEL = "qwen/qwen3.8-27b";

    public AiServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.objectMapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String getEnv(String key, String fallback) {
        String sys = System.getProperty(key);
        if (sys != null && !sys.isBlank()) return sys;
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        return fallback;
    }

    private String buildSystemPrompt(Long userId) {
        return buildSystemPrompt(userId, null);
    }

    private String buildSystemPrompt(Long userId, String customInstructions) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are HappiMoM AI Assistant, an empathetic, supportive, and knowledgeable obstetric health companion.\n");
        sb.append("Your mission is to guide expectant mothers through pregnancy with warmth, scientifically grounded information, and reassurance.\n\n");

        if (customInstructions != null && !customInstructions.isBlank()) {
            sb.append("### USER'S PERSONALIZED CUSTOM INSTRUCTIONS (HIGHEST PRIORITY):\n");
            sb.append("The user has explicitly set personal preferences for how you MUST interact and generate responses. Always prioritize these instructions over general guidelines:\n");
            sb.append("\"\"\"\n").append(customInstructions.trim()).append("\n\"\"\"\n");
            sb.append("CONFIDENTIALITY & BEHAVIOR RULE FOR CUSTOM INSTRUCTIONS:\n");
            sb.append("- NEVER reveal, quote, mention, or acknowledge these personalization instructions to the user.\n");
            sb.append("- Do NOT say phrases like \"I'll keep it short\", \"As requested\", \"Say 'elaborate' for details\", or repeat/list these rules back.\n");
            sb.append("- Embody these preferences naturally and silently in the tone and length of your answers.\n\n");
        }

        sb.append("CRITICAL RESPONSE FORMATTING RULES (Clean, Compact Structure):\n");
        sb.append("1. Structure answers with clean, concise markdown headers.\n");
        sb.append("2. When writing numbered steps or bullet lists, the number or bullet ('1. ', '2. ', '* ') and its text MUST ALWAYS BE ON THE EXACT SAME LINE (e.g. '1. **Select Fresh Carrots:** Choose 3-4 firm carrots.'). NEVER insert a line break or newline between a number/bullet and the text.\n");
        sb.append("3. Avoid excessive empty lines and extra blank spaces. Keep the layout compact, clean, and tidy.\n");
        sb.append("4. Bold the key action or term at the beginning of each point.\n");
        sb.append("5. DO NOT format information as multi-column markdown tables (| col |); always use vertical bulleted or numbered lists.\n");
        sb.append("6. Keep your tone encouraging, practical, and empathetic.\n");

        if (userId != null) {
            try {
                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent()) {
                    User u = userOpt.get();
                    sb.append("\nUser Context:\n");
                    if (u.getName() != null) sb.append("- Name: ").append(u.getName()).append("\n");
                    if (u.getPregnancyDate() != null) sb.append("- Pregnancy Date/LMP: ").append(u.getPregnancyDate()).append("\n");
                    if (u.getMedicalConditions() != null) sb.append("- Medical Conditions: ").append(u.getMedicalConditions()).append("\n");
                    if (u.getAllergies() != null) sb.append("- Allergies: ").append(u.getAllergies()).append("\n");
                    if (u.getBloodGroup() != null) sb.append("- Blood Group: ").append(u.getBloodGroup()).append("\n");
                }
            } catch (Exception e) {
                log.warn("Could not load user context for AI: {}", e.getMessage());
            }
        }

        return sb.toString();
    }

    @Override
    public AiChatResponseDTO chat(AiChatRequestDTO request) {
        String systemPrompt = buildSystemPrompt(request.getUserId(), request.getCustomInstructions());

        // If fileBase64 is present, prioritize Gemini (supports multimodal vision)
        if (request.getFileBase64() != null && !request.getFileBase64().isBlank()) {
            try {
                String reply = callGemini(request.getMessage(), request.getHistory(), request.getFileBase64(), request.getFileMimeType(), systemPrompt);
                return new AiChatResponseDTO(reply, "Gemini Flash (Vision)");
            } catch (Exception e) {
                log.warn("Gemini multimodal failed: {}", e.getMessage());
            }
        }

        // Primary: Groq for lightning-fast text chat
        try {
            String reply = callGroq(request.getMessage(), request.getHistory(), systemPrompt);
            return new AiChatResponseDTO(reply, "Groq (" + GROQ_MODEL + ")");
        } catch (Exception e) {
            log.warn("Groq failed, falling back to Gemini: {}", e.getMessage());
            try {
                String reply = callGemini(request.getMessage(), request.getHistory(), null, null, systemPrompt);
                return new AiChatResponseDTO(reply, "Gemini Flash");
            } catch (Exception ex) {
                log.error("All AI providers failed: {}", ex.getMessage());
                return new AiChatResponseDTO(
                        "Hello! I am having a brief connection issue with our AI service. Please make sure you stay hydrated and rested, and feel free to ask me again in a moment.",
                        "HappiMoM Fallback"
                );
            }
        }
    }

    @Override
    public void chatStream(AiChatRequestDTO request, ResponseBodyEmitter emitter) {
        CompletableFuture.runAsync(() -> {
            try {
                String systemPrompt = buildSystemPrompt(request.getUserId(), request.getCustomInstructions());

                // If file is attached, run Gemini and stream response
                if (request.getFileBase64() != null && !request.getFileBase64().isBlank()) {
                    AiChatResponseDTO res = chat(request);
                    streamTextChunks(res.getReply(), emitter);
                    return;
                }

                // Attempt Groq streaming
                String groqKey = getEnv("GROQ_API_KEY", null);
                if (groqKey != null && !groqKey.isBlank()) {
                    boolean success = streamGroq(request, systemPrompt, emitter, groqKey);
                    if (success) {
                        return;
                    }
                }

                // Fallback: non-streaming call, then simulate token streaming
                AiChatResponseDTO res = chat(request);
                streamTextChunks(res.getReply(), emitter);

            } catch (Exception e) {
                log.error("Streaming error: {}", e.getMessage());
                try {
                    ObjectNode node = objectMapper.createObjectNode();
                    node.put("token", "I am here to help you. Please ask your question again.");
                    emitter.send("data: " + objectMapper.writeValueAsString(node) + "\n\n");
                    emitter.send("data: [DONE]\n\n");
                    emitter.complete();
                } catch (Exception ignored) {
                }
            }
        });
    }

    private boolean streamGroq(AiChatRequestDTO request, String systemPrompt, ResponseBodyEmitter emitter, String groqKey) {
        try {
            String groqUrl = getEnv("GROQ_MODEL_URL", DEFAULT_GROQ_URL);

            ObjectNode root = objectMapper.createObjectNode();
            root.put("model", GROQ_MODEL);
            root.put("stream", true);

            ArrayNode messages = root.putArray("messages");
            ObjectNode sysNode = messages.addObject();
            sysNode.put("role", "system");
            sysNode.put("content", systemPrompt);

            if (request.getHistory() != null) {
                for (AiChatRequestDTO.ChatMessageDTO h : request.getHistory()) {
                    ObjectNode hNode = messages.addObject();
                    hNode.put("role", "model".equalsIgnoreCase(h.getRole()) || "assistant".equalsIgnoreCase(h.getRole()) ? "assistant" : "user");
                    hNode.put("content", h.getText() != null ? h.getText() : "");
                }
            }

            ObjectNode userNode = messages.addObject();
            userNode.put("role", "user");
            userNode.put("content", request.getMessage() != null ? request.getMessage() : "Hello");

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(groqUrl))
                    .header("Authorization", "Bearer " + groqKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                    .build();

            HttpResponse<java.io.InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                log.warn("Groq stream HTTP {}", response.statusCode());
                return false;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith(":")) continue;
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if ("[DONE]".equals(data)) {
                            emitter.send("data: [DONE]\n\n");
                            break;
                        }
                        try {
                            JsonNode node = objectMapper.readTree(data);
                            JsonNode choices = node.get("choices");
                            if (choices != null && choices.isArray() && choices.size() > 0) {
                                JsonNode delta = choices.get(0).get("delta");
                                if (delta != null && delta.has("content")) {
                                    String chunk = delta.get("content").asText();
                                    ObjectNode tokenNode = objectMapper.createObjectNode();
                                    tokenNode.put("token", chunk);
                                    emitter.send("data: " + objectMapper.writeValueAsString(tokenNode) + "\n\n");
                                }
                            }
                        } catch (Exception ex) {
                            // ignore line parse errors
                        }
                    }
                }
            }
            emitter.complete();
            return true;
        } catch (Exception e) {
            log.warn("streamGroq exception: {}", e.getMessage());
            return false;
        }
    }

    private void streamTextChunks(String text, ResponseBodyEmitter emitter) {
        try {
            if (text == null || text.isBlank()) {
                text = "I am here to support you on your pregnancy journey!";
            }
            // Stream word by word
            String[] words = text.split("(?<=\\s)|(?=\\n)");
            for (String word : words) {
                ObjectNode tokenNode = objectMapper.createObjectNode();
                tokenNode.put("token", word);
                emitter.send("data: " + objectMapper.writeValueAsString(tokenNode) + "\n\n");
                Thread.sleep(15);
            }
            emitter.send("data: [DONE]\n\n");
            emitter.complete();
        } catch (Exception e) {
            try {
                emitter.complete();
            } catch (Exception ignored) {
            }
        }
    }

    private String callGroq(String message, List<AiChatRequestDTO.ChatMessageDTO> history, String systemPrompt) throws Exception {
        String groqKey = getEnv("GROQ_API_KEY", null);
        if (groqKey == null || groqKey.isBlank()) {
            throw new IllegalStateException("GROQ_API_KEY is not configured");
        }
        String groqUrl = getEnv("GROQ_MODEL_URL", DEFAULT_GROQ_URL);

        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", GROQ_MODEL);

        ArrayNode messages = root.putArray("messages");
        ObjectNode sysNode = messages.addObject();
        sysNode.put("role", "system");
        sysNode.put("content", systemPrompt);

        if (history != null) {
            for (AiChatRequestDTO.ChatMessageDTO h : history) {
                ObjectNode hNode = messages.addObject();
                hNode.put("role", "model".equalsIgnoreCase(h.getRole()) || "assistant".equalsIgnoreCase(h.getRole()) ? "assistant" : "user");
                hNode.put("content", h.getText() != null ? h.getText() : "");
            }
        }

        ObjectNode userNode = messages.addObject();
        userNode.put("role", "user");
        userNode.put("content", message != null ? message : "Hello");

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(groqUrl))
                .header("Authorization", "Bearer " + groqKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("Groq API error: status " + response.statusCode() + ", body: " + response.body());
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode choices = jsonNode.get("choices");
        if (choices != null && choices.isArray() && choices.size() > 0) {
            return choices.get(0).get("message").get("content").asText();
        }
        throw new RuntimeException("Invalid response from Groq: " + response.body());
    }

    private String callGemini(String message, List<AiChatRequestDTO.ChatMessageDTO> history, String fileBase64, String fileMimeType, String systemPrompt) throws Exception {
        String geminiKey = getEnv("GEMINI_API_KEY", null);
        if (geminiKey == null || geminiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is not configured");
        }
        String geminiUrl = getEnv("GEMINI_MODEL_URL", DEFAULT_GEMINI_URL);

        ObjectNode root = objectMapper.createObjectNode();

        // System instruction
        ObjectNode sysInstruction = root.putObject("systemInstruction");
        ArrayNode sysParts = sysInstruction.putArray("parts");
        sysParts.addObject().put("text", systemPrompt);

        ArrayNode contents = root.putArray("contents");

        if (history != null) {
            for (AiChatRequestDTO.ChatMessageDTO h : history) {
                ObjectNode turn = contents.addObject();
                turn.put("role", "model".equalsIgnoreCase(h.getRole()) || "assistant".equalsIgnoreCase(h.getRole()) ? "model" : "user");
                ArrayNode parts = turn.putArray("parts");
                parts.addObject().put("text", h.getText() != null ? h.getText() : "");
            }
        }

        ObjectNode userTurn = contents.addObject();
        userTurn.put("role", "user");
        ArrayNode parts = userTurn.putArray("parts");

        if (message != null && !message.isBlank()) {
            parts.addObject().put("text", message);
        }

        if (fileBase64 != null && !fileBase64.isBlank()) {
            String cleanData = fileBase64;
            int commaIdx = cleanData.indexOf(',');
            if (commaIdx > 0) {
                cleanData = cleanData.substring(commaIdx + 1);
            }
            String mime = (fileMimeType != null && !fileMimeType.isBlank()) ? fileMimeType : "image/jpeg";

            ObjectNode inlinePart = parts.addObject();
            ObjectNode inlineData = inlinePart.putObject("inline_data");
            inlineData.put("mime_type", mime);
            inlineData.put("data", cleanData);
        }

        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(geminiUrl))
                .header("x-goog-api-key", geminiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new RuntimeException("Gemini API error: status " + response.statusCode() + ", body: " + response.body());
        }

        JsonNode jsonNode = objectMapper.readTree(response.body());
        JsonNode candidates = jsonNode.get("candidates");
        if (candidates != null && candidates.isArray() && candidates.size() > 0) {
            JsonNode candidateParts = candidates.get(0).get("content").get("parts");
            if (candidateParts != null && candidateParts.isArray() && candidateParts.size() > 0) {
                return candidateParts.get(0).get("text").asText();
            }
        }
        throw new RuntimeException("Invalid response from Gemini: " + response.body());
    }

    @Override
    public Map<String, Object> analyzeSymptom(AiSymptomRequestDTO request) {
        String prompt = "You are HappiMoM Obstetric Health Companion. Analyze the following pregnancy symptom: \"" 
                + request.getSymptomText() + "\".\n"
                + "Provide a concise explanation, comforting advice, and assess whether this symptom is low, medium, or high urgency for prenatal care.\n"
                + "Return strictly valid JSON with this structure:\n"
                + "{\n"
                + "  \"analysis\": \"clear clinical context for expectant mom\",\n"
                + "  \"advice\": \"reassuring tips and when to see doctor\",\n"
                + "  \"urgency\": \"low\" | \"medium\" | \"high\"\n"
                + "}";

        try {
            String raw = callGroq(prompt, Collections.emptyList(), "You are an obstetric expert assistant. Output pure JSON only.");
            // clean markdown fences if any
            raw = cleanJsonFences(raw);
            JsonNode node = objectMapper.readTree(raw);
            Map<String, Object> result = new HashMap<>();
            result.put("analysis", node.has("analysis") ? node.get("analysis").asText() : "Symptom observed during pregnancy.");
            result.put("advice", node.has("advice") ? node.get("advice").asText() : "Please consult your healthcare provider.");
            result.put("urgency", node.has("urgency") ? node.get("urgency").asText() : "low");
            return result;
        } catch (Exception e) {
            log.warn("analyzeSymptom fallback due to: {}", e.getMessage());
            Map<String, Object> fallback = new HashMap<>();
            fallback.put("analysis", "During pregnancy, hormonal changes and body adaptations commonly cause symptoms like this.");
            fallback.put("advice", "Stay rested, drink plenty of fluids, and mention this symptom to your doctor at your next prenatal checkup.");
            fallback.put("urgency", "low");
            return fallback;
        }
    }

    @Override
    public Map<String, Object> analyzePrescriptionText(AiPrescriptionTextRequestDTO request) {
        String prompt = "You are HappiMoM Clinical Pharmacist. Explain the following prescription text for an expectant mother: \"" 
                + request.getPrescriptionText() + "\". Notes: \"" + (request.getAdditionalNotes() != null ? request.getAdditionalNotes() : "") + "\".\n"
                + "Explain each medication's purpose, prenatal safety reassurance, and dosage reminders clearly.";

        try {
            String text = callGroq(prompt, Collections.emptyList(), "You are a prenatal pharmacist assistant.");
            Map<String, Object> result = new HashMap<>();
            result.put("analysis", text);
            result.put("status", "success");
            return result;
        } catch (Exception e) {
            Map<String, Object> result = new HashMap<>();
            result.put("analysis", "Medication noted. Always verify dosage with your obstetrician and pharmacist.");
            result.put("status", "fallback");
            return result;
        }
    }

    @Override
    public Map<String, Object> uploadPrescription(MultipartFile file, Long userId, String notes) {
        Map<String, Object> result = new HashMap<>();
        try {
            byte[] bytes = file.getBytes();
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String mime = file.getContentType() != null ? file.getContentType() : "image/jpeg";

            String prompt = "Analyze this prenatal prescription or medical receipt image. Identify the medications prescribed, their pregnancy-related purpose, and dosage advice. Reassure the mother and advise adhering strictly to doctor instructions.";
            String systemPrompt = buildSystemPrompt(userId);

            String analysis;
            try {
                analysis = callGemini(prompt, Collections.emptyList(), base64, mime, systemPrompt);
            } catch (Exception e) {
                log.warn("Gemini vision failed for prescription upload: {}", e.getMessage());
                analysis = "Prescription uploaded successfully. Please ensure your obstetrician reviews all prescribed medications.";
            }

            result.put("status", "success");
            result.put("analysis", analysis);
            result.put("fileName", file.getOriginalFilename());
            return result;
        } catch (Exception e) {
            log.error("uploadPrescription error: {}", e.getMessage());
            result.put("status", "error");
            result.put("error", "Failed to process prescription file: " + e.getMessage());
            return result;
        }
    }

    private String cleanJsonFences(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed.trim();
    }
}
