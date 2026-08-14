package com.example.jpetstore.backend.presentation.rest.security

import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification
import tools.jackson.databind.json.JsonMapper

/**
 * AC3/AC7: 未認証アクセス(401)を正規化JSONで返しつつ AuditLogRecorder に記録結線する。
 */
class AuditingAuthenticationEntryPointSpec extends Specification {

    def recorder = Mock(AuditLogRecorder)
    def objectMapper = JsonMapper.builder().build()
    def entryPoint = new AuditingAuthenticationEntryPoint(recorder, objectMapper)

    def "401 JSONを書き込みAuditLogRecorder.recordAuthzFailureを呼ぶ"() {
        given:
        def request = new MockHttpServletRequest("GET", "/api/secured/ping")
        def response = new MockHttpServletResponse()

        when:
        entryPoint.commence(request, response, new BadCredentialsException("no token"))

        then:
        response.status == HttpStatus.UNAUTHORIZED.value()
        response.contentType.contains("application/json")
        def body = objectMapper.readTree(response.contentAsString)
        body.get("code").asText() == "UNAUTHORIZED"
        1 * recorder.recordAuthzFailure("/api/secured/ping", "no token", request)
    }
}
