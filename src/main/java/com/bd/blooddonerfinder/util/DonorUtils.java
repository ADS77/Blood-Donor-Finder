package com.bd.blooddonerfinder.util;

import com.bd.blooddonerfinder.model.User;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.chrono.ChronoLocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public class DonorUtils {
    @Value("${min.days.between.donation}")
    private static int minGapDays;

    public static List<User> filterEligibleDonors(List<User> donorList) {
        LocalDateTime cutoffDateTime = LocalDate.now().minusDays(minGapDays).atStartOfDay();

        return donorList.stream()
                .filter(donor -> donor.getLastDonationDate() == null ||
                        donor.getLastDonationDate().isBefore(cutoffDateTime))
                .collect(Collectors.toList());
    }
}
