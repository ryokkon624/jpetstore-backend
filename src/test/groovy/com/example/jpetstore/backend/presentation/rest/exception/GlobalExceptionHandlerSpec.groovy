package com.example.jpetstore.backend.presentation.rest.exception

import com.example.jpetstore.backend.domain.exception.OptimisticLockConflictException
import com.example.jpetstore.backend.domain.exception.ResourceNotFoundException
import com.example.jpetstore.backend.infrastructure.audit.AuditLogRecorder
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import spock.lang.Specification

/**
 * AC4 (SBD-10): 例外を正規化 DTO（{@link ErrorResponse}）にマッピングし、スタックトレース/内部パス/版数を
 * 露出しないことを検証する。
 *
 * <p>AC7: {@code @PreAuthorize} 等メソッドセキュリティ由来の認可失敗は DispatcherServlet 内で
 * {@link GlobalExceptionHandler} が先に捕捉し、Security フィルタチェーン側の
 * {@code AccessDeniedHandler}/{@code AuthenticationEntryPoint} まで届かない
 * （{@code SecurityEndToEndSpec} の実機検証で判明）。そのためここでも記録結線する。
 */
class GlobalExceptionHandlerSpec extends Specification {

    def recorder = Mock(AuditLogRecorder)
    def handler = new GlobalExceptionHandler(recorder)
    def request = new MockHttpServletRequest("GET", "/api/secured/ping")

    def "ResourceNotFoundExceptionは404に正規化される"() {
        when:
        def response = handler.handleNotFound(new ResourceNotFoundException("item not found"), request)

        then:
        response.statusCode == HttpStatus.NOT_FOUND
        response.body.code == "NOT_FOUND"
        response.body.message == "item not found"
        response.body.path == "/api/secured/ping"
        response.body.timestamp != null
    }

    def "OptimisticLockConflictExceptionは409に正規化される"() {
        when:
        def response = handler.handleConflict(new OptimisticLockConflictException(), request)

        then:
        response.statusCode == HttpStatus.CONFLICT
        response.body.code == "CONFLICT"
    }

    def "AccessDeniedExceptionは403に正規化され詳細理由を露出しない"() {
        when:
        def response = handler.handleAccessDenied(
                new AccessDeniedException("internal detail: role=ADMIN required for /internal/secret"), request)

        then:
        response.statusCode == HttpStatus.FORBIDDEN
        response.body.code == "FORBIDDEN"
        !response.body.message.contains("internal/secret")
        1 * recorder.recordAuthzFailure("/api/secured/ping", _ as String, request)
    }

    def "AuthenticationExceptionは401に正規化される"() {
        when:
        def response = handler.handleAuthentication(new BadCredentialsException("bad creds"), request)

        then:
        response.statusCode == HttpStatus.UNAUTHORIZED
        response.body.code == "UNAUTHORIZED"
        1 * recorder.recordAuthzFailure("/api/secured/ping", _ as String, request)
    }

    def "IllegalArgumentExceptionは400に正規化される"() {
        when:
        def response = handler.handleIllegalArgument(new IllegalArgumentException("bad input"), request)

        then:
        response.statusCode == HttpStatus.BAD_REQUEST
        response.body.code == "BAD_REQUEST"
        response.body.message == "bad input"
    }

    def "想定外の例外は500に正規化されスタックトレース情報を含まない"() {
        when:
        def response = handler.handleUnexpected(new RuntimeException("boom, at com.example.Internal.method(Internal.java:42)"), request)

        then:
        response.statusCode == HttpStatus.INTERNAL_SERVER_ERROR
        response.body.code == "INTERNAL_ERROR"
        !response.body.message.contains("Internal.java")
        !response.body.message.contains("boom")
    }
}
