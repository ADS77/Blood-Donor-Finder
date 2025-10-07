package com.bd.blooddonerfinder.util;

import com.bd.blooddonerfinder.model.User;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class MailUtils {
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^(?=.{1,64}@)[A-Za-z0-9_-]+(\\.[A-Za-z0-9_-]+)*@"
                    + "[^-][A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)*(\\.[A-Za-z]{2,})$");
    @Value("${min.days.between.donation}")
    private static int MIN_GAP_DAYS;

    public static boolean isValidEmail(String email){
        if(email == null || email.isBlank()){
            return false;
        }
        return EMAIL_PATTERN
                .matcher(email)
                .matches();
    }

    public static String buildHtmlBody(String recipientName, String geoLocation, String phoneNumber, String organizationName) {
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
                    safeNull(geoLocation, "our center"),
                    safeNull(geoLocation, "our center"),
                    safeNull(phoneNumber, "N/A"),
                    safeNull(organizationName, "Blood Donation Team")
            );
    }

    private static String safeNull(String value, String defaultValue) {
            return value == null || value.trim().isEmpty() ? defaultValue : value;
    }

    public static List<User> filterEligibleDonors(List<User> donorList){
        LocalDate today = LocalDate.now();
        return donorList.stream()
                .filter(donor -> donor.getLastDonationDate() == null ||
                        donor.getLastDonationDate().isBefore(
                                today.minusDays(MIN_GAP_DAYS)))
                .collect(Collectors.toList());
    }



}
