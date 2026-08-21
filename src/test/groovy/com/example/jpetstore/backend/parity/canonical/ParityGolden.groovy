package com.example.jpetstore.backend.parity.canonical

import com.fasterxml.jackson.annotation.JsonInclude

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
 *   "preconditions": { "EST-1": { "qtyBefore": 1, "qtyAfter": -1 } },
 *   "snapshot": { ... }
 * }
 * </pre>
 *
 * <p>{@code preconditions}はSM verification対応(c)で追加した任意項目。canonical比較の対象外
 * （{@code ParityComparator}は参照しない）のメタ情報で、採取時にDBへ問い合わせて実測した値を
 * 監査可能性のために残す（例: W3＝在庫不足シナリオの「注文前/後の実在庫」）。ほとんどのシナリオでは
 * {@code null}のまま。
 */
class ParityGolden {

    String scenario
    String expectation
    String divergenceId
    List<String> divergentFields = []
    CapturedFrom capturedFrom
    // 未使用(null)のシナリオではJSONに出さない(他8シナリオのgolden schemaへ差分ノイズを持ち込まない・
    // divergentFields等の既存フィールドのnull表現は変えない=フィールド単位のinclude指定に留める)。
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Map<String, Map<String, Object>> preconditions
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
