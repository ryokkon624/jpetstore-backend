package com.example.jpetstore.backend.config

import com.example.jpetstore.backend.JpetstoreBackendApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.WebApplicationType
import spock.lang.Specification

/**
 * 実アプリのブートストラップ経路（{@link JpetstoreBackendApplication}）で、環境変数
 * (DB_USERNAME/DB_PASSWORD/JWT_SECRET) が未設定のとき本当に起動失敗するかを検証する
 * (ApplicationContextRunner は ConfigDataEnvironmentPostProcessor を経由しないため
 * application.yml のプレースホルダ未解決の挙動を再現できない。実ブート経路で確認する)。
 */
class ApplicationBootFailFastSpec extends Specification {

    def "環境変数が一切未設定だと実アプリの起動に失敗する"() {
        when:
        new SpringApplicationBuilder(JpetstoreBackendApplication)
                .web(WebApplicationType.NONE)
                .run()

        then:
        Exception ex = thrown(Exception)
        println "startup failed with: ${ex.class.name}: ${ex.message}"
    }

    /** 例外チェーン全体（cause を辿った toString）を連結する。原因メッセージが cause 側にあることが多いため。 */
    private static String causeChain(Throwable t) {
        StringBuilder sb = new StringBuilder()
        Throwable cur = t
        while (cur != null) {
            sb.append(cur.toString()).append(" | ")
            cur = cur.cause
        }
        return sb.toString()
    }

    def "JWT_SECRETのみ設定してもDB_USERNAME/DB_PASSWORD未設定なら起動に失敗する"() {
        given:
        // SpringApplicationBuilder#properties() は defaultProperties（最低優先）扱いのため、
        // application.yml の "${JWT_SECRET}" プレースホルダに勝てない。System property として
        // 環境変数相当の優先度で注入する。
        System.setProperty("JWT_SECRET", "a" * 32)

        when:
        new SpringApplicationBuilder(JpetstoreBackendApplication)
                .web(WebApplicationType.NONE)
                .run()

        then:
        Exception ex = thrown(Exception)
        println "startup failed with: ${causeChain(ex)}"
        causeChain(ex).contains("DB_USERNAME")

        cleanup:
        System.clearProperty("JWT_SECRET")
    }
}
