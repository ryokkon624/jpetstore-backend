package com.example.jpetstore.backend.parity.capture

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * legacy（Struts1.2 .do）を実HTTPで駆動する薄いクライアント（#48 AC6・design.md §7.1で実証済みの駆動経路専用）。
 *
 * <p>JSESSIONIDはCookieヘッダで手動保持する（{@link java.net.CookieManager}には依存しない。
 * {@code verify.NewHttpClient} と同方針）。legacyはStruts標準で {@code ;jsessionid=...} をURLへも
 * 埋め込むが、Cookie経由のセッション追跡が機能するため本クライアントはCookieのみで完結させる。
 */
class LegacyHttpClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    private final String baseUrl
    private final Map<String, String> cookies = [:]

    LegacyHttpClient(String baseUrl) {
        this.baseUrl = baseUrl
    }

    HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET())
    }

    HttpResponse<String> postForm(String path, Map<String, String> form) {
        String body = form.collect { k, v -> "${urlEncode(k)}=${urlEncode(v)}" }.join("&")
        return send(HttpRequest.newBuilder(uri(path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)))
    }

    /** 新しいHTTPセッションを強制する（F4: シナリオ間のカート残留対策）。既存のJSESSIONIDを破棄する。 */
    void resetSession() {
        cookies.clear()
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
                return
            }
            String name = firstPair.substring(0, eq).trim()
            String value = firstPair.substring(eq + 1).trim()
            if (value.isEmpty()) {
                cookies.remove(name)
            } else {
                cookies[name] = value
            }
        }
    }

    private URI uri(String path) {
        return URI.create("${baseUrl}${path}")
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, "UTF-8")
    }
}
