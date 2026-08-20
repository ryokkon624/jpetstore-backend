package com.example.jpetstore.backend.parity.verify

import groovy.transform.PackageScope

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * 新側（jpetstore-backend）を実HTTPで駆動する薄いクライアント（#48 AC9・F3）。
 *
 * <p>{@code java.net.http.HttpClient} は敢えて {@code CookieHandler} を設定しない。理由は
 * {@code jwt.cookie.secure: true}（既定）により発行される Cookie が {@code Secure} 属性付きであり、
 * {@link java.net.CookieManager} は plain HTTP（RANDOM_PORTのテストは {@code http://localhost}）へ
 * Secure Cookie を送らないため（design.md 発見4）。本番設定を歪めず対応するため、Cookie の保持・付与は
 * 本クラスが Cookie 名だけをキーに手動で行う（属性は無視）。
 *
 * <p>CSRF は「トークン Cookie が存在すれば削除・無ければ発行」の交互ローテーションが実測されている
 * （design.md F3・spikeで4回踏んだ落とし穴）。{@link #ensureCsrfToken} は非空の {@code XSRF-TOKEN} が
 * 得られるまで GET を繰り返す（上限付き）。空値／{@code Max-Age=0} の {@code Set-Cookie} は削除として扱う。
 */
class NewHttpClient {

    private static final String XSRF_COOKIE = "XSRF-TOKEN"
    private static final int CSRF_RETRY_LIMIT = 10

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()
    private final String baseUrl
    private final Map<String, String> cookies = [:]

    NewHttpClient(String baseUrl) {
        this.baseUrl = baseUrl
    }

    HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET())
    }

    HttpResponse<String> postJson(String path, String jsonBody) {
        String token = ensureCsrfToken()
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", token)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody)))
    }

    HttpResponse<String> putJson(String path, String jsonBody) {
        String token = ensureCsrfToken()
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/json")
                .header("X-XSRF-TOKEN", token)
                .PUT(HttpRequest.BodyPublishers.ofString(jsonBody)))
    }

    HttpResponse<String> delete(String path) {
        String token = ensureCsrfToken()
        return send(HttpRequest.newBuilder(uri(path))
                .header("X-XSRF-TOKEN", token)
                .DELETE())
    }

    /**
     * 非空の {@code XSRF-TOKEN} Cookie が得られるまで {@code GET /api/ping} を繰り返す。
     * 交互ローテーション（F3）に対応するための唯一の正攻法（spikeで実証済み）。
     *
     * @throws IllegalStateException 上限回数（{@value #CSRF_RETRY_LIMIT}）以内に取得できなかった場合
     *     （試行履歴つきで fail させる。SM申し送り①）
     */
    String ensureCsrfToken() {
        List<String> attempts = []
        for (int attempt = 1; attempt <= CSRF_RETRY_LIMIT; attempt++) {
            get("/api/ping")
            String token = cookies[XSRF_COOKIE]
            attempts << "attempt ${attempt}: ${token ? 'present' : 'absent'}".toString()
            if (token) {
                // Groovyの.each{}クロージャ内returnはループを抜けない(continue相当)ため、
                // 実クロージャの外まで確実に抜けられる素のforループを使う(スモークで踏んだ罠)。
                return token
            }
        }
        throw new IllegalStateException(
                "XSRF-TOKEN Cookieが${CSRF_RETRY_LIMIT}回のGET /api/pingでも取得できなかった: ${attempts.join(', ')}")
    }

    /** ログインしCookie（ACCESS_TOKEN/REFRESH_TOKEN）を保持する。CSRF付きPOSTのため事前にトークンを確保する。 */
    HttpResponse<String> login(String username, String password) {
        String json = /{"username":"${escape(username)}","password":"${escape(password)}"}/
        return postJson("/api/auth/login", json)
    }

    @PackageScope
    Map<String, String> cookieSnapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(cookies))
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) {
        if (!cookies.isEmpty()) {
            builder.header("Cookie", cookies.collect { k, v -> "${k}=${v}" }.join("; "))
        }
        HttpResponse<String> response =
                httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        captureCookies(response)
        return response
    }

    private void captureCookies(HttpResponse<String> response) {
        response.headers().allValues("set-cookie").each { String header ->
            String firstPair = header.split(";", 2)[0]
            int eq = firstPair.indexOf('=')
            if (eq <= 0) {
                // 不正な形式のSet-Cookieを1件スキップして次のヘッダへ進む(.each{}内のreturnはcontinue相当
                // ＝正しい実装。ensureCsrfTokenのループ抜けバグとは意味論が逆なので機械的に統一しないこと)。
                return
            }
            String name = firstPair.substring(0, eq).trim()
            String value = firstPair.substring(eq + 1).trim()
            boolean maxAgeZero = header.toLowerCase().contains("max-age=0")
            if (value.isEmpty() || maxAgeZero) {
                cookies.remove(name)
            } else {
                cookies[name] = value
            }
        }
    }

    private URI uri(String path) {
        return URI.create("${baseUrl}${path}")
    }

    private static String escape(String value) {
        return value.replace('\\', '\\\\').replace('"', '\\"')
    }
}
