package com.bd.blooddonorfinder.model.enums;

/**
 * Kinds of user-uploaded documents. Each type pins its own bucket and
 * key folder so storage stays fine-grained and lifecycle rules can be
 * applied per bucket (e.g. medical retention, profile photo cleanup).
 */
public enum DocumentType {
    /** Small avatar images — bucket sondhan-profile, folder avatar/. */
    PROFILE_PHOTO("sondhan-profile", "avatar"),

    /** Medical: blood reports — bucket sondhan-medical, folder blood-reports/. */
    BLOOD_REPORT("sondhan-medical", "blood-reports"),

    /** Medical: diagnostic reports — bucket sondhan-medical, folder diagnostic-reports/. */
    DIAGNOSTIC_REPORT("sondhan-medical", "diagnostic-reports");

    private final String bucket;
    private final String folder;

    DocumentType(String bucket, String folder) {
        this.bucket = bucket;
        this.folder = folder;
    }

    public String getBucket() {
        return bucket;
    }

    public String getFolder() {
        return folder;
    }
}
