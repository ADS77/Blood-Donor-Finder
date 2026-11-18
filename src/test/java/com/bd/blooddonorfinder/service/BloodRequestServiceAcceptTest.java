package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.NotificationType;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.RestApiResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Acceptance is a blood-request transition only. These tests pin down that the
 * accept use case owns no chat state and that chat availability is advertised
 * as a capability the client can act on later.
 */
@ExtendWith(MockitoExtension.class)
class BloodRequestServiceAcceptTest {

    @Mock
    private BloodRequestRepository bloodRequestRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private NotificationService notificationService;

    private BloodRequestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BloodRequestServiceImpl(bloodRequestRepository, userRepository, notificationService);
    }

    @Test
    void accept_claimsRequestAndAdvertisesChatWithoutCreatingIt() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, RequestStatus.PENDING);

        when(userRepository.findById(donor.getId())).thenReturn(Optional.of(donor));
        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(bloodRequestRepository.acceptAtomically(eq(request.getId()), eq(donor), any(Instant.class)))
                .thenReturn(1);

        RestApiResponse<BloodRequestViewDto> response = service.acceptRequest(request.getId(), donor.getId());

        BloodRequestViewDto view = response.getSuccess().getData();
        assertEquals(RequestStatus.ACCEPTED.name(), view.status());
        assertEquals(donor.getId(), view.acceptedDonorId());
        assertTrue(view.chatAvailable());
        assertEquals(RequestStatus.ACCEPTED, request.getStatus());
        assertEquals(donor, request.getAcceptedDonor());

        // The acceptance event points at the request, not at a chat that does
        // not exist yet. Chat creation is now an explicit chat-API operation.
        verify(notificationService).notify(eq(requester.getId()), eq(NotificationType.REQUEST_ACCEPTED),
                eq(request.getId()), anyMap());
    }

    @Test
    void accept_rejectsUnverifiedDonor() {
        User donor = user("Donor");
        donor.setIsVerified(false);
        when(userRepository.findById(donor.getId())).thenReturn(Optional.of(donor));

        AuthException error = assertThrows(AuthException.class,
                () -> service.acceptRequest(UUID.randomUUID(), donor.getId()));

        assertEquals(ErrorCode.USER_NOT_VERIFIED, error.getErrorCode());
        verifyNoInteractions(notificationService);
    }

    @Test
    void accept_losesGracefullyWhenAnotherDonorWon() {
        User requester = user("Requester");
        User donor = user("Donor");
        BloodRequest request = request(requester, RequestStatus.PENDING);

        when(userRepository.findById(donor.getId())).thenReturn(Optional.of(donor));
        when(bloodRequestRepository.findWithParticipantsById(request.getId())).thenReturn(Optional.of(request));
        when(bloodRequestRepository.acceptAtomically(eq(request.getId()), eq(donor), any(Instant.class)))
                .thenReturn(0);

        AuthException error = assertThrows(AuthException.class,
                () -> service.acceptRequest(request.getId(), donor.getId()));

        assertEquals(ErrorCode.REQUEST_ALREADY_ACCEPTED, error.getErrorCode());
        verify(notificationService, never()).notify(any(), any(), any(), anyMap());
    }

    @Test
    void accept_doesNotReportChatForCancelledRequest() {
        User requester = user("Requester");
        BloodRequest request = request(requester, RequestStatus.CANCELLED);

        assertFalse(BloodRequestViewDto.from(request).chatAvailable());
    }

    private static BloodRequest request(User requester, RequestStatus status) {
        BloodRequest request = new BloodRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(requester);
        request.setStatus(status);
        return request;
    }

    private static User user(String firstName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName(firstName);
        user.setLastName("User");
        user.setIsVerified(true);
        return user;
    }
}
