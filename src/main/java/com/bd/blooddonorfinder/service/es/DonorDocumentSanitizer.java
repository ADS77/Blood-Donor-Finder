package com.bd.blooddonorfinder.service.es;

import com.bd.blooddonorfinder.model.es.Location;
import com.bd.blooddonorfinder.model.es.documents.DonorSearchDocument;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Strips PII and fine-grained location data from donor documents before they are
 * returned by public search endpoints. Filtering still uses the exact coordinates
 * server-side; only the response payload is sanitized.
 */
public final class DonorDocumentSanitizer {

    /** ~0.01 degree ≈ 1.1 km precision is enough for "nearby" display purposes */
    private static final int COORD_SCALE = 2;

    private DonorDocumentSanitizer() {
    }

    public static DonorSearchDocument sanitize(DonorSearchDocument doc) {
        if (doc == null) {
            return null;
        }
        // contact details are never exposed; contact happens through the notify flow
        doc.setEmail(null);
        doc.setPhone(null);

        Location location = doc.getLocation();
        if (location != null) {
            location.setAddress(null);
            location.setZipcode(null);
            location.setLatitude(round(location.getLatitude()));
            location.setLongitude(round(location.getLongitude()));
        }
        return doc;
    }

    public static void sanitizeAll(List<DonorSearchDocument> docs) {
        if (docs == null) {
            return;
        }
        docs.forEach(DonorDocumentSanitizer::sanitize);
    }

    private static double round(double value) {
        return BigDecimal.valueOf(value).setScale(COORD_SCALE, RoundingMode.HALF_UP).doubleValue();
    }
}
