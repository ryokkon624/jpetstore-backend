package com.example.jpetstore.backend.presentation.rest

import com.example.jpetstore.backend.infrastructure.security.LoginAttemptProperties
import com.example.jpetstore.backend.infrastructure.security.RegisterAttemptProperties
import com.example.jpetstore.backend.support.IntegrationTestBase
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.RequestPostProcessor
import spock.lang.Tag

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

/**
 * #41 N4／L3 §3 残件1: ログイン/登録レート制限の check-then-act（TOCTOU）を、SEC が稼働機への並列送信を
 * 自粛したライブ・バーストPoC（{@code reports/after/l3-security-regression-backend.md} §3 残件1）の
 * 代替実証として Testcontainers 実DB + 並列HTTPリクエストで固定する回帰テスト。
 *
 * <p>技法は Sprint11 {@code OrderConcurrencyIntegrationSpec} を踏襲（{@code Executors.newFixedThreadPool}
 * ＋ {@code CountDownLatch} 2本で同時解放）。修正前（check-then-act）はカウンタが並列数まで伸びるが、
 * 修正後（照合前スロット確保の単一UPDATE）は InnoDB の行ロックにより同一キーへの UPDATE が直列化されるため、
 * {@code authenticate()}/登録処理への到達回数が閾値（{@code maxAttempts}）ちょうどで頭打ちになる。
 */
@Tag("integration")
@AutoConfigureMockMvc
class RateLimitBurstConcurrencySpec extends IntegrationTestBase {

    private static final int PARALLEL_COUNT = 20
    private static final String LOGIN_USERNAME = "burst_login_test_user"
    private static final String LOGIN_RAW_PASSWORD = "Correct#Passw0rd!"
    private static final String REGISTER_CLIENT_IP = "203.0.113.201"
    private static final String SUCCESS_LOGIN_USERNAME = "burst_success_login_test_user"

    @Autowired
    MockMvc mockMvc

    @Autowired
    JdbcTemplate jdbcTemplate

    @Autowired
    PasswordEncoder passwordEncoder

    @Autowired
    LoginAttemptProperties loginAttemptProperties

    @Autowired
    RegisterAttemptProperties registerAttemptProperties

    /**
     * 登録側の {@code RegisterAttemptService#acquireAttemptSlotOrThrow} は {@code REQUIRES_NEW}
     * （F6）のため、呼び出し元（{@code register()} の主 {@code @Transactional}）が保持する接続とは別に
     * もう1本 DB 接続を必要とする。20並列だと最大40本同時に必要となり既定の HikariCP pool（10）を
     * 使い切ってしまう（DBロジック自体の不具合ではなく、本テスト特有の高並列に対する接続池サイズ不足）ため、
     * 本spec専用に pool 上限を引き上げる（本番の pool サイジング自体は本Sprintのスコープ外）。
     */
    @DynamicPropertySource
    static void registerLargerConnectionPool(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "50")
    }

    void setup() {
        cleanupFixtures()
        insertLoginTestUser(LOGIN_USERNAME)
        insertLoginTestUser(SUCCESS_LOGIN_USERNAME)
    }

    void cleanup() {
        cleanupFixtures()
    }

    private void cleanupFixtures() {
        jdbcTemplate.update("DELETE FROM t_login_attempt")
        jdbcTemplate.update("DELETE FROM t_register_attempt WHERE client_ip = ?", REGISTER_CLIENT_IP)
        jdbcTemplate.update("DELETE FROM t_audit_log")
        jdbcTemplate.update("DELETE FROM t_audit_write_quota")
        jdbcTemplate.update(
                "DELETE FROM m_signon WHERE user_id IN (SELECT user_id FROM m_account WHERE username IN (?, ?))",
                LOGIN_USERNAME, SUCCESS_LOGIN_USERNAME)
        jdbcTemplate.update("DELETE FROM m_account WHERE username IN (?, ?)", LOGIN_USERNAME, SUCCESS_LOGIN_USERNAME)
    }

    private void insertLoginTestUser(String username) {
        jdbcTemplate.update(
                """
                INSERT INTO m_account
                    (username, email, first_name, last_name, status, address1, city, state, postal_code, country, phone,
                     create_program, update_program)
                VALUES (?, ?, 'Burst', 'TestUser', 'OK', '1 Test St', 'Testville', 'CA', '90000', 'USA', '555-0100',
                     'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                username, "${username}@example.com")
        Long userId = jdbcTemplate.queryForObject("SELECT user_id FROM m_account WHERE username = ?", Long, username)
        jdbcTemplate.update(
                """
                INSERT INTO m_signon (user_id, password_hash, create_program, update_program)
                VALUES (?, ?, 'TEST_FIXTURE', 'TEST_FIXTURE')
                """,
                userId, passwordEncoder.encode(LOGIN_RAW_PASSWORD))
    }

    private static String loginBody(String username, String password) {
        """{"username":"${username}","password":"${password}"}"""
    }

    private static String registerBody(String username) {
        // #17 の @StrongPassword を通過させたうえでpassword!=repeatedPasswordのService層400を狙う
        // (単純な短いパスワードだとBean Validationで先に400化し、acquireAttemptSlotOrThrow自体に
        // 到達しないため枠確保の並列制御を検証できない)。
        """
        {
          "username": "${username}",
          "password": "Correct#Passw0rd!",
          "repeatedPassword": "Correct#Passw0rd!X",
          "email": "${username}@example.com",
          "firstName": "Taro",
          "lastName": "Yamada",
          "address1": "1 Test St",
          "address2": "Suite 2",
          "city": "Testville",
          "state": "CA",
          "postalCode": "90000",
          "country": "USA",
          "phone": "555-0100"
        }
        """
    }

    private static RequestPostProcessor remoteAddr(String clientIp) {
        { MockHttpServletRequest req -> req.setRemoteAddr(clientIp); req } as RequestPostProcessor
    }

    /** {@code readyLatch}で全スレッドの準備を待ってから{@code startLatch}で一斉解放し、結果を集める。 */
    private static List<Integer> runConcurrently(int count, Closure<Integer> action) {
        ExecutorService executor = Executors.newFixedThreadPool(count)
        CountDownLatch readyLatch = new CountDownLatch(count)
        CountDownLatch startLatch = new CountDownLatch(1)
        try {
            def futures = (1..count).collect { i ->
                executor.submit({
                    readyLatch.countDown()
                    startLatch.await()
                    action.call(i)
                } as Callable<Integer>)
            }
            readyLatch.await(5, TimeUnit.SECONDS)
            startLatch.countDown()
            return futures.collect { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdown()
        }
    }

    def "#41 AC1/AC-neg1(N4・L3§3残件1): 同一usernameへの20並列失敗ログインでもfailed_attempt_countが閾値で頭打ちになる"() {
        given:
        int maxAttempts = loginAttemptProperties.maxAttempts()

        when:
        def statuses = runConcurrently(PARALLEL_COUNT) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(LOGIN_USERNAME, "wrong-password")))
                    .andReturn().response.status
        }

        then: "誤資格・ロック中いずれも既存の401で区別不能(列挙不可・AC3)"
        statuses.every { it == 401 }

        and: "failed_attempt_countは閾値ちょうどで頭打ち(修正前=check-then-actでは20まで伸びていた)"
        jdbcTemplate.queryForObject(
                "SELECT failed_attempt_count FROM t_login_attempt WHERE username = ?", Integer, LOGIN_USERNAME) == maxAttempts
    }

    def "#41 AC2/AC-neg2(N4・L3§3残件1): 同一client_ipへの20並列登録試行でもattempt_countが閾値で頭打ちになる"() {
        given:
        int maxAttempts = registerAttemptProperties.maxAttempts()

        when: "パスワード不一致にして枠通過後は必ず400で終わらせ、枠確保の並列制御のみを対象にする"
        def statuses = runConcurrently(PARALLEL_COUNT) { i ->
            mockMvc.perform(post("/api/register").with(csrf()).with(remoteAddr(REGISTER_CLIENT_IP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerBody("burst_reg_test_user_${i}")))
                    .andReturn().response.status
        }

        then: "全応答は枠内(400・パスワード不一致)か枠切れ(429)のいずれか"
        statuses.every { it == 400 || it == 429 }
        statuses.count { it == 429 } >= PARALLEL_COUNT - maxAttempts

        and: "attempt_countは閾値ちょうどで頭打ち(修正前=check-then-actでは20まで伸びていた)"
        jdbcTemplate.queryForObject(
                "SELECT attempt_count FROM t_register_attempt WHERE client_ip = ?", Integer, REGISTER_CLIENT_IP) == maxAttempts
    }

    def "S1: 同一usernameへのmaxAttempts並列の「成功」ログインは全て200を返す(誤ロックしない)"() {
        given:
        int maxAttempts = loginAttemptProperties.maxAttempts()

        when:
        def statuses = runConcurrently(maxAttempts) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(SUCCESS_LOGIN_USERNAME, LOGIN_RAW_PASSWORD)))
                    .andReturn().response.status
        }

        then: "S1: 照合前スロット確保は成功ログインも枠を消費するが、maxAttempts本ちょうどまでは全て200"
        statuses.every { it == 200 }
    }
}
