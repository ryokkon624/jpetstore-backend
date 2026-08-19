package com.example.jpetstore.backend.config

import com.example.jpetstore.backend.infrastructure.security.JwtProperties
import com.example.jpetstore.backend.support.TestJwtSecrets
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.boot.convert.ApplicationConversionService
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.core.convert.ConversionService
import spock.lang.Specification

/**
 * #38 AC-neg1/AC-neg2: JwtProperties 単体（DB非依存）で、denylist/低エントロピーの
 * {@code JWT_SECRET} では {@code ApplicationContext} の起動そのものが失敗することを固定する。
 *
 * <p>Bean生成時例外ではなくコンテキスト起動失敗そのものを assert できる方式として
 * {@link ApplicationContextRunner} を採用した（DB不要で高速。既存の実ブート経路検証
 * {@link ApplicationBootFailFastSpec} へJWTケースを足すとFlyway/DataSourceの初期化順で
 * flakyになるため分離。実ブート経路での確認は DoD の実機確認で別途担保する）。
 * {@code @Value("${jwt.secret}")} のプレースホルダ解決に {@link PropertyPlaceholderAutoConfiguration}
 * が必要（登録しないと {@code ${...}} が未解決のまま文字列として渡ってしまう）。あわせて
 * {@code jwt.access-token-ttl} 等（{@code Duration}型）の文字列→型変換には {@code conversionService}
 * という名前で {@link ApplicationConversionService} を bean 登録する必要がある（プレーンな
 * {@code AnnotationConfigApplicationContext} は Spring Boot 拡張の {@code Duration} コンバータを
 * 持たないため、未登録だと JWT_SECRET の値に関わらずコンテキストが常に起動失敗し、
 * AC-neg1/AC-neg2 が「別の理由での起動失敗」に紛れて偽陽性GREENになってしまう）。
 *
 * <p>出典: L3 N1（{@code reports/after/l3-security-regression-backend.md} §2.1・SEC run
 * {@code security/20260819_01}）。
 */
class JwtSecretContextFailFastSpec extends Specification {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(PropertyPlaceholderAutoConfiguration))
                    .withBean("conversionService", ConversionService, { -> new ApplicationConversionService() })
                    .withUserConfiguration(JwtProperties)
                    .withPropertyValues(
                            "jwt.access-token-ttl=PT15M",
                            "jwt.refresh-token-ttl=P7D")

    def "AC-neg1: .env.exampleのplaceholder値ではコンテキスト起動が失敗する"() {
        expect:
        contextRunner
                .withPropertyValues("jwt.secret=" + TestJwtSecrets.ENV_EXAMPLE_PLACEHOLDER)
                .run { context ->
                    assert context.startupFailure != null
                }
    }

    def "AC-neg2: ユニーク文字数24未満の値ではコンテキスト起動が失敗する"() {
        expect:
        contextRunner
                .withPropertyValues("jwt.secret=" + TestJwtSecrets.LOW_ENTROPY)
                .run { context ->
                    assert context.startupFailure != null
                }
    }

    def "AC-neg2: openssl rand -base64 32相当(32byte以上・ユニーク24以上)は正常起動する(正当な鍵の誤拒否が無いことの確認)"() {
        expect:
        contextRunner
                .withPropertyValues("jwt.secret=" + TestJwtSecrets.STRONG)
                .run { context ->
                    assert context.startupFailure == null
                    assert context.getBean(JwtProperties) != null
                }
    }
}
