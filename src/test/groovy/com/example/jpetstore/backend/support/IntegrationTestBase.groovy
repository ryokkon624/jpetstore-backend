package com.example.jpetstore.backend.support

import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer
import spock.lang.Shared
import spock.lang.Specification

/**
 * Testcontainers 統合テストの共通基盤。
 *
 * <p>Docker 上に MySQL 8.4 コンテナを1つだけ起動し（JVM 内で共有・Singleton container パターン）、
 * Spring Boot の Flyway 自動設定（{@code spring-boot-starter-flyway}）でスキーマを適用したうえで
 * アプリケーションコンテキスト全体を起動する。適用するスキーマは {@code jpetstore-database}
 * からコピーした {@code src/test/resources/flyway/sql}（詳細は同ディレクトリの README 参照）。
 *
 * <p>{@code @Tag("integration")} をサブクラスに付与すること（UT/ITの分離。`backend-conventions`
 * 参照）。DB を必要としない slice テスト（{@code @WebMvcTest} 等）はこの基底を使わない。
 */
@SpringBootTest
abstract class IntegrationTestBase extends Specification {

    static final String DB_NAME = "jpetstore_db"

    @Shared
    static MySQLContainer<?> MYSQL

    static {
        MYSQL = new MySQLContainer<>("mysql:8.4.0")
                .withDatabaseName(DB_NAME)
                .withUsername("jpetstore")
                .withPassword("jpetstore")
        MYSQL.start()
    }

    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl)
        registry.add("spring.datasource.username", MYSQL::getUsername)
        registry.add("spring.datasource.password", MYSQL::getPassword)
        registry.add("spring.flyway.url", MYSQL::getJdbcUrl)
        registry.add("spring.flyway.user", MYSQL::getUsername)
        registry.add("spring.flyway.password", MYSQL::getPassword)
        registry.add("spring.flyway.locations", () -> "classpath:flyway/sql")
        // 統合テスト専用の JWT 鍵（本番秘密ではない・実行時のみメモリ上に存在）。
        registry.add("jwt.secret", () -> "integration-test-only-secret-key-32bytes-min")
    }
}
