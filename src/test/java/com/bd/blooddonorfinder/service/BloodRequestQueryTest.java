package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.AuthException;
import com.bd.blooddonorfinder.exception.enums.ErrorCode;
import com.bd.blooddonorfinder.model.BloodRequest;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.BloodGroup;
import com.bd.blooddonorfinder.model.enums.RequestStatus;
import com.bd.blooddonorfinder.model.enums.RequestUrgency;
import com.bd.blooddonorfinder.payload.enums.BloodRequestScope;
import com.bd.blooddonorfinder.payload.request.BloodRequestQuery;
import com.bd.blooddonorfinder.payload.response.BloodRequestViewDto;
import com.bd.blooddonorfinder.payload.response.PageResponse;
import com.bd.blooddonorfinder.repository.BloodRequestRepository;
import com.bd.blooddonorfinder.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The list endpoint's scope is an authorization boundary, not a convenience
 * filter: MINE and ACCEPTED must pin the caller, OPEN must never do so.
 */
@ExtendWith(MockitoExtension.class)
class BloodRequestQueryTest {

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
    void listOpen_isThePendingFeedAndIgnoresCallerIdentity() {
        UUID caller = UUID.randomUUID();
        BloodRequest request = request(UUID.randomUUID(), null, RequestStatus.PENDING);
        when(bloodRequestRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pageOf(request));

        PageResponse<BloodRequestViewDto> response = service.listRequests(
                caller, new BloodRequestQuery(null, null, null, null), 0, 20);

        ArgumentCaptor<Collection<RequestStatus>> statuses = statusesCaptor();
        verify(bloodRequestRepository).search(statuses.capture(), isNull(), isNull(),
                isNull(), isNull(), any(Pageable.class));
        assertEquals(List.of(RequestStatus.PENDING), List.copyOf(statuses.getValue()));
        assertEquals(1, response.data().size());
        assertEquals(RequestStatus.PENDING.name(), response.data().get(0).status());
        assertEquals(0, response.page());
        assertEquals(1, response.totalElements());
    }

    @Test
    void listMine_scopesToTheRequesterAndIncludesAllStatuses() {
        UUID caller = UUID.randomUUID();
        BloodRequest request = request(caller, null, RequestStatus.CANCELLED);
        when(bloodRequestRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pageOf(request));

        service.listRequests(caller,
                new BloodRequestQuery(BloodRequestScope.MINE, null, null, null), 0, 20);

        ArgumentCaptor<Collection<RequestStatus>> statuses = statusesCaptor();
        verify(bloodRequestRepository).search(statuses.capture(), isNull(), isNull(),
                org.mockito.ArgumentMatchers.eq(caller), isNull(), any(Pageable.class));
        assertEquals(RequestStatus.values().length, statuses.getValue().size());
    }

    @Test
    void listAccepted_scopesToTheAcceptedDonor() {
        UUID caller = UUID.randomUUID();
        BloodRequest request = request(UUID.randomUUID(), caller, RequestStatus.ACCEPTED);
        when(bloodRequestRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(pageOf(request));

        service.listRequests(caller,
                new BloodRequestQuery(BloodRequestScope.ACCEPTED, null, null, null), 0, 20);

        ArgumentCaptor<Collection<RequestStatus>> statuses = statusesCaptor();
        verify(bloodRequestRepository).search(statuses.capture(), isNull(), isNull(),
                isNull(), org.mockito.ArgumentMatchers.eq(caller), any(Pageable.class));
        assertEquals(RequestStatus.values().length, statuses.getValue().size());
    }

    @Test
    void listOpen_rejectsStatusOutsideTheScopeWithoutQuerying() {
        UUID caller = UUID.randomUUID();

        PageResponse<BloodRequestViewDto> response = service.listRequests(caller,
                new BloodRequestQuery(BloodRequestScope.OPEN, RequestStatus.ACCEPTED, null, null), 0, 20);

        assertTrue(response.data().isEmpty());
        verifyNoInteractions(bloodRequestRepository);
    }

    @Test
    void listMine_appliesOptionalFiltersNarrowingTheScopedSet() {
        UUID caller = UUID.randomUUID();
        when(bloodRequestRepository.search(any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.listRequests(caller, new BloodRequestQuery(BloodRequestScope.MINE,
                RequestStatus.PENDING, BloodGroup.A_POSITIVE, RequestUrgency.URGENT), 0, 20);

        verify(bloodRequestRepository).search(
                org.mockito.ArgumentMatchers.eq(List.of(RequestStatus.PENDING)),
                org.mockito.ArgumentMatchers.eq(BloodGroup.A_POSITIVE),
                org.mockito.ArgumentMatchers.eq(RequestUrgency.URGENT),
                org.mockito.ArgumentMatchers.eq(caller), isNull(), any(Pageable.class));
    }

    @Test
    void getRequest_pendingIsVisibleToAnyAuthenticatedCaller() {
        BloodRequest request = request(UUID.randomUUID(), null, RequestStatus.PENDING);
        when(bloodRequestRepository.findWithParticipantsById(request.getId()))
                .thenReturn(Optional.of(request));

        BloodRequestViewDto view = service.getRequest(request.getId(), UUID.randomUUID());

        assertEquals(request.getId(), view.id());
        assertEquals(RequestStatus.PENDING.name(), view.status());
    }

    @Test
    void getRequest_claimedIsHiddenFromNonParticipants() {
        BloodRequest request = request(UUID.randomUUID(), UUID.randomUUID(), RequestStatus.ACCEPTED);
        when(bloodRequestRepository.findWithParticipantsById(request.getId()))
                .thenReturn(Optional.of(request));

        AuthException error = assertThrows(AuthException.class,
                () -> service.getRequest(request.getId(), UUID.randomUUID()));

        assertEquals(ErrorCode.REQUEST_NOT_FOUND, error.getErrorCode());
    }

    @Test
    void getRequest_claimedIsVisibleToRequesterAndAcceptedDonor() {
        UUID requesterId = UUID.randomUUID();
        UUID donorId = UUID.randomUUID();
        BloodRequest request = request(requesterId, donorId, RequestStatus.ACCEPTED);
        when(bloodRequestRepository.findWithParticipantsById(request.getId()))
                .thenReturn(Optional.of(request));

        assertEquals(donorId, service.getRequest(request.getId(), donorId).acceptedDonorId());
        assertEquals(requesterId, service.getRequest(request.getId(), requesterId).requester().id());
    }

    @Test
    void getRequest_unknownReadsAsNotFound() {
        UUID id = UUID.randomUUID();
        when(bloodRequestRepository.findWithParticipantsById(id)).thenReturn(Optional.empty());

        AuthException error = assertThrows(AuthException.class,
                () -> service.getRequest(id, UUID.randomUUID()));

        assertEquals(ErrorCode.REQUEST_NOT_FOUND, error.getErrorCode());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<Collection<RequestStatus>> statusesCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
    }

    private static Page<BloodRequest> pageOf(BloodRequest request) {
        return new PageImpl<>(List.of(request));
    }

    private static BloodRequest request(UUID requesterId, UUID donorId, RequestStatus status) {
        BloodRequest request = new BloodRequest();
        request.setId(UUID.randomUUID());
        request.setRequester(user(requesterId, "Requester"));
        request.setStatus(status);
        request.setBloodGroup(BloodGroup.O_POSITIVE);
        request.setUrgency(RequestUrgency.ROUTINE);
        request.setRequestTime(LocalDateTime.now());
        if (donorId != null) {
            request.setAcceptedDonor(user(donorId, "Donor"));
        }
        return request;
    }

    private static User user(UUID id, String firstName) {
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName("User");
        return user;
    }
}
