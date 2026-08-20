package com.example.jpetstore.backend.parity.capture

import com.example.jpetstore.backend.parity.ParityScenarios
import com.example.jpetstore.backend.parity.canonical.ParityGolden
import com.example.jpetstore.backend.parity.canonical.ParityGoldenIO
import com.example.jpetstore.backend.parity.canonical.ParitySnapshot

import java.time.Instant
import java.util.regex.Matcher

/**
 * legacyを実際に駆動しgolden JSONを生成する手動実行ツール（#48 AC6/AC7/AC8）。
 * {@code ./gradlew captureGolden} から実行する（legacy起動が前提）。
 *
 * <p>base URL / JDBC接続先 / golden出力先はすべてシステムプロパティで差し替え可能にする（AC7・F2。
 * 既定値をコードにハードコードしない）。{@code legacyCommit} は本ツール自身が
 * {@code git -C <legacyRepoPath> rev-parse HEAD} を解決する（AC8。取得できなければ書き出さずfail。
 * legacy repoがdirtyな場合もfail・{@code -Pparity.allowDirtyLegacy=true}で解除可）。
 */
class LegacyCaptureTool {

    static void main(String[] args) {
        String baseUrl = System.getProperty("parity.legacy.baseUrl", "http://localhost:8081/jpetstore/shop")
        String jdbcUrl = System.getProperty("parity.legacy.jdbcUrl", "jdbc:hsqldb:hsql://localhost:9002")
        String jdbcUser = System.getProperty("parity.legacy.jdbcUser", "sa")
        String jdbcPassword = System.getProperty("parity.legacy.jdbcPassword", "")
        String driverClassName = System.getProperty("parity.legacy.jdbcDriver", "org.hsqldb.jdbcDriver")
        String goldenDir = System.getProperty("parity.goldenDir", "src/test/resources/parity/golden")
        String legacyRepoPath = System.getProperty("parity.legacyRepoPath", "../legacy-jpetstore")
        boolean allowDirty = Boolean.getBoolean("parity.allowDirtyLegacy")
        String commitOverride = System.getProperty("parity.legacy.commit")

        String legacyCommit = commitOverride ?: resolveLegacyCommit(legacyRepoPath, allowDirty)
        String capturedAt = Instant.now().toString()

        LegacyHttpClient http = new LegacyHttpClient(baseUrl)
        LegacyDbReader db = new LegacyDbReader(jdbcUrl, jdbcUser, jdbcPassword, driverClassName)
        try {
            LegacyScenarioRunner runner = new LegacyScenarioRunner(http, db)
            ParityScenarios.ALL.each { ParityScenarios.Scenario scenario ->
                println("[captureGolden] running ${scenario.id} ...")
                // golden JSONは常に正規化済みの値で保存する(design.mdの例=scale(2)/キー昇順と一致させ、
                // 差分レビュー時に人が読んでそのまま信用できる状態にする。比較自体はComparator側でも
                // 改めてnormalize()するため、ここでの正規化は表示上の一貫性のため)。
                ParitySnapshot snapshot = runner.run(scenario.id).normalize()
                ParityGolden golden = new ParityGolden(
                        scenario: scenario.id,
                        expectation: scenario.expectation,
                        divergenceId: extractDivergenceId(scenario.expectation),
                        divergentFields: scenario.divergentFields,
                        capturedFrom: new ParityGolden.CapturedFrom(
                                legacyCommit: legacyCommit, capturedAt: capturedAt),
                        snapshot: snapshot)
                File file = new File(goldenDir, "${scenario.id}.json")
                ParityGoldenIO.writeToFile(golden, file)
                println("[captureGolden] wrote ${file} (expectation=${scenario.expectation})")
            }
        } finally {
            db.close()
        }
    }

    private static String extractDivergenceId(String expectation) {
        Matcher matcher = expectation =~ /INTENDED_DIVERGENCE\(([^)]+)\)/
        return matcher.find() ? matcher.group(1) : null
    }

    private static String resolveLegacyCommit(String legacyRepoPath, boolean allowDirty) {
        String status = runGit(legacyRepoPath, "status", "--porcelain")
        if (!status.isBlank() && !allowDirty) {
            throw new IllegalStateException(
                    "legacy repo(${legacyRepoPath})がdirty。golden採取はclean stateで行うこと" +
                            "(許容する場合は -Pparity.allowDirtyLegacy=true)。")
        }
        String sha = runGit(legacyRepoPath, "rev-parse", "HEAD").trim()
        if (sha.isBlank()) {
            throw new IllegalStateException("legacyのcommit shaを取得できなかった(AC8: 取得できなければ書き出さずfail)")
        }
        return sha
    }

    private static String runGit(String repoPath, String... gitArgs) {
        List<String> command = ["git", "-C", repoPath] + (gitArgs as List<String>)
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start()
        String output = process.inputStream.text
        process.waitFor()
        if (process.exitValue() != 0) {
            throw new IllegalStateException("git command failed: ${command.join(' ')}\n${output}")
        }
        return output
    }
}
