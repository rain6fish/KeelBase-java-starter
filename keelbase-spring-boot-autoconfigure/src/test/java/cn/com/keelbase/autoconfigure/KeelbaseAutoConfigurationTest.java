// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.autoconfigure;

import cn.com.keelbase.client.KeelbaseAuditReporter;
import cn.com.keelbase.client.KeelbaseClient;
import cn.com.keelbase.compensation.CompensationAuditSink;
import cn.com.keelbase.compensation.JdbcRevocationLedgerStore;
import cn.com.keelbase.compensation.RevocationLedger;
import cn.com.keelbase.compensation.RevocationLedgerStore;
import cn.com.keelbase.delegation.DelegationAuthFilter;
import cn.com.keelbase.export.ExportConfigResolver;
import cn.com.keelbase.export.KeelbaseStatusController;
import cn.com.keelbase.export.ProxyToolsExportController;
import cn.com.keelbase.export.ProxyToolsScanner;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import javax.sql.DataSource;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the starter's auto-configuration is complete and conditional: one dependency yields every
 * bean, missing required keys fail startup by name, user beans replace defaults, and switches and
 * the servlet guard remove exactly their own beans.
 *
 * <p>验证 starter 自动配置「完整且条件化」：一个依赖带来全部 bean；缺必填键启动失败并点名；
 * 用户 bean 覆盖默认；开关与 servlet 守卫只移除各自的 bean。
 */
class KeelbaseAutoConfigurationTest {

    private static final String SECRET = "0123456789012345678901234567890123456789012345678901234567890123";

    private WebApplicationContextRunner webRunner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DelegationAutoConfiguration.class,
                        ToolsExportAutoConfiguration.class,
                        CompensationAutoConfiguration.class,
                        KeelbaseClientAutoConfiguration.class))
                .withBean(RequestMappingHandlerMapping.class, KeelbaseAutoConfigurationTest::emptyHandlerMapping)
                .withPropertyValues(
                        "keelbase.delegation.secret=" + SECRET,
                        "keelbase.delegation.audience=legacy-crm",
                        "keelbase.tools.base-url=http://localhost:8081");
    }

    /** The scanner only reads {@code getHandlerMethods()}; mapping discovery is not needed here. */
    private static RequestMappingHandlerMapping emptyHandlerMapping() {
        return new RequestMappingHandlerMapping() {
            @Override
            public void afterPropertiesSet() {
                // no handler discovery needed for auto-configuration assertions
            }
        };
    }

    @Test
    void completeConfigurationStartsAndExposesEveryBean() {
        webRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DelegationAuthFilter.class);
            assertThat(context).hasSingleBean(ProxyToolsScanner.class);
            assertThat(context).hasSingleBean(ExportConfigResolver.class);
            assertThat(context).hasSingleBean(ProxyToolsExportController.class);
            assertThat(context).hasSingleBean(KeelbaseStatusController.class);
            assertThat(context).hasSingleBean(CompensationAuditSink.class);
            assertThat(context).hasSingleBean(KeelbaseClient.class);
            assertThat(context).hasSingleBean(KeelbaseAuditReporter.class);
            assertThat(context.getBean(RevocationLedgerStore.class)).isInstanceOf(RevocationLedger.class);
        });
    }

    @Test
    void missingSecretFailsStartupNamingTheKey() {
        webRunner()
                .withPropertyValues("keelbase.delegation.secret=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("keelbase.delegation.secret");
                });
    }

    @Test
    void missingAudienceFailsStartupNamingTheKey() {
        webRunner()
                .withPropertyValues("keelbase.delegation.audience=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("keelbase.delegation.audience");
                });
    }

    @Test
    void userLedgerReplacesTheDefault() {
        RevocationLedgerStore custom = new RevocationLedger(8);
        webRunner()
                .withBean(RevocationLedgerStore.class, () -> custom)
                .run(context -> {
                    assertThat(context).hasSingleBean(RevocationLedgerStore.class);
                    assertThat(context.getBean(RevocationLedgerStore.class)).isSameAs(custom);
                });
    }

    @Test
    void disablingToolsRemovesOnlyItsBeans() {
        webRunner()
                .withPropertyValues("keelbase.tools.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ProxyToolsScanner.class);
                    assertThat(context).doesNotHaveBean(ProxyToolsExportController.class);
                    assertThat(context).hasSingleBean(DelegationAuthFilter.class);
                });
    }

    @Test
    void disablingDelegationRemovesOnlyItsFilter() {
        webRunner()
                .withPropertyValues("keelbase.delegation.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(DelegationAuthFilter.class);
                    assertThat(context).hasSingleBean(KeelbaseStatusController.class);
                });
    }

    @Test
    void jdbcStoreWithoutDataSourceFailsStartupNamingTheKey() {
        webRunner()
                .withPropertyValues("keelbase.compensation.store=jdbc")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("keelbase.compensation.store=jdbc")
                            .hasStackTraceContaining("DataSource");
                });
    }

    @Test
    void jdbcStoreWithoutTheTableFailsStartupAndNamesTheDdl() {
        webRunner()
                .withPropertyValues("keelbase.compensation.store=jdbc")
                .withBean(DataSource.class, KeelbaseAutoConfigurationTest::h2DataSource)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining(JdbcRevocationLedgerStore.TABLE)
                            .hasStackTraceContaining(JdbcRevocationLedgerStore.CREATE_TABLE_DDL);
                });
    }

    @Test
    void jdbcStoreUsesTheHostDataSource() {
        webRunner()
                .withPropertyValues(
                        "keelbase.compensation.store=jdbc",
                        "keelbase.compensation.jdbc.auto-ddl=true")
                .withBean(DataSource.class, KeelbaseAutoConfigurationTest::h2DataSource)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RevocationLedgerStore.class))
                            .isInstanceOf(JdbcRevocationLedgerStore.class);
                    assertThat(context.getBean(RevocationLedgerStore.class).isRevoked(1L)).isFalse();
                });
    }

    @Test
    void unknownStoreValueFailsStartupNamingTheKey() {
        webRunner()
                .withPropertyValues("keelbase.compensation.store=cassandra")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("keelbase.compensation.store");
                });
    }

    @Test
    void nonServletApplicationSkipsServletOnlyAutoConfiguration() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DelegationAutoConfiguration.class,
                        ToolsExportAutoConfiguration.class,
                        CompensationAutoConfiguration.class,
                        KeelbaseClientAutoConfiguration.class))
                .withBean(RequestMappingHandlerMapping.class, KeelbaseAutoConfigurationTest::emptyHandlerMapping)
                .withPropertyValues(
                        "keelbase.delegation.secret=" + SECRET,
                        "keelbase.delegation.audience=legacy-crm",
                        "keelbase.tools.base-url=http://localhost:8081")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DelegationAuthFilter.class);
                    assertThat(context).doesNotHaveBean(ProxyToolsScanner.class);
                    assertThat(context).hasSingleBean(KeelbaseClient.class);
                    assertThat(context).hasSingleBean(RevocationLedgerStore.class);
                });
    }

    private static DataSource h2DataSource() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:autoconfig_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
