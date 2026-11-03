package com.project.w_3d_world.features.wallet.service.impl;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.wallet.dto.response.PayoutHistoryResponse;
import com.project.w_3d_world.features.wallet.dto.response.WalletLedgerResponse;
import com.project.w_3d_world.features.wallet.repository.WalletHistoryRepository;
import com.project.w_3d_world.features.wallet.service.WalletHistoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class WalletHistoryServiceImpl implements WalletHistoryService {
    private final WalletHistoryRepository history;
    private final AccountAuthorization authorization;

    @Override public List<PayoutHistoryResponse> payouts(int seller,int page,int size){
        validate(seller,page,size);return history.payouts(seller,page,size);
    }
    @Override public List<WalletLedgerResponse> ledgers(int seller,int page,int size){
        validate(seller,page,size);return history.ledgers(seller,page,size);
    }
    private void validate(int seller,int page,int size){
        authorization.requireRole(seller,RoleCode.SELLER);
        if(page<0||size<1||size>100)throw ApiException.invalidRequest("page must be >= 0 and size must be 1–100.");
    }
}
