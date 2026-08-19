package com.example.jpetstore.backend.infrastructure.audit

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.example.jpetstore.backend.domain.security.CurrentUserProvider
import com.example.jpetstore.backend.infrastructure.mybatis.custom.mapper.AuditLogCustomMapper
import com.example.jpetstore.backend.infrastructure.security.AuditWriteQuotaService
import org.slf4j.LoggerFactory
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

/**
 * #39 AC2/AC-neg2: 監査INSERT失敗時のbest-effort化。呼び出し元(セキュリティハンドラ等)へ例外を伝播させず、
 * SBD-14の記録宣言に対する後退を最小化するため必ずアプリログへERRORで残すことを実証する（S2）。
 * DB非依存のunit test（{@link AuditLogCustomMapper}をMockし例外を注入）。
 */
class AuditLogRecorderBestEffortSpec extends Specification {

    def mapper = Mock(AuditLogCustomMapper)
    def currentUserProvider = Stub(CurrentUserProvider) {
        currentUser() >> Optional.empty()
    }
    def objectMapper = JsonMapper.builder().build()
    def auditWriteQuotaService = Stub(AuditWriteQuotaService) {
        tryAcquire(_) >> true
    }
    def recorder = new AuditLogRecorder(mapper, currentUserProvider, objectMapper, auditWriteQuotaService)

    ListAppender<ILoggingEvent> appender
    ch.qos.logback.classic.Logger logbackLogger

    def setup() {
        logbackLogger = LoggerFactory.getLogger(AuditLogRecorder) as ch.qos.logback.classic.Logger
        appender = new ListAppender<>()
        appender.start()
        logbackLogger.addAppender(appender)
    }

    def cleanup() {
        logbackLogger.detachAppender(appender)
    }

    def "recordAuthzFailureはmapper.insertが例外を投げても呼び出し元へ伝播させない(AC-neg2)"() {
        given:
        mapper.insert(_) >> { throw new RuntimeException("boom") }

        when:
        recorder.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        noExceptionThrown()
    }

    def "記録失敗はアプリログにERRORとして残る(黙って消さない・S2)"() {
        given:
        mapper.insert(_) >> { throw new RuntimeException("boom") }

        when:
        recorder.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        appender.list.any {
            it.level == Level.ERROR && it.formattedMessage.contains("Failed to record audit log")
        }
    }

    def "recordStateChange(成功監査)もmapper.insertが例外を投げても呼び出し元へ伝播させずERRORログに残す(S2)"() {
        given:
        mapper.insert(_) >> { throw new RuntimeException("boom") }

        when:
        recorder.recordStateChange("ORDER_CREATE", "ORDER", "1", "SUCCESS", null)

        then:
        noExceptionThrown()
        appender.list.any {
            it.level == Level.ERROR && it.formattedMessage.contains("Failed to record audit log")
        }
    }

    def "recordStateChangeIndependentlyもmapper.insertが例外を投げても呼び出し元へ伝播させない"() {
        given:
        mapper.insert(_) >> { throw new RuntimeException("boom") }

        when:
        recorder.recordStateChangeIndependently("ORDER_CREATE", null, null, "FAILURE", null)

        then:
        noExceptionThrown()
    }

    def "mapper.insertが成功すればERRORログは出ない"() {
        given:
        mapper.insert(_) >> 1

        when:
        recorder.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        appender.list.isEmpty()
    }

    def "#39 AC2 fix(SM verification確定所見): auditWriteQuotaService.tryAcquireが例外を投げても呼び出し元へ伝播させない(fail-open)"() {
        given: "未認証(actor==null)のためquotaチェックが呼ばれる状況で、tryAcquire自体が例外を投げる"
        def failingQuotaService = Stub(AuditWriteQuotaService) {
            tryAcquire(_) >> { throw new RuntimeException("connection pool exhausted") }
        }
        def recorderWithFailingQuota = new AuditLogRecorder(mapper, currentUserProvider, objectMapper, failingQuotaService)

        when:
        recorderWithFailingQuota.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        noExceptionThrown()
    }

    def "#39 AC2 fix: quotaチェック失敗時もfail-openで監査INSERTへ進む(監査を握り潰さない)"() {
        given:
        def failingQuotaService = Stub(AuditWriteQuotaService) {
            tryAcquire(_) >> { throw new RuntimeException("connection pool exhausted") }
        }
        def recorderWithFailingQuota = new AuditLogRecorder(mapper, currentUserProvider, objectMapper, failingQuotaService)

        when:
        recorderWithFailingQuota.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        1 * mapper.insert(_) >> 1
    }

    def "#39 AC2 fix: quotaチェック失敗はアプリログにERRORとして残る(黙って消さない)"() {
        given:
        def failingQuotaService = Stub(AuditWriteQuotaService) {
            tryAcquire(_) >> { throw new RuntimeException("connection pool exhausted") }
        }
        def recorderWithFailingQuota = new AuditLogRecorder(mapper, currentUserProvider, objectMapper, failingQuotaService)
        mapper.insert(_) >> 1

        when:
        recorderWithFailingQuota.recordAuthzFailure("/api/orders/1", "denied", new MockHttpServletRequest())

        then:
        appender.list.any {
            it.level == Level.ERROR && it.formattedMessage.contains("Audit write quota check failed")
        }
    }
}
