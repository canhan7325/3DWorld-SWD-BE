package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.wallet.entity.Wallet;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class SellerShippingDebtTest {
    @Test void multipleReturnsConsumeReserveThenFutureEarningsPayAccumulatedDebt() {
        Wallet wallet=new Wallet(9);
        wallet.creditAvailable(new BigDecimal("100000"));
        charge(wallet,60000);
        charge(wallet,70000);
        charge(wallet,50000);
        assertEquals(0,wallet.getAvailableBalance().signum());
        assertEquals(new BigDecimal("80000"),wallet.getReturnShippingDebt());
        var since=wallet.getShippingDebtSince();assertNotNull(since);
        wallet.addPending(new BigDecimal("50000"));wallet.releasePending(new BigDecimal("50000"));
        settle(wallet);
        assertEquals(new BigDecimal("30000"),wallet.getReturnShippingDebt());
        assertEquals(since,wallet.getShippingDebtSince());
        wallet.creditAvailable(new BigDecimal("50000"));settle(wallet);
        assertEquals(0,wallet.getReturnShippingDebt().signum());assertNull(wallet.getShippingDebtSince());
        assertEquals(new BigDecimal("20000"),wallet.getAvailableBalance());
    }
    private void charge(Wallet wallet,int amount){var fee=BigDecimal.valueOf(amount);var applied=wallet.getAvailableBalance().min(fee);wallet.debitAvailable(applied);wallet.addReturnShippingDebt(fee.subtract(applied));}
    private void settle(Wallet wallet){var paid=wallet.settleReturnShippingDebt(wallet.getAvailableBalance());wallet.debitAvailable(paid);}
}
