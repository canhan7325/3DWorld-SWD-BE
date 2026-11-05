package com.project.w_3d_world.features.wallet;

import com.project.w_3d_world.features.auth.service.AccountAuthorization;
import com.project.w_3d_world.features.order.repository.PhysicalOrderRepository;
import com.project.w_3d_world.features.storefront.repository.StorefrontRepository;
import com.project.w_3d_world.features.wallet.controller.AdminDashboardController;
import com.project.w_3d_world.features.wallet.controller.EarningReconciliationController;
import com.project.w_3d_world.features.wallet.repository.impl.AdminDashboardRepositoryImpl;
import com.project.w_3d_world.features.wallet.repository.impl.EarningReleaseStatusRepositoryImpl;
import com.project.w_3d_world.features.wallet.repository.WalletRepository;
import com.project.w_3d_world.features.audit.repository.AuditLogRepository;
import com.project.w_3d_world.features.wallet.service.CommissionWalletService;
import com.project.w_3d_world.features.wallet.service.CommissionSnapshotService;
import com.project.w_3d_world.features.wallet.service.impl.AdminDashboardServiceImpl;
import com.project.w_3d_world.features.wallet.service.impl.EarningReconciliationServiceImpl;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Explicit opt-in read-only diagnostic. Does not boot schedulers, Flyway, or any write service. */
@EnabledIfSystemProperty(named="commission.live.readonly",matches="true")
class CommissionApiReadOnlyTest {
    @Test void dashboardAndOrderDiagnosticUseRealStoredCommissionWithoutWriting() throws Exception {
        var env=new HashMap<String,String>();
        for(String line:Files.readAllLines(Path.of(".env"))){
            if(line.isBlank()||line.stripLeading().startsWith("#")||!line.contains("="))continue;
            var pair=line.split("=",2);String value=pair[1].trim();
            if(value.length()>1 && ((value.startsWith("\"")&&value.endsWith("\""))||(value.startsWith("'")&&value.endsWith("'"))))value=value.substring(1,value.length()-1);
            env.put(pair[0].trim(),value);
        }
        var underlying=new DriverManagerDataSource(env.get("DB_URL"),env.get("DB_USERNAME"),env.get("DB_PASSWORD"));
        var readonly=new AbstractDataSource(){
            @Override public Connection getConnection() throws SQLException{Connection c=underlying.getConnection();c.setReadOnly(true);return c;}
            @Override public Connection getConnection(String user,String password) throws SQLException{throw new SQLException("Explicit credentials are disabled.");}
        };
        var jdbc=new JdbcTemplate(readonly);jdbc.setQueryTimeout(15);
        var dashboard=new AdminDashboardServiceImpl(new AdminDashboardRepositoryImpl(new NamedParameterJdbcTemplate(jdbc)),mock(StorefrontRepository.class),mock(WalletRepository.class),mock(AuditLogRepository.class),mock(PhysicalOrderRepository.class));
        var reconciliation=new EarningReconciliationServiceImpl(new EarningReleaseStatusRepositoryImpl(jdbc),mock(CommissionWalletService.class),mock(AccountAuthorization.class),mock(CommissionSnapshotService.class));
        var mvc=MockMvcBuilders.standaloneSetup(new AdminDashboardController(dashboard),new EarningReconciliationController(reconciliation))
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver(){
                    @Override public boolean supportsParameter(org.springframework.core.MethodParameter p){return p.getParameterType()==com.project.w_3d_world.common.config.AuthenticatedUser.class;}
                    @Override public Object resolveArgument(org.springframework.core.MethodParameter p,org.springframework.web.method.support.ModelAndViewContainer m,org.springframework.web.context.request.NativeWebRequest w,org.springframework.web.bind.support.WebDataBinderFactory b){return new com.project.w_3d_world.common.config.AuthenticatedUser(0,java.util.Set.of("ADMIN"),null);}
                }).build();
        String from="2026-10-01",to=LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).plusDays(1).toString();
        var mapper=new JsonMapper();
        var metrics=mapper.readTree(mvc.perform(get("/api/admin/dashboard/metrics").param("from",from).param("to",to))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        System.out.println("READ_ONLY_API_METRICS "+metrics.path("data"));
        assertTrue(metrics.path("data").path("realized_commission").isNumber());
        for(int id:new int[]{31,38}){
            var json=mapper.readTree(mvc.perform(get("/api/admin/finance/orders/"+id+"/earning"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
            assertTrue(json.path("expected_commission").isNumber());
            System.out.println("READ_ONLY_ORDER "+id+" stored="+json.path("commission_amount")+" expected="+json.path("expected_commission")+" mismatch="+json.path("commissionMismatch"));
        }
    }
}
