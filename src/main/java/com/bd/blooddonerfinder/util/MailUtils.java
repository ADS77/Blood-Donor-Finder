package com.bd.blooddonerfinder.util;

import com.bd.blooddonerfinder.model.Location;

import java.util.regex.Pattern;

public class MailUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                    + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$");

    public static boolean isValidEmail(String email){
        if(email == null || email.isBlank()){
            return false;
        }
        return EMAIL_PATTERN
                .matcher(email)
                .matches();
    }

        public static String buildHtmlBody(String recipientName, String location, String phoneNumber, String organizationName) {
            return String.format("""
            <p>Dear %s,</p>
            
            <p>We urgently need blood donors at <strong>%s</strong> to help save lives. If you are able and willing to donate, your support would mean the world to those in critical need.</p>
            
            <p><strong>📍 Location:</strong> %s</p>
            
            <p>For more details or if you wish to confirm your availability, please call us directly at <strong>%s</strong>.</p>
            
            <p>Every drop counts. Thank you for making a difference!</p>
            
            <p>Best regards,<br/>
            %s</p>
            """,
                    safeNull(recipientName, "Donor"),
                    safeNull(location, "our center"),
                    safeNull(location, "our center"),
                    safeNull(phoneNumber, "N/A"),
                    safeNull(organizationName, "Blood Donation Team")
            );
        }



        private static String safeNull(String value, String defaultValue) {
            return value == null || value.trim().isEmpty() ? defaultValue : value;
        }



}
