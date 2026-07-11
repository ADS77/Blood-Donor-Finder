package com.bd.blooddonorfinder.search.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;

import java.time.Instant;

@Document(indexName = "sondhan_donors", createIndex = false)
@Setting(shards = 8, replicas = 1)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DonorDocument {
    @Id
    private String donorId;

    @Field(type = FieldType.Keyword, name = "blood_group")
    private String bloodGroup;

    /**
     * geo_point field — supports geo_distance queries and geo_distance sort.
     * Spring Data ES maps GeoPoint to {"lat":..,"lon":..} in ES.
     */
    @GeoPointField
    @Field(name = "location")
    private GeoPoint location;

    @Field(type = FieldType.Boolean, name = "is_available")
    private boolean isAvailable;

    @Field(type = FieldType.Boolean, name = "is_eligible")
    private boolean isEligible;

    @Field(type = FieldType.Boolean, name = "is_verified")
    private boolean isVerified;

    @Field(type = FieldType.Float, name = "trust_score")
    private float trustScore;

    @Field(type = FieldType.Integer, name = "total_donations")
    private int totalDonations;

    @Field(type = FieldType.Float, name = "donation_frequency")
    private float donationFrequency;

    @Field(type = FieldType.Float, name = "availability_reliability")
    private float availabilityReliability;

    @Field(type = FieldType.Keyword, name = "org_id")
    private String orgId;

    @Field(type = FieldType.Boolean, name = "has_unresolved_abuse_report")
    private boolean hasUnresolvedAbuseReport;

    @Field(type = FieldType.Boolean, name = "availability_stale")
    private boolean availabilityStale;

    /**
     * org_boundary — geo_shape field for org-scoped geo-fence filtering.
     * Stored as a GeoJSON polygon. Only set when org defines a boundary.
     */
    @Field(type = FieldType.Object, name = "org_boundary")
    private Object orgBoundary;

    @Field(type = FieldType.Date, name = "last_active_at", format = DateFormat.date_time)
    private Instant lastActiveAt;

    @Field(type = FieldType.Date, name = "last_donation_at", format = DateFormat.date_time)
    private Instant lastDonationAt;

    @Field(type = FieldType.Date, name = "updated_at", format = DateFormat.date_time)
    private Instant updatedAt;

    /** Proxy UUID for contact resolution — never expose real contact details */
    @Field(type = FieldType.Keyword, name = "masked_contact_id")
    private String maskedContactId;

    /** ES routing value — set to blood_group for shard-per-blood-group strategy */
    public String routingValue() {
        return bloodGroup;
    }
}
