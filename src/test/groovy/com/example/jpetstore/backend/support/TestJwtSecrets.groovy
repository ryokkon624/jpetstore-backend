package com.example.jpetstore.backend.support

/**
 * テスト専用の JWT 署名鍵定数（本番秘密ではない）。
 *
 * <p>#38 AC1/AC2 で {@code JwtProperties} の起動時検証に denylist（既知 placeholder 値の拒否）と
 * 最小エントロピー（ユニーク文字数24以上）を追加したことに伴い、それまでテスト全体で使われていた
 * 低エントロピーな鍵 fixture（{@code "a" * 32} など）が軒並みコンストラクタ例外を起こすようになった。
 * ここに集約した値を使い回すことで、以降も同種の fixture 破綻を防ぐ。
 *
 * @see com.example.jpetstore.backend.infrastructure.security.JwtSecretPolicy
 */
class TestJwtSecrets {

    /** 32byte以上・ユニーク文字数24以上で denylist/エントロピー検証を両方通過する正当な鍵。 */
    static final String STRONG = "aB3dE5gH7jK9mN1pQ4rT6vW8xZ0cF2yL"

    /** {@code jpetstore-backend/.env.example} が配布している placeholder のリテラル値そのもの。denylist該当。 */
    static final String ENV_EXAMPLE_PLACEHOLDER = "please-replace-with-a-random-secret-of-at-least-32-bytes"

    /** 32byte以上だがユニーク文字数が24未満（低エントロピー）。denylist非該当・エントロピー検証で拒否される値。 */
    static final String LOW_ENTROPY = "a" * 40
}
