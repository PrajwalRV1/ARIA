package com.company.interview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorWebSocketHandler extends TextWebSocketHandler {
    private final WebSocketSessionRegistry registry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session.getUri());
        registry.addOrchestratorSession(sessionId, session);
        log.info("Orchestrator WS connected: {}", sessionId);
        session.sendMessage(new TextMessage("{\"type\":\"ai_status\",\"data\":{\"status\":\"ready\",\"action\":\"AI Interviewer connected\"}}"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        log.debug("Orchestrator WS message: {}", payload);
        
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> messageData = objectMapper.readValue(payload, Map.class);
            String messageType = (String) messageData.get("type");
            
            if ("initialize_structured_interview".equals(messageType)) {
                handleInitializeStructuredInterview(session, messageData);
            } else {
                log.debug("Unhandled message type: {}", messageType);
            }
        } catch (Exception e) {
            log.error("Failed to parse orchestrator message: {}", e.getMessage(), e);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String sessionId = extractSessionId(session.getUri());
        registry.removeOrchestratorSession(sessionId, session);
        log.info("Orchestrator WS disconnected: {}", sessionId);
    }

    private String extractSessionId(URI uri) {
        // Expecting path like /ws/interview/{sessionId}
        String path = uri != null ? uri.getPath() : "";
        String[] parts = path.split("/");
        return parts.length > 3 ? parts[3] : "unknown";
    }

    private void handleInitializeStructuredInterview(WebSocketSession session, Map<String, Object> messageData) {
        try {
            String sessionId = extractSessionId(session.getUri());
            log.info("Initializing structured interview with Alex AI for session: {}", sessionId);
            
            // Extract candidate information from message
            Map<String, Object> data = (Map<String, Object>) messageData.get("data");
            String candidateName = data != null ? (String) data.get("candidateName") : "Candidate";
            String position = data != null ? (String) data.get("position") : "the position";
            
            // Send acknowledgment
            Map<String, Object> ackMessage = Map.of(
                "type", "interview_initialization_ack",
                "data", Map.of(
                    "message", "Alex AI interviewer initialized successfully",
                    "sessionId", sessionId,
                    "interviewer", "Alex AI"
                )
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(ackMessage)));
            
            // Send AI status update
            Map<String, Object> statusMessage = Map.of(
                "type", "ai_status",
                "data", Map.of(
                    "status", "active",
                    "action", "Alex is introducing himself"
                )
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(statusMessage)));
            
            // **Alex AI Self-Introduction First** - This is the key fix!
            String alexIntroduction = String.format(
                "Hey %s, thanks for joining me today. I'm Alex, and I'll be conducting your interview for the %s position. " +
                "I'm excited to learn more about your background and experience. How are you feeling today?", 
                candidateName, position
            );
            
            Map<String, Object> introductionMessage = Map.of(
                "type", "ai_speech",
                "data", Map.of(
                    "text", alexIntroduction,
                    "speaker", "Alex AI",
                    "message_type", "greeting",
                    "duration", 8000 // 8 seconds for introduction
                )
            );
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(introductionMessage)));
            
            // Wait a moment, then send the first actual interview question
            // This simulates Alex waiting for candidate's response to "How are you feeling today?"
            scheduleFirstQuestion(session, sessionId, candidateName);
            
            log.info("Alex AI introduced himself for session: {} to candidate: {}", sessionId, candidateName);
            
        } catch (Exception e) {
            log.error("Failed to initialize Alex AI interview: {}", e.getMessage(), e);
        }
    }
    
    private void scheduleFirstQuestion(WebSocketSession session, String sessionId, String candidateName) {
        // Schedule the first question after Alex's introduction
        new Thread(() -> {
            try {
                Thread.sleep(12000); // Wait 12 seconds for candidate to respond to "How are you feeling?"
                
                // Send AI status update
                Map<String, Object> statusMessage = Map.of(
                    "type", "ai_status",
                    "data", Map.of(
                        "status", "speaking",
                        "action", "Asking the first interview question"
                    )
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(statusMessage)));
                
                // Now send the first structured interview question
                Map<String, Object> questionMessage = Map.of(
                    "type", "structured_question",
                    "data", Map.of(
                        "question", "Great! Now, let's start with your background. Tell me about yourself and your experience in software development.",
                        "id", "intro_question_001",
                        "category", "Introduction",
                        "followUp", false,
                        "timeLimit", 300 // 5 minutes
                    )
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(questionMessage)));
                
                // Update AI status to listening
                Map<String, Object> listeningStatus = Map.of(
                    "type", "ai_status",
                    "data", Map.of(
                        "status", "listening",
                        "action", "Waiting for your response"
                    )
                );
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(listeningStatus)));
                
                log.info("First interview question sent for session: {}", sessionId);
                
            } catch (Exception e) {
                log.error("Failed to send scheduled first question: {}", e.getMessage(), e);
            }
        }).start();
    }

    public void broadcast(String sessionId, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            for (WebSocketSession s : registry.getOrchestratorSessions(sessionId)) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception e) {
            log.error("Failed to broadcast orchestrator message: {}", e.getMessage(), e);
        }
    }
}
