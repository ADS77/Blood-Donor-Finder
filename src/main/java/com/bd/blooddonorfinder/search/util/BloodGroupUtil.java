package com.bd.blooddonorfinder.search.util;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
@Component
public class BloodGroupUtil {
    public static final Set<String> VALID_GROUPS = Set.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");

    public static final Set<String> RARE_GROUPS = Set.of("O-", "AB-", "B-", "A-");

    private static final Map<String, List<String>> COMPATIBILITY = Map.of(
            "AB+", List.of("AB+", "AB-", "A+", "A-", "B+", "B-", "O+", "O-"),
            "AB-", List.of("AB-", "A-", "B-", "O-"),
            "A+",  List.of("A+", "A-", "O+", "O-"),
            "A-",  List.of("A-", "O-"),
            "B+",  List.of("B+", "B-", "O+", "O-"),
            "B-",  List.of("B-", "O-"),
            "O+",  List.of("O+", "O-"),
            "O-",  List.of("O-")
    );

    public String normalize(String input) {
        if (input == null) return null;

        String trimmed = input.trim().toLowerCase();
        trimmed = trimmed.replaceAll("\\s+", " "); // collapse whitespace

        String upper = input.trim().toUpperCase();
        if (VALID_GROUPS.contains(upper)) return upper;

        return null;
    }

    public String normalizeOrThrow(String input) {
        String result = normalize(input);
        if (result == null) {
            throw new IllegalArgumentException(
                    "Unknown blood group: '" + input + "'. Expected one of: " + VALID_GROUPS);
        }
        return result;
    }

    public boolean isRare(String bloodGroup){
        return RARE_GROUPS.contains(bloodGroup);
    }

    public List<String> getCompatibleDonorGroups(String recipientGroup) {
        List<String> compatible = COMPATIBILITY.getOrDefault(recipientGroup, List.of());
        return compatible.stream()
                .filter(g -> !g.equals(recipientGroup))
                .toList();
    }

    public String compatibilityNote(String recipientGroup, String donorGroup) {
        return String.format("%s compatible (donor group: %s)", recipientGroup, donorGroup);
    }


}
