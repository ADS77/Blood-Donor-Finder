package com.bd.blooddonerfinder.kafka.consumer;

import com.bd.blooddonerfinder.model.User;
import com.bd.blooddonerfinder.payload.message.DonorNotificationMessage;
import com.bd.blooddonerfinder.payload.request.DonorSearchRequest;
import com.bd.blooddonerfinder.service.NotificationManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class DonorListConsumer {

    private final ObjectMapper objectMapper;
    private final ExecutorService executorService;
    private final NotificationManager notificationManager;


    public DonorListConsumer(ObjectMapper objectMapper,
                             NotificationManager notificationManager) {
        this.objectMapper = objectMapper;
        this.notificationManager = notificationManager;
        this.executorService = Executors.newFixedThreadPool(10);
    }

    @KafkaListener(
            topics = "${kafka.topics.eligible-donors}",
            groupId = "${kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeDonorNotification(
            @Payload String payload,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(value = "X-Notification-ID", required = false) String notificationId,
            Acknowledgment acknowledgment
    ){
        try {
            log.info("Received notification message from topic: {}, key: {}, notification ID: {}", topic, key, notificationId);

            DonorNotificationMessage notificationMessage = objectMapper.readValue(payload, DonorNotificationMessage.class);
            DonorSearchRequest searchRequest = notificationMessage.getSearchRequest();
            List<User> eligibleDonors = notificationMessage.getEligibleDonors();
            log.debug("Processing notification with {} donors for blood group: {}, emergency level: {}",
                    eligibleDonors.size(),
                    searchRequest.getBloodGroup() != null ? searchRequest.getBloodGroup().getDisplayName() : "UNKNOWN",
                    searchRequest.getEmergencyLevel());
            processNotifications(eligibleDonors, searchRequest);

        } catch (JsonMappingException e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void processNotifications(List<User> eligibleDonors, DonorSearchRequest searchRequest) {
        log.info("Processing batch of {} donors for request from hospital: {}",
                eligibleDonors.size(), searchRequest.getHospitalName());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for(User donor : eligibleDonors){
          CompletableFuture<Void> future = CompletableFuture.runAsync(() ->{
              try {
                  notificationManager.notifyByMail(eligibleDonors, searchRequest);
              } catch (Exception e) {
                  log.error("Failed to send email to donor: {}", donor.getName());
              }
          }, executorService);
          futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        log.debug("Completed sending {} email notification fro blood group:{}",
                eligibleDonors.size(),
                searchRequest.getBloodGroup() != null ? searchRequest.getBloodGroup().getDisplayName() : "UNKNOWN");
    }


}
