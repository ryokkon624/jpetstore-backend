package com.example.jpetstore.backend.parity.verify

import spock.lang.Tag

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * {@link ParityIntegrationTestBase} 自体の疎通確認（#48・D1・SM申し送り①）。
 *
 * <p>サブクラスでの {@code @SpringBootTest(webEnvironment = RANDOM_PORT)} 再宣言が実際に効き、
 * Servletコンテナが実ポートで起動していることを、MockMvcではなく実HTTP（{@link HttpClient}）で
 * 到達確認することで実証する。効かない場合（webEnvironment=MOCKのまま）は {@code localServerPort}
 * が採番されないか、接続が確立できず本specが失敗する。
 */
@Tag("integration")
@Tag("parity")
class ParityIntegrationTestBaseSmokeSpec extends ParityIntegrationTestBase {

    def "RANDOM_PORTのサブクラス再宣言が効き、実HTTPでGET /api/pingへ到達できる"() {
        given:
        def client = HttpClient.newHttpClient()
        def request = HttpRequest.newBuilder(URI.create("${baseUrl()}/api/ping")).GET().build()

        when:
        def response = client.send(request, HttpResponse.BodyHandlers.ofString())

        then:
        localServerPort > 0
        response.statusCode() == 200
        response.body().contains('"status":"ok"')
    }
}
