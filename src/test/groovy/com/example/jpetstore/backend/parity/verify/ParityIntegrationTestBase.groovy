package com.example.jpetstore.backend.parity.verify

import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import spock.lang.Tag

/**
 * L2パリティ検証（#48 AC9・D1）専用の統合テスト基盤。
 *
 * <p>{@link IntegrationTestBase}（{@code @SpringBootTest} webEnvironment未指定=MOCK）をサブクラス側で
 * {@code webEnvironment = RANDOM_PORT} 再宣言し、実HTTP（Servletコンテナ実起動）で新側を駆動する。
 * MySQL Testcontainers + Flyway の共有基盤（{@code IntegrationTestBase} 静的初期化）はそのまま継承・再利用し、
 * 既存 {@code IntegrationTestBase} 自体は無変更（D1）。
 *
 * <p>再宣言が実際に効くことは {@link ParityIntegrationTestBaseSmokeSpec} で実HTTPリクエストにより検証済み
 * （Spring TestContextFrameworkは {@code @SpringBootTest} をクラス階層をたどってマージし、サブクラスの
 * 属性がスーパークラスを上書きする。{@code @Inherited} 指定は無いが、Springの
 * {@code TestContextAnnotationUtils} 独自のクラス階層探索により機能する）。
 */
@Tag("integration")
@Tag("parity")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ParityIntegrationTestBase extends IntegrationTestBase {

    @LocalServerPort
    int localServerPort

    String baseUrl() {
        return "http://localhost:${localServerPort}"
    }
}
