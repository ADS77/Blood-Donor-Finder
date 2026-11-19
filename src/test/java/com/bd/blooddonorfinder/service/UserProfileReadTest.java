package com.bd.blooddonorfinder.service;

import com.bd.blooddonorfinder.exception.UserNotFoundException;
import com.bd.blooddonorfinder.kafka.producer.GenericKafkaEventProducer;
import com.bd.blooddonorfinder.model.PiiToken;
import com.bd.blooddonorfinder.model.common.GeoLocation;
import com.bd.blooddonorfinder.model.common.Role;
import com.bd.blooddonorfinder.model.common.User;
import com.bd.blooddonorfinder.model.enums.PiiType;
import com.bd.blooddonorfinder.payload.response.DonorDetailDto;
import com.bd.blooddonorfinder.payload.response.UserProfileDto;
import com.bd.blooddonorfinder.repository.UserRepository;
import com.bd.blooddonorfinder.repository.auth.RoleRepository;
import com.bd.blooddonorfinder.security.AppPermissions;
import com.bd.blooddonorfinder.service.audit.AuditService;
import com.bd.blooddonorfinder.service.otp.OtpIssuanceService;
import com.bd.blooddonorfinder.utils.PiiTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The profile and donor-detail reads have different audiences: one returns the
 * caller's own PII, the other must never expose it.
 */
@ExtendWith(MockitoExtension.class)
class UserProfileReadTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private GenericKafkaEventProducer eventProducer;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private GeoLocationService geoLocationService;
    @Mock
    private PiiTokenizer piiTokenizer;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private OtpIssuanceService otpIssuanceService;
    @Mock
    private AuditService auditService;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, eventProducer, passwordEncoder,
                geoLocationService, piiTokenizer, roleRepository, otpIssuanceService, auditService);
    }

    @Test
    void getProfile_returnsCallerOwnContactDetailsAndSafeFields() {
        User user = activeDonor();
        UUID emailTokenId = UUID.randomUUID();
        UUID phoneTokenId = UUID.randomUUID();
        user.setEmailToken(token(PiiType.EMAIL, emailTokenId));
        user.setPhoneToken(token(PiiType.PHONE, phoneTokenId));
        when(userRepository.findUserWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));
        when(piiTokenizer.decrypt(emailTokenId)).thenReturn(Optional.of("donor@example.com"));
        when(piiTokenizer.decrypt(phoneTokenId)).thenReturn(Optional.of("+8801000000000"));

        UserProfileDto profile = service.getProfile(user.getId());

        assertEquals(user.getId(), profile.id());
        assertEquals("donor@example.com", profile.email());
        assertEquals("+8801000000000", profile.phone());
        assertEquals("O_POSITIVE", profile.bloodGroup());
        assertEquals(List.of(AppPermissions.ROLE_DONOR), profile.roles());
        assertEquals("Dhaka", profile.location().city());
        assertTrue(profile.isVerified());
    }

    @Test
    void getProfile_toleratesUndecryptablePii() {
        User user = activeDonor();
        UUID emailTokenId = UUID.randomUUID();
        user.setEmailToken(token(PiiType.EMAIL, emailTokenId));
        when(userRepository.findUserWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));
        when(piiTokenizer.decrypt(emailTokenId)).thenThrow(new IllegalStateException("key rotated"));

        UserProfileDto profile = service.getProfile(user.getId());

        assertNull(profile.email());
        assertNull(profile.phone());
        assertEquals(user.getId(), profile.id());
    }

    @Test
    void getDonorDetail_returnsPublicViewWithoutContactDetails() {
        User donor = activeDonor();
        donor.setEmailToken(token(PiiType.EMAIL, UUID.randomUUID()));
        donor.setPhoneToken(token(PiiType.PHONE, UUID.randomUUID()));
        when(userRepository.findUserWithRolesAndPermissionsById(donor.getId())).thenReturn(Optional.of(donor));

        DonorDetailDto detail = service.getDonorDetail(donor.getId());

        assertEquals(donor.getId(), detail.id());
        assertEquals("Donor User", detail.name());
        assertEquals("O_POSITIVE", detail.bloodGroup());
        assertEquals("Dhaka", detail.city());
        assertEquals("Dhaka", detail.district());
        assertTrue(detail.isVerified());
        assertEquals(4.5, detail.rating());
        assertEquals(9L, detail.totalDonations());
        // The DTO has no email/phone fields at all — decryption is never even
        // attempted on this path.
        verifyNoInteractions(piiTokenizer);
    }

    @Test
    void getDonorDetail_unknownUserReadsAsNotFound() {
        UUID id = UUID.randomUUID();
        when(userRepository.findUserWithRolesAndPermissionsById(id)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.getDonorDetail(id));
    }

    @Test
    void getDonorDetail_inactiveUserReadsAsNotFound() {
        User user = activeDonor();
        user.setActive(false);
        when(userRepository.findUserWithRolesAndPermissionsById(user.getId())).thenReturn(Optional.of(user));

        assertThrows(UserNotFoundException.class, () -> service.getDonorDetail(user.getId()));
    }

    @Test
    void getDonorDetail_nonDonorAccountReadsAsNotFound() {
        User admin = activeDonor();
        Set<Role> roles = new HashSet<>();
        roles.add(new Role(2L, AppPermissions.ROLE_SYSTEM_ADMIN, "admin"));
        admin.setRoles(roles);
        when(userRepository.findUserWithRolesAndPermissionsById(admin.getId())).thenReturn(Optional.of(admin));

        assertThrows(UserNotFoundException.class, () -> service.getDonorDetail(admin.getId()));
    }

    private static User activeDonor() {
        GeoLocation geo = new GeoLocation();
        geo.setCity("Dhaka");
        geo.setDistrict("Dhaka");
        geo.setCountry("Bangladesh");

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Donor");
        user.setLastName("User");
        user.setBloodGroup(com.bd.blooddonorfinder.model.enums.BloodGroup.O_POSITIVE);
        user.setIsVerified(true);
        user.setIsAvailable(true);
        user.setActive(true);
        user.setRating(4.5);
        user.setTotalDonations(9L);
        user.setRatingCount(2L);
        user.setGeoLocation(geo);
        Set<Role> roles = new HashSet<>();
        roles.add(new Role(1L, AppPermissions.ROLE_DONOR, "donor"));
        user.setRoles(roles);
        return user;
    }

    private static PiiToken token(PiiType type, UUID id) {
        PiiToken token = new PiiToken(type, "hash-" + id, new byte[]{1});
        ReflectionTestUtils.setField(token, "id", id);
        return token;
    }
}
