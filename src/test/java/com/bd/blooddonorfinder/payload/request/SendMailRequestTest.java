package com.bd.blooddonorfinder.payload.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for the Kafka email payload contract.
 *
 * <p>{@link SendMailRequest} crosses the outbox and Kafka boundary as JSON and
 * is deserialized by the consumer with the application's {@code ObjectMapper}.
 * It must not depend on parameter-name introspection, which is not always
 * enabled in every build or runtime configuration.</p>
 */
class SendMailRequestTest {

    private static final String JSON = "{"
            + "\"mailFrom\":\"noreply@sondhan.test\","
            + "\"mailTo\":[\"donor@example.com\"],"
            + "\"subject\":\"Your verification code\","
            + "\"body\":\"Your code is 123456\","
            + "\"htmlContent\":true"
            + "}";

    @Test
    void deserializesWithPlainObjectMapper() throws Exception {
        assertDeserialized(new ObjectMapper().readValue(JSON, SendMailRequest.class));
    }

    @Test
    void deserializesWithApplicationStyleObjectMapper() throws Exception {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json().build();
        assertDeserialized(mapper.readValue(JSON, SendMailRequest.class));
    }

    @Test
    void roundTripsThroughJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SendMailRequest original = SendMailRequest.of(
                List.of("donor@example.com"), "noreply@sondhan.test",
                "Your verification code", "Your code is 123456");

        SendMailRequest restored = mapper.readValue(
                mapper.writeValueAsString(original), SendMailRequest.class);

        assertDeserialized(restored);
    }

    private void assertDeserialized(SendMailRequest request) {
        assertEquals(List.of("donor@example.com"), request.getMailTo());
        assertEquals("noreply@sondhan.test", request.getMailFrom());
        assertEquals("Your verification code", request.getSubject());
        assertEquals("Your code is 123456", request.getBody());
        assertTrue(request.isHtmlContent());
    }
}
