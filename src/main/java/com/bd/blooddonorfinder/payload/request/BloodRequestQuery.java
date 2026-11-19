package com.bd.blooddonorfinder.payload.request;

import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.enums.BloodRequestScope;

/**
 * Read-side filters for the blood-request list. Kept separate from the
 * persistence query so the service can apply scope rules (which statuses a
 * caller may see) before the repository sees anything.
 */
public record BloodRequestQuery(
        BloodRequestScope scope,
        RequestStatus status,
        BloodGroup bloodGroup,
        RequestUrgency urgency
) {

    public BloodRequestQuery {
        if (scope == null) {
            scope = BloodRequestScope.OPEN;
        }
    }
}
