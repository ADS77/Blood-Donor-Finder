package com.bd.blooddonerfinder.kafka.publisher;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.message.DonorNotificationMessage;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class DonorListPublisher {

    @Value("${kafka.topic.name}")
    private String topicName;

    private final KafkaTemplate<String, String>kafkaTemplate;
    private final ObjectMapper objectMapper;

    public DonorListPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public CompletableFuture<SendResult<String, String>> publishEligibleDonors(List<User>donors, DonorSearchRequest searchRequest){
        try {
            DonorNotificationMessage notificationMessage = new DonorNotificationMessage(searchRequest, donors);
            String payload = objectMapper.writeValueAsString(notificationMessage);
            String bloodGroup = searchRequest.getBloodGroup() != null ?
                    searchRequest.getBloodGroup().getDisplayName() : "UNKNOWN";
            String emergencyLevel = searchRequest.getEmergencyLevel() != null ?
                    searchRequest.getEmergencyLevel() : "MEDIUM";
            String messageKey = bloodGroup + "-" + emergencyLevel + "-" + System.currentTimeMillis();

            log.debug("Publishing {} eligible donors with emergency level: {}", donors.size(), emergencyLevel);
            Message<String> message = MessageBuilder
                    .withPayload(payload)
                    .setHeader(KafkaHeaders.TOPIC, topicName)
                    .setHeader(KafkaHeaders.KEY, messageKey)
                    .setHeader("X-Notification-ID", notificationMessage.getNotificationId())
                    .setHeader("X-Emergency-Level", emergencyLevel)
                    .setHeader("X-Blood-Group", bloodGroup)
                    .setHeader("X-Donor-Count", donors.size())
                    .build();

            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(message);

            future.whenComplete((result, ex) -> {
               if(ex == null){
                   log.debug("Successfully sent message to topic {} with offset {}", topicName, result.getRecordMetadata().offset());
               }
               else {
                   log.error("Failed to send donor list to kafka", ex);
               }
            });
            return future;
        } catch (JsonProcessingException e) {
            log.error("Error serializing donor list", e);
            throw new RuntimeException("Error publishing donor list to Kafka", e);
        }
    }

}
