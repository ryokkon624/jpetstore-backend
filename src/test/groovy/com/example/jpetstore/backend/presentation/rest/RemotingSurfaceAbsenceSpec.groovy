package com.example.jpetstore.backend.presentation.rest

import spock.lang.Specification

/**
 * #11 AC1/AC-neg1(SBD-7): 無認証 remoting/WS 面（Hessian/Burlap/HttpInvoker/RMI/Axis/JAX-WS 等）が
 * classpath に一切存在しないことを固定する回帰テスト（plain Spock・Spring context 不要）。
 *
 * <p>Spring 6+ で {@code org.springframework.remoting.*} のエクスポータ階層自体が撤去済みのため、
 * 「Context にエクスポータ系 Bean が登録されていないこと」を assert する方式は型参照ができず不可能。
 * 本 spec は SBD-7（remoting/WS 面の構造的不在）を、該当クラスが {@link Class#forName(String)} で
 * {@link ClassNotFoundException} になることで直接証明する（Sprint4 SBD-9 型の「sink 不在を回帰テストで固定」と同型）。
 *
 * <p>将来これらのクラスが依存追加等で classpath に混入した場合、本 spec が赤化し退行を検知する。
 */
class RemotingSurfaceAbsenceSpec extends Specification {

    def "remoting/WSエクスポータ/エンドポイントクラス(#className)はclasspathに存在しない(SBD-7)"() {
        when:
        Class.forName(className)

        then:
        thrown(ClassNotFoundException)

        where:
        className << [
                'org.springframework.remoting.caucho.HessianServiceExporter',
                'org.springframework.remoting.caucho.BurlapServiceExporter',
                'org.springframework.remoting.httpinvoker.HttpInvokerServiceExporter',
                'org.springframework.remoting.rmi.RmiServiceExporter',
                'org.springframework.remoting.support.RemoteExporter',
                'org.apache.axis.transport.http.AxisServlet',
                'jakarta.jws.WebService',
        ]
    }
}
