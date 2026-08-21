package com.example.jpetstore.backend.parity.canonical

/**
 * canonical スナップショットのフィールド単位デルタ比較 + expectation 判定（#48 AC4・AC-neg1）。
 *
 * <p>判定規則（design.md §2・backlog AC4）:
 * <pre>
 * EQUIVALENT              かつ 差分なし              -&gt; pass
 * EQUIVALENT              かつ 差分あり              -&gt; fail（台帳に無い差分＝欠陥候補）
 * INTENDED_DIVERGENCE(ID) かつ 差分なし              -&gt; fail（台帳の形骸化）
 * INTENDED_DIVERGENCE(ID) かつ divergentFieldsと一致 -&gt; pass
 * INTENDED_DIVERGENCE(ID) かつ 宣言外の差分あり      -&gt; fail（Q4確定）
 * </pre>
 */
class ParityComparator {

    static final String EQUIVALENT = "EQUIVALENT"

    /** フィールド単位の差分1件（AC-neg1: どのフィールドが食い違ったか判別可能な粒度）。 */
    static class FieldDiff {
        final String field
        final String legacyValue
        final String newValue

        FieldDiff(String field, String legacyValue, String newValue) {
            this.field = field
            this.legacyValue = legacyValue
            this.newValue = newValue
        }

        @Override
        String toString() {
            return "field=${field} golden(legacy)=\"${legacyValue}\" actual(new)=\"${newValue}\""
        }
    }

    static class Result {
        final String scenario
        final boolean pass
        final List<FieldDiff> diffs
        final String message

        Result(String scenario, boolean pass, List<FieldDiff> diffs, String message) {
            this.scenario = scenario
            this.pass = pass
            this.diffs = diffs
            this.message = message
        }

        @Override
        String toString() {
            return message
        }
    }

    /**
     * @param scenario シナリオID（失敗メッセージ・ログの特定に使う）
     * @param expectation {@code "EQUIVALENT"} または {@code "INTENDED_DIVERGENCE(ID-x)"}
     * @param divergentFields expectationがINTENDED_DIVERGENCEの場合に台帳が宣言するフィールド名の集合
     * @param golden 旧側（legacy）canonical snapshot
     * @param actual 新側（new）canonical snapshot
     */
    static Result compare(
            String scenario,
            String expectation,
            List<String> divergentFields,
            ParitySnapshot golden,
            ParitySnapshot actual) {
        ParitySnapshot g = golden.normalize()
        ParitySnapshot a = actual.normalize()
        List<FieldDiff> diffs = diffFields(g, a)
        Set<String> diffFieldNames = diffs.collect { it.field } as Set
        Set<String> declared = (divergentFields ?: []) as Set

        boolean pass
        String message
        if (expectation == EQUIVALENT) {
            pass = diffs.isEmpty()
            message = pass
                    ? "scenario=${scenario}: EQUIVALENT (差分なし)"
                    : "scenario=${scenario}: EQUIVALENT宣言だが差分あり(台帳に無い差分=欠陥候補) -> ${diffs.join(', ')}"
        } else if (diffs.isEmpty()) {
            pass = false
            message = "scenario=${scenario}: ${expectation}宣言だが実測が一致した(台帳の形骸化) declaredFields=${declared}"
        } else if (diffFieldNames == declared) {
            pass = true
            message = "scenario=${scenario}: ${expectation} 宣言どおりの差分を確認 -> ${diffs.join(', ')}"
        } else {
            pass = false
            Set<String> undeclared = diffFieldNames - declared
            message = "scenario=${scenario}: ${expectation}宣言だが宣言外の差分あり(Q4) " +
                    "undeclaredFields=${undeclared} -> ${diffs.join(', ')}"
        }
        return new Result(scenario, pass, diffs, message)
    }

    private static List<FieldDiff> diffFields(ParitySnapshot g, ParitySnapshot a) {
        List<FieldDiff> diffs = []
        if (g.outcome != a.outcome) {
            diffs << new FieldDiff("outcome", "${g.outcome}", "${a.outcome}")
        }
        if (g.ordersCreated != a.ordersCreated) {
            diffs << new FieldDiff("ordersCreated", "${g.ordersCreated}", "${a.ordersCreated}")
        }
        if (g.orderTotal != a.orderTotal) {
            diffs << new FieldDiff("orderTotal", "${g.orderTotal}", "${a.orderTotal}")
        }
        if (g.accountsCreated != a.accountsCreated) {
            diffs << new FieldDiff("accountsCreated", "${g.accountsCreated}", "${a.accountsCreated}")
        }
        if (g.httpStatus != a.httpStatus) {
            diffs << new FieldDiff("httpStatus", "${g.httpStatus}", "${a.httpStatus}")
        }
        if (g.stackTraceExposed != a.stackTraceExposed) {
            diffs << new FieldDiff("stackTraceExposed", "${g.stackTraceExposed}", "${a.stackTraceExposed}")
        }

        Set<String> accountKeys = (g.account.keySet() + a.account.keySet())
        accountKeys.sort().each { String key ->
            String gv = g.account[key]
            String av = a.account[key]
            if (gv != av) {
                diffs << new FieldDiff("account[${key}]", "${gv}", "${av}")
            }
        }

        Set<String> itemIds = (g.inventoryDelta.keySet() + a.inventoryDelta.keySet())
        itemIds.sort().each { String itemId ->
            Integer gv = g.inventoryDelta[itemId]
            Integer av = a.inventoryDelta[itemId]
            if (gv != av) {
                diffs << new FieldDiff("inventoryDelta[${itemId}]", "${gv}", "${av}")
            }
        }

        Set<String> lineItemIds = ((g.lines*.itemId) + (a.lines*.itemId)) as Set
        lineItemIds.sort().each { String itemId ->
            ParitySnapshot.Line gl = g.lines.find { it.itemId == itemId }
            ParitySnapshot.Line al = a.lines.find { it.itemId == itemId }
            if (gl?.quantity != al?.quantity) {
                diffs << new FieldDiff("lines[${itemId}].quantity", "${gl?.quantity}", "${al?.quantity}")
            }
            if (gl?.unitPrice != al?.unitPrice) {
                diffs << new FieldDiff("lines[${itemId}].unitPrice", "${gl?.unitPrice}", "${al?.unitPrice}")
            }
            if (gl?.productName != al?.productName) {
                diffs << new FieldDiff("lines[${itemId}].productName", "${gl?.productName}", "${al?.productName}")
            }
        }

        if (g.entries != a.entries) {
            diffs << new FieldDiff("entries", "${g.entries}", "${a.entries}")
        }

        return diffs
    }
}
