package com.project.w_3d_world.features.refund.service.impl;

import com.project.w_3d_world.features.refund.service.ManualRefundService;
import com.project.w_3d_world.features.refund.service.RefundBankEncryption;

import com.project.w_3d_world.common.exception.ApiException;
import com.project.w_3d_world.common.storage.CloudinaryStorage;
import com.project.w_3d_world.features.auth.enums.RoleCode;
import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.refund.dto.request.RefundRequests.*;
import com.project.w_3d_world.features.user.repository.UserBankAccountRepository;
import com.project.w_3d_world.features.user.service.AvatarImageValidator;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor @Slf4j @Transactional(readOnly=true)
public class ManualRefundServiceImpl implements ManualRefundService {
 private final JdbcTemplate jdbc; private final PhysicalOrderRepository orders; private final AccountAuthorization authorization;
 private final UserBankAccountRepository banks; private final AvatarImageValidator images; private final CloudinaryStorage storage;
 private final CommissionWalletService commission; private final RefundBankEncryption bankEncryption;
 @Transactional public Map<String,Object> submit(int buyer,int orderId,int bankId,String accountNumber,String reason,List<MultipartFile> files){
  var order=orders.lockById(orderId).filter(o->o.getBuyerUserId().equals(buyer)).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"Order does not exist."));
  if(order.getReceivedAt()!=null&&(order.getRefundDeadlineAt()==null||!Instant.now().isBefore(order.getRefundDeadlineAt()))) throw new ApiException(HttpStatus.CONFLICT,"The refund window has expired.");
  if(reason==null||reason.isBlank()||reason.length()>2000||files==null||files.isEmpty()||files.size()>5) throw ApiException.invalidRequest("A reason and 1 to 5 evidence images are required.");
  if(jdbc.queryForObject("SELECT count(*) FROM transactions WHERE physical_order_id=? AND transaction_type='REFUND' AND status IN ('PENDING','PROCESSING','SUCCESS')",Integer.class,orderId)>0) throw new ApiException(HttpStatus.CONFLICT,"An active or completed refund already exists.");
  var bank=banks.findById(bankId).filter(b->b.getUserId().equals(buyer)&&b.isActive()&&"VERIFIED".equals(b.getVerificationStatus())).orElseThrow(()->ApiException.invalidRequest("An owned active verified bank account is required."));
  if(accountNumber==null||!accountNumber.matches("[0-9]{6,34}")) throw ApiException.invalidRequest("Bank account number is required.");
  try { var hash=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(accountNumber.getBytes(java.nio.charset.StandardCharsets.UTF_8))); if(!hash.equals(bank.getAccountNumberToken())) throw ApiException.invalidRequest("Bank number does not match the verified account."); } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
  BigDecimal amount=jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM transactions WHERE physical_order_id=? AND transaction_type='PAYMENT' AND status='SUCCESS' AND settlement_mode IN ('PLATFORM','COD')",BigDecimal.class,orderId);
  if(amount.signum()<=0||amount.compareTo(order.getTotalAmount())!=0) throw new ApiException(HttpStatus.CONFLICT,"Full platform receipt must be reconciled before refund.");
  List<String> urls=new ArrayList<>(); for(var file:files){var asset=storage.uploadRefund(orderId,images.validateAndNormalize(file),"image/png");urls.add(asset.url());}
  String meta=toJson(Map.of("bankAccountId",bankId,"bankCode",bank.getBankCode(),"bankName",bank.getBankName(),"accountHolder",bank.getAccountHolder(),"maskedAccount",bank.getAccountNumberMasked(),"accountNumberEncrypted",bankEncryption.encrypt(accountNumber,"refund-bank:"+orderId+":"+buyer+":"+bankId),"images",urls));
  int tx=jdbc.queryForObject("INSERT INTO transactions(transaction_type,purpose,operation_key,physical_order_id,payer_user_id,payment_stage,settlement_mode,amount,status,fulfillment_status,refund_reason) VALUES ('REFUND','PHYSICAL_ORDER',?,?,?,'REFUND','PLATFORM',?,'PENDING','PENDING',?) RETURNING transaction_id",Integer.class,"REFUND:"+orderId,orderId,buyer,amount,reason.trim());
  audit(buyer,"REFUND_REQUESTED",orderId,reason,meta); return detail(buyer,tx);
 }
 public List<Map<String,Object>> queue(int actor,String status,int page,int size){authorization.requireRole(actor,RoleCode.PRINT_STAFF,RoleCode.ADMIN);if(page<0||size<1||size>100|| (status!=null&&!Set.of("PENDING","APPROVED","REJECTED","PAID").contains(status)))throw ApiException.invalidRequest("Invalid pagination or status.");String filter=status==null?"":"AND fulfillment_status=? ";String normalized=status==null?null:(status.equals("APPROVED")?"APPROVED":status.equals("REJECTED")?"REJECTED":status.equals("PAID")?"COMPLETED":"PENDING");return jdbc.queryForList("SELECT transaction_id id,physical_order_id,amount,CASE WHEN fulfillment_status='APPROVED' THEN 'APPROVED' WHEN fulfillment_status='REJECTED' THEN 'REJECTED' WHEN fulfillment_status='COMPLETED' THEN 'PAID' ELSE status END status,refund_reason reason,created_at FROM transactions WHERE transaction_type='REFUND' "+filter+"ORDER BY transaction_id DESC LIMIT ? OFFSET ?",status==null?new Object[]{size,(long)page*size}:new Object[]{normalized,size,(long)page*size});}
 public Map<String,Object> detail(int actor,long id){var row=jdbc.queryForMap("SELECT transaction_id id,physical_order_id,payer_user_id,amount,status,refund_reason reason,active_key transfer_reference,created_at FROM transactions WHERE transaction_id=? AND transaction_type='REFUND'",id);if(((Number)row.get("payer_user_id")).intValue()!=actor)authorization.requireRole(actor,RoleCode.PRINT_STAFF,RoleCode.ADMIN);var out=new LinkedHashMap<>(row);out.put("history",jdbc.queryForList("SELECT actor_user_id actor_id,action_type status,reason,created_at,metadata FROM audit_logs WHERE entity_type='PHYSICAL_ORDER' AND entity_id=? AND action_type LIKE 'REFUND_%' ORDER BY audit_log_id",row.get("physical_order_id")));return out;}
 @Transactional public Map<String,Object> review(int actor,long id,Review req){authorization.requireRole(actor,RoleCode.PRINT_STAFF);var row=locked(id);if(!"PENDING".equals(row.get("status")))throw new ApiException(HttpStatus.CONFLICT,"Refund is already decided.");int order=((Number)row.get("physical_order_id")).intValue();if(req.approved()){if(req.shippingPayer()==null||req.outboundFee()==null||req.returnFee()==null||req.carrierReference()==null||req.carrierReference().isBlank())throw ApiException.invalidRequest("Approval requires actual two-way shipping fees and carrier reference.");commission.assessShipping(order,id,actor,req.shippingPayer(),req.outboundFee(),req.returnFee(),req.reason(),req.carrierReference());jdbc.update("UPDATE transactions SET status='PROCESSING',fulfillment_status='APPROVED',refund_reason=?,updated_at=now() WHERE transaction_id=?",req.reason(),id);audit(actor,"REFUND_APPROVED",order,req.reason(),null);}else{jdbc.update("UPDATE transactions SET status='FAILED',fulfillment_status='REJECTED',refund_reason=?,updated_at=now() WHERE transaction_id=?",req.reason(),id);audit(actor,"REFUND_REJECTED",order,req.reason(),null);}return detail(actor,id);}
 @Transactional public Map<String,Object> confirm(int admin,long id,Transfer req){authorization.requireRole(admin,RoleCode.ADMIN);var row=locked(id);if("SUCCESS".equals(row.get("status")))return detail(admin,id);if(!"PROCESSING".equals(row.get("status")))throw new ApiException(HttpStatus.CONFLICT,"Only an approved refund can be paid.");int order=((Number)row.get("physical_order_id")).intValue();jdbc.update("UPDATE transactions SET status='SUCCESS',fulfillment_status='COMPLETED',active_key=?,completed_at=now(),updated_at=now() WHERE transaction_id=?",req.transferReference(),id);audit(admin,"REFUND_PAID",order,req.reason(),toJson(Map.of("transferReference",req.transferReference(),"amount",row.get("amount"))));return detail(admin,id);}
 @Transactional public void assessCancelled(int actor,int orderId,ShippingAssessment req){authorization.requireRole(actor,RoleCode.PRINT_STAFF,RoleCode.ADMIN);var o=orders.lockById(orderId).orElseThrow();if("CANCELLED".equals(o.getStatus())&&!"SELLER".equals(req.payer()))throw ApiException.invalidRequest("Seller must bear cancellation shipping fees.");commission.assessShipping(orderId,null,actor,req.payer(),req.outboundFee(),req.returnFee(),req.reason(),req.carrierReference());}
 @Transactional public Map<String,String> transferDetails(int admin,long id,String reason){
  authorization.requireRole(admin,RoleCode.ADMIN); var row=jdbc.queryForMap("SELECT payer_user_id,physical_order_id FROM transactions WHERE transaction_id=? AND transaction_type='REFUND'",id);
  int order=((Number)row.get("physical_order_id")).intValue(); int buyer=((Number)row.get("payer_user_id")).intValue();
  String metadata=jdbc.queryForObject("SELECT metadata::text FROM audit_logs WHERE entity_type='PHYSICAL_ORDER' AND entity_id=? AND action_type='REFUND_REQUESTED' ORDER BY audit_log_id DESC LIMIT 1",String.class,order);
  try { var tree=new tools.jackson.databind.json.JsonMapper().readTree(metadata); String encrypted=tree.path("accountNumberEncrypted").asText();
    String account=bankEncryption.decrypt(encrypted,"refund-bank:"+order+":"+buyer+":"+tree.path("bankAccountId").asInt());
    audit(admin,"REFUND_BANK_DETAILS_ACCESSED",order,reason,null); return Map.of("bankCode",tree.path("bankCode").asText(),"bankName",tree.path("bankName").asText(),"accountHolder",tree.path("accountHolder").asText(),"accountNumber",account);
  } catch(Exception e){throw new ApiException(HttpStatus.CONFLICT,"Refund bank details are unavailable; ask buyer to resubmit.");}
}
 private Map<String,Object> locked(long id){return jdbc.queryForMap("SELECT transaction_id,physical_order_id,payer_user_id,amount,status FROM transactions WHERE transaction_id=? AND transaction_type='REFUND' FOR UPDATE",id);}
 private void audit(int actor,String action,int order,String reason,String meta){jdbc.update("INSERT INTO audit_logs(actor_user_id,action_type,entity_type,entity_id,reason,metadata) VALUES (?,?,?,?,?,cast(? as jsonb))",actor,action,"PHYSICAL_ORDER",order,reason,meta);}
 private String toJson(Object o){try{return new tools.jackson.databind.json.JsonMapper().writeValueAsString(o);}catch(Exception e){return "{}";}}
}
