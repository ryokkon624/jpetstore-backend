package com.example.jpetstore.backend.parity.canonical

/**
 * golden JSON のスキーマ（#48 AC8・design.md 冒頭のcanonical例）。
 *
 * <pre>
 * {
 *   "scenario": "order-single-item",
 *   "expectation": "EQUIVALENT",
 *   "divergenceId": null,
 *   "divergentFields": [],
 *   "capturedFrom": { "legacyCommit": "&lt;sha&gt;", "capturedAt": "&lt;iso8601&gt;" },
 *   "snapshot": { ... }
 * }
 * </pre>
 */
class ParityGolden {

    String scenario
    String expectation
    String divergenceId
    List<String> divergentFields = []
    CapturedFrom capturedFrom
    ParitySnapshot snapshot

    static class CapturedFrom {
        String legacyCommit
        String capturedAt
    }

    /**
     * AC8: {@code capturedFrom.legacyCommit}/{@code capturedAt} が埋まっていないまま書き出すことを禁止する
     * （sha を取得できなければ書き出さずに fail させる）。
     */
    void validateMetaOrThrow() {
        if (capturedFrom == null
                || capturedFrom.legacyCommit == null
                || capturedFrom.legacyCommit.isBlank()
                || capturedFrom.capturedAt == null
                || capturedFrom.capturedAt.isBlank()) {
            throw new IllegalStateException(
                    "golden(scenario=${scenario}) の capturedFrom.legacyCommit/capturedAt が未設定。" +
                            "legacyのshaを取得できないまま golden を書き出してはならない(AC8)。")
        }
    }
}
