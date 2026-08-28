package cn.com.keelbase.compensation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 默认审计实现：SLF4J 结构化日志。 */
public class Slf4jCompensationAuditSink implements CompensationAuditSink {

    private static final Logger log = LoggerFactory.getLogger(Slf4jCompensationAuditSink.class);

    @Override
    public void audit(String action, Long resultId, String subject) {
        log.info("compensation action={} resultId={} subject={}", action, resultId, subject);
    }
}
