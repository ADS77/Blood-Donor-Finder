package com.bd.blooddonorfinder.model;

import com.bd.blooddonorfinder.model.enums.GeoStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Embeddable
@Data
@NoArgsConstructor
public class GeoLocation implements Serializable {
    @Column(length = 500)
    private String address;

    @NotNull(message = "City must not be null")
    @NotEmpty(message = "City must not be empty")
    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String district;

    @Column(length = 100)
    private String country;

    @Column(name = "latitude", precision = 10, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 6)
    private BigDecimal longitude;

    @Column(length = 20)
    private String zipcode;

    @Enumerated(EnumType.STRING)
    private GeoStatus geoStatus = GeoStatus.PENDING;

    private int geoRetryCount = 0;

    @Column(length = 500)
    private String geoLastError;
}
