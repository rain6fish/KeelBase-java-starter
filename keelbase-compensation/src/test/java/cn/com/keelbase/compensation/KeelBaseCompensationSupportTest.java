// SPDX-License-Identifier: Apache-2.0

package cn.com.keelbase.compensation;

import cn.com.keelbase.delegation.DelegationAuthFilter;
import cn.com.keelbase.delegation.DelegationPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3 验收：补偿基类 handleRevoke 流程（验身份 / 幂等 / 不存在即幂等）。 */
class KeelBaseCompensationSupportTest {

    static class TestSupport extends KeelBaseCompensationSupport<Map<String, Object>> {
        TestSupport(RevocationLedgerStore ledger, CompensationAuditSink sink) {
            super(ledger, sink);
        }

        ResponseEntity<?> revoke(HttpServletRequest req, Long id,
                                 Map<Long, Map<String, Object>> store) {
            return handleRevoke(req, id, store::get,
                    (item, s) -> {
                        item.put("cancelled", true);
                        item.put("cancelledBy", s);
                    }, "test.revoke");
        }
    }

    private TestSupport support() {
        return new TestSupport(new RevocationLedger(16), (a, id, s) -> {
        });
    }

    private MockHttpServletRequest reqWithPrincipal() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setAttribute(DelegationAuthFilter.PRINCIPAL_ATTR,
                new DelegationPrincipal("local:42", "oidc-1", "legacy-crm", "keelbase", Map.of()));
        return req;
    }

    @Test
    void noPrincipal_returns401() {
        TestSupport s = support();
        ResponseEntity<?> r = s.revoke(new MockHttpServletRequest(), 42L, new ConcurrentHashMap<>());
        assertEquals(401, r.getStatusCodeValue());
    }

    @Test
    void firstRevoke_revoked_thenIdempotent() {
        Map<Long, Map<String, Object>> store = new ConcurrentHashMap<>();
        store.put(42L, new HashMap<>(Map.of("id", 42L)));
        TestSupport s = support();

        ResponseEntity<?> r1 = s.revoke(reqWithPrincipal(), 42L, store);
        assertEquals(200, r1.getStatusCodeValue());
        Map<?, ?> body1 = (Map<?, ?>) r1.getBody();
        assertEquals(Boolean.FALSE, body1.get("idempotent"));
        assertEquals("revoked", body1.get("status"));
        assertEquals("oidc-1", store.get(42L).get("cancelledBy"), "cancelOp 应拿到委托身份");

        ResponseEntity<?> r2 = s.revoke(reqWithPrincipal(), 42L, store);
        assertEquals(200, r2.getStatusCodeValue());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) r2.getBody()).get("idempotent"));
    }

    @Test
    void notFound_treatedAsIdempotent() {
        TestSupport s = support();
        ResponseEntity<?> r = s.revoke(reqWithPrincipal(), 99L, new ConcurrentHashMap<>());
        assertEquals(200, r.getStatusCodeValue());
        assertEquals(Boolean.TRUE, ((Map<?, ?>) r.getBody()).get("idempotent"));
    }
}
