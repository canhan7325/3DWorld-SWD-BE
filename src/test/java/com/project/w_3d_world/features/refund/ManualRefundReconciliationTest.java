package com.project.w_3d_world.features.refund;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.entity.PhysicalOrder;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.refund.dto.request.RefundRequests.Transfer;
import com.project.w_3d_world.features.refund.dto.request.RefundRequests.Review;
import com.project.w_3d_world.features.refund.repository.RefundReceiptRepository;
import com.project.w_3d_world.features.refund.service.RefundBankEncryption;
import com.project.w_3d_world.features.refund.service.impl.ManualRefundServiceImpl;
import com.project.w_3d_world.features.user.entity.UserBankAccount;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.service.AvatarImageValidator;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ManualRefundReconciliationTest {
    private final JdbcTemplate jdbc=mock(JdbcTemplate.class);
    private final PhysicalOrderRepository orders=mock(PhysicalOrderRepository.class);
    private final RefundReceiptRepository receipts=mock(RefundReceiptRepository.class);
    private final UserBankAccountRepository banks=mock(UserBankAccountRepository.class);
    private final AvatarImageValidator images=mock(AvatarImageValidator.class);
    private final CloudinaryStorage storage=mock(CloudinaryStorage.class);
    private final RefundBankEncryption encryption=mock(RefundBankEncryption.class);
    private final CommissionWalletService commission=mock(CommissionWalletService.class);
    private ManualRefundServiceImpl service;
    private PhysicalOrder order;
    private final BigDecimal total=new BigDecimal("125000");

    @BeforeEach void setUp(){
        service=spy(new ManualRefundServiceImpl(receipts,jdbc,orders,mock(AccountAuthorization.class),banks,images,storage,commission,encryption));
        order=new PhysicalOrder(1,2,"{}",UUID.randomUUID(),new BigDecimal("100000"),new BigDecimal("25000"));
        order.selectCod();
        when(orders.lockById(37)).thenReturn(Optional.of(order));
        doReturn(Map.of("id",7)).when(service).detail(anyInt(),eq(7L));
    }
    @Test void buyerCanSubmitCodBeforePlatformSettlementUsingFullOrderTotal() throws Exception {
        String number="123456789";
        String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(number.getBytes(StandardCharsets.UTF_8)));
        var bank=new UserBankAccount(1,"VCB","Vietcombank","Buyer",hash,"*****6789");bank.review(true);
        when(banks.findById(4)).thenReturn(Optional.of(bank));
        when(jdbc.queryForObject(startsWith("SELECT count"),eq(Integer.class),any(Object[].class))).thenReturn(0);
        when(jdbc.queryForObject(startsWith("INSERT INTO transactions"),eq(Integer.class),any(Object[].class))).thenReturn(7);
        when(images.validateAndNormalize(any())).thenReturn(new byte[]{1});
        when(storage.uploadRefund(eq(37),any(),eq("image/png"))).thenReturn(new CloudinaryStorage.UploadedImage("https://res.cloudinary.com/test/Refund/photo.png","Refund/photo"));
        when(encryption.encrypt(anyString(),anyString())).thenReturn("encrypted");
        service.submit(1,37,4,number,"Broken item",List.of(new MockMultipartFile("images",new byte[]{1})));
        verifyNoInteractions(receipts);
        verify(jdbc).queryForObject(startsWith("INSERT INTO transactions"),eq(Integer.class),eq("REFUND:37"),eq(37),eq(1),eq(total),eq("Broken item"));
    }
    @Test void staffCanApproveWithoutCodSettlement(){
        row("PENDING",total);
        service.review(8,7L,new Review(true,"Verified damage","SELLER",new BigDecimal("25000"),new BigDecimal("25000"),"GHTK-37"));
        verifyNoInteractions(receipts);
        verify(commission).assessShipping(eq(37),eq(7L),eq(8),eq("SELLER"),any(),any(),eq("Verified damage"),eq("GHTK-37"));
    }
    @Test void adminCannotConfirmUnsettledOrPartiallySettledCod(){
        row("PROCESSING",total);
        when(receipts.receivedByPlatform(37)).thenReturn(BigDecimal.ZERO,new BigDecimal("100000"));
        for(int i=0;i<2;i++){
            var error=assertThrows(ApiException.class,()->service.confirm(9,7L,new Transfer("BANK-7","Transferred")));
            assertTrue(error.getMessage().contains("reconciled"));
        }
        verify(jdbc,never()).update(startsWith("UPDATE transactions"),any(Object[].class));
        verify(jdbc,never()).update(startsWith("INSERT INTO audit_logs"),any(Object[].class));
    }
    @Test void settledApprovedRefundCanBeConfirmedAndReplayDoesNotPayAgain(){
        row("PROCESSING",total);when(receipts.receivedByPlatform(37)).thenReturn(total);
        service.confirm(9,7L,new Transfer("BANK-7","Transferred"));
        row("SUCCESS",total);
        service.confirm(9,7L,new Transfer("BANK-7","Transferred"));
        verify(receipts,times(1)).receivedByPlatform(37);
        verify(jdbc,times(1)).update(startsWith("UPDATE transactions"),eq("BANK-7"),eq(7L));
    }
    @Test void unapprovedAndMismatchedRefundCannotBePaid(){
        row("PENDING",total);
        assertThrows(ApiException.class,()->service.confirm(9,7L,new Transfer("BANK-7","Transferred")));
        verifyNoInteractions(receipts);
        row("PROCESSING",new BigDecimal("100000"));when(receipts.receivedByPlatform(37)).thenReturn(total);
        assertThrows(ApiException.class,()->service.confirm(9,7L,new Transfer("BANK-7","Transferred")));
        verify(jdbc,never()).update(startsWith("UPDATE transactions"),any(Object[].class));
    }
    private void row(String status,BigDecimal amount){when(jdbc.queryForMap(contains("FOR UPDATE"),eq(7L))).thenReturn(Map.of("physical_order_id",37,"status",status,"amount",amount));}
}
