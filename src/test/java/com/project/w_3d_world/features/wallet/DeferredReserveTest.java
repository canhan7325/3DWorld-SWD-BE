package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.wallet.entity.Wallet;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeferredReserveTest {
    private final BigDecimal target=new BigDecimal("100000");
    @Test void firstReleasedSaleRetainsReserveWithoutChargingItTwice(){
        var wallet=new Wallet(9);
        assertEquals(0,wallet.reserveBalance(target).signum());
        assertEquals(target,wallet.reserveShortfall(target));
        wallet.addPending(new BigDecimal("190000"));wallet.releasePending(new BigDecimal("190000"));
        assertEquals(target,wallet.reserveBalance(target));assertEquals(new BigDecimal("90000"),wallet.withdrawableBalance(target));
        assertEquals(new BigDecimal("190000"),wallet.getAvailableBalance());
        wallet.hold(new BigDecimal("90000"));wallet.spendHold(new BigDecimal("90000"));
        assertEquals(target,wallet.getAvailableBalance());
        wallet.debitAvailable(new BigDecimal("60000"));assertEquals(new BigDecimal("40000"),wallet.reserveBalance(target));
        wallet.addPending(new BigDecimal("80000"));wallet.releasePending(new BigDecimal("80000"));
        assertEquals(target,wallet.reserveBalance(target));assertEquals(new BigDecimal("20000"),wallet.withdrawableBalance(target));
    }
    @Test void smallFirstSaleBuildsReserveAcrossSubsequentSales(){
        var wallet=new Wallet(9);wallet.addPending(new BigDecimal("30000"));wallet.releasePending(new BigDecimal("30000"));
        assertEquals(new BigDecimal("30000"),wallet.reserveBalance(target));assertEquals(0,wallet.withdrawableBalance(target).signum());
        wallet.addPending(new BigDecimal("80000"));wallet.releasePending(new BigDecimal("80000"));
        assertEquals(target,wallet.reserveBalance(target));assertEquals(new BigDecimal("10000"),wallet.withdrawableBalance(target));
    }
}
