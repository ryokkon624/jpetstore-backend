package com.example.jpetstore.backend.presentation.rest.security

import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.access.AccessDeniedException
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

/**
 * AC3/AC7: 認可失敗(403)を正規化JSONで返しつつ AuditLogRecorder に記録結線する。
 */
class AuditingAccessDeniedHandlerSpec extends Specification {

    def recorder = Mock(AuditLogRecorder)
    def objectMapper = JsonMapper.builder().build()
    def handler = new AuditingAccessDeniedHandler(recorder, objectMapper)

    def "403 JSONを書き込みAuditLogRecorder.recordAuthzFailureを呼ぶ"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/secured/ping")
        def response = new MockHttpServletResponse()

        when:
        handler.handle(request, response, new AccessDeniedException("insufficient role"))

        then:
        response.status == HttpStatus.FORBIDDEN.value()
        response.contentType.contains("application/json")
        def body = objectMapper.readTree(response.contentAsString)
        body.get("code").asText() == "FORBIDDEN"
        !response.contentAsString.contains("insufficient role") // 詳細理由を外部に出さない
        1 * recorder.recordAuthzFailure("/api/secured/ping", "insufficient role", request)
    }
}
