package com.project.w_3d_world.features.user.service;

import com.project.w_3d_world.features.user.service.impl.UserProfileServiceImpl;
import com.project.w_3d_world.features.user.repository.UserRepository;
import com.project.w_3d_world.features.user.repository.UserAddressRepository;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.entity.User;
import com.project.w_3d_world.features.user.entity.UserBankAccount;
import com.project.w_3d_world.features.user.dto.ProfileRequests.BankRequest;
import com.project.w_3d_world.features.user.dto.ProfileRequests.UpdateProfileRequest;
import com.project.w_3d_world.features.auth.service.CapabilityService;
import com.project.w_3d_world.features.auth.service.SellerVerificationService;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProfileAutoVerificationTest {
    @Test void phoneAndBankAreVerifiedButSellerReviewIsStillInvalidatedOnChange(){
        var users=mock(UserRepository.class);var banks=mock(UserBankAccountRepository.class);
        var capabilities=mock(CapabilityService.class);var seller=mock(SellerVerificationService.class);
        var service=new UserProfileServiceImpl(users,mock(UserAddressRepository.class),banks,capabilities,mock(AuditLogRepository.class),seller);
        var user=new User("u@test.com","hash","Buyer",null);ReflectionTestUtils.setField(user,"id",9);
        when(users.lockById(9)).thenReturn(Optional.of(user));when(capabilities.getCapabilities(user)).thenReturn(Set.of());
        service.updateProfile(9,new UpdateProfileRequest("Buyer","0901234567",null));
        assertTrue(user.isPhoneVerified());verify(seller).invalidate(user,"Phone changed");
        var bank=new UserBankAccount(9,"VCB","Bank","Buyer","hash","****6789");ReflectionTestUtils.setField(bank,"id",4);
        when(banks.findById(4)).thenReturn(Optional.of(bank));
        when(banks.findByUserIdAndActiveTrueOrderByIdAsc(9)).thenReturn(List.of(bank,bank));
        var request=new BankRequest("VCB","Bank","Buyer","123456789");
        var created=service.addBank(9,request);assertEquals("VERIFIED",created.verificationStatus());
        var updated=service.updateBank(9,4,request);assertEquals("VERIFIED",updated.verificationStatus());assertNotNull(bank.getVerifiedAt());
        verify(seller).invalidate(user,"Bank account changed");
        service.updateProfile(9,new UpdateProfileRequest("Buyer",null,null));assertFalse(user.isPhoneVerified());
    }
}
