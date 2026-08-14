# jpetstore-backend

モダン版 JPetStore の **REST API** リポジトリ。レガシー版（`legacy-jpetstore`）を Java 21 / Spring Boot 4.x へ刷新した after 側のバックエンド。

- polyrepo 3本構成のひとつ（`jpetstore-frontend` / **`jpetstore-backend`** / `jpetstore-database`）。
- スコープ外: batch・mobile・S3・mail・infra（当面）。

## 技術スタック

| 項目           | 採用                                                              |
| -------------- | ---------------------------------------------------------------- |
| 言語 / ランタイム | Java 21                                                          |
| フレームワーク | Spring Boot 4.1.0 / Spring Security / Spring AOP                  |
| O/R マッパー   | MyBatis（mybatis-spring-boot-starter 4.1.0 / MyBatis Generator） |
| DB             | MySQL 8.4（`jpetstore_db`）                                       |
| API ドキュメント | springdoc-openapi（Swagger UI）                                  |
| テスト         | Spock（Groovy 5）/ Testcontainers / Flyway（test）               |
| フォーマッタ   | Spotless（google-java-format 1.32.0）                            |

## パッケージ構成

ベースパッケージ: `com.example.jpetstore.backend`

| パッケージ                                   | 役割                                             |
| -------------------------------------------- | ------------------------------------------------ |
| `presentation.rest`                          | REST コントローラ（例: `PingController`・`AuthController`・`SecuredPingController`） |
| `presentation.rest.exception`                | 正規化エラーハンドリング（`GlobalExceptionHandler`・`ErrorResponse`。AC4） |
| `presentation.rest.security`                 | Security の応答系 SPI 実装（`AuditingAccessDeniedHandler`・`AuditingAuthenticationEntryPoint`。AC3/AC7） |
| `application.service`                        | 業務サービス（WHO 自動付与の pointcut 対象。例: `AuthApplicationService`） |
| `domain.enums`                               | 区分値 enum（`EnumGenerator` の**生成物**）      |
| `domain.security`                            | 認証プリンシパル（`AuthenticatedUser`）と取得口（`CurrentUserProvider`）。AC2 |
| `domain.exception`                           | 基底例外型（`ResourceNotFoundException`・`OptimisticLockConflictException`）。AC4/AC8 |
| `domain.concurrency`                         | 並行制御ヘルパ（`AffectedRows`）。AC8            |
| `infrastructure.audit`                       | WHO カラム自動付与（AOP + MyBatis Interceptor）＋監査ログ記録ファサード（`AuditLogRecorder`）。AC7 |
| `infrastructure.security`                    | JWT 発行/検証・Cookie 読み書き・認証フィルタ・CSRF 補助フィルタ。AC3 |
| `infrastructure.mybatis.generated.{entity,mapper}` | MyBatis Generator の**生成物**             |
| `infrastructure.mybatis.custom.{entity,mapper}` | 手書きの entity/mapper（生成物ではないが規約上 Infrastructure に閉じる。例: `AuditLogCustomEntity`/`AuditLogCustomMapper`。命名は `XxxCustomEntity`/`XxxCustomMapper`） |
| `config`                                     | Spring 設定（`SecurityConfig`・`RequiredSecretsValidator`）|
| `tool`                                       | 開発ツール（`EnumGenerator`）                    |

## 前提

`jpetstore-database` リポジトリで Docker Compose による MySQL 起動と Flyway マイグレーション（`flywayMigrate`）が完了していること。DB 接続は `jdbc:mysql://localhost:3306/jpetstore_db`。

## 秘密管理（環境変数・AC5/AC-neg1）

DB 資格情報・JWT 署名鍵はソースにデフォルト値を持たせていない。**未設定だと起動失敗（fail-fast）**。

| 環境変数 | 用途 | 備考 |
| --- | --- | --- |
| `DB_USERNAME` / `DB_PASSWORD` | DB 接続資格情報 | ローカル開発は `jpetstore-database` の docker-compose 既定値（`jpetstore`/`jpetstore`） |
| `JWT_SECRET` | JWT 署名鍵（HS256） | 最小 32byte。起動時に鍵長を検証する（`JwtProperties`） |

1. `.env.example` を `.env` にコピーして値を設定する（`.env` は `.gitignore` 対象）。
2. 起動前にシェルの環境変数として読み込む。

   ```bash
   # bash
   set -a; source .env; set +a
   ./gradlew bootRun
   ```

   ```powershell
   # PowerShell（1行ずつ読み込む簡易例）
   Get-Content .env | ForEach-Object { if ($_ -match '^\s*([^#=]+)=(.*)$') { [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2]) } }
   ./gradlew bootRun
   ```

⚠️ **既知の注意点**: `spring.datasource.username`/`password` のような `@ConfigurationProperties`（Binder）経由でバインドされる値は、`${DB_USERNAME}` が未解決でも例外を投げず**リテラル文字列のまま素通り**する（実際に DB へ接続を試みて初めて失敗が顕在化する）。これは `@Value` による解決（未解決なら即例外）と挙動が異なる。本プロジェクトでは `config.RequiredSecretsValidator`（`@Value` で強制解決するだけの Bean）を置き、DB 資格情報も JWT 鍵と同様に**起動時点で**確実に fail-fast するようにしている。

## 起動

```bash
./gradlew bootRun
# 疎通確認
curl http://localhost:8080/api/ping   # => {"status":"ok"}
# Swagger UI: http://localhost:8080/swagger-ui.html
```

## コード生成

### entity / mapper（MyBatis Generator）

`generatorConfig.xml` の `<table>` 要素にスキーマ確定後のテーブルを列挙してから実行する（雛形時点では未定義）。

```bash
./gradlew mybatisGenerator
```

- entity → `com.example.jpetstore.backend.infrastructure.mybatis.generated.entity`
- mapper → `com.example.jpetstore.backend.infrastructure.mybatis.generated.mapper` + `resources/mapper/generated`

### 区分値 enum（EnumGenerator）

`m_code` テーブルから区分値 enum を生成し、`domain/enums/*.java` に**ソースツリー直下（コミット対象）**として出力する。区分値を足す＝ `m_code` に登録 → 再実行。

```bash
./gradlew generateEnums
```

- `code_type_name_en` → クラス名、`display_name_en` → 定数名、`code_value` → `getCode()` / `fromCode()`、`CodeEnum` 実装。

## WHO カラム自動付与の仕組み

全業務テーブルの `create_program` / `update_program`（`ClassName#method` テキスト）を **AOP + MyBatis Interceptor で自動付与**する。`ProgramType` enum も m_code `0012` も使わない（決定 D2/D3）。

1. **`ProgramContextAspect`**（AOP `@Around`）
   `..application.service..` の全メソッドを囲む。enter で `ProgramContext.setIfAbsent("ClassName#method")` を呼び、ThreadLocal が空だったメソッドだけが **owner** になる（**set-once・最外の業務サービスが勝つ**）。owner が finally で `clear()`。
2. **`AuditProgramInterceptor`**（MyBatis `Executor#update` intercept）
   INSERT/UPDATE 時、対象エンティティの `createProgram`（INSERT のみ）/ `updateProgram`（INSERT・UPDATE）が未設定なら `ProgramContext` の値で補完する。空なら `"SYSTEM"`。既に明示設定があれば尊重。
   ※ MyBatis Generator 生成エンティティ（POJO）を param とする利用を前提（Map param は setter 判定が緩く意図しない put が起こり得るため）。

これにより `OrderService#placeOrder` → `CommonWriteService.insert()` と潜っても、記録は最外の `OrderService#placeOrder`（機能の入口）になる。

## 認可・JWT認証・CSRF（AC2/AC3・SBD-1/3/4/15）

- **認可はサービス/ドメイン層・認証プリンシパル基準**。`domain.security.AuthenticatedUser`（userId/username/roles）を
  Spring Security の `Authentication#getPrincipal()` に直接格納し、`CurrentUserProvider`（実装:
  `SecurityContextCurrentUserProvider`）経由で取得する。リクエストパラメータは認可に使わない。
- `@EnableMethodSecurity` を有効化済み（`SecurityConfig`）。`@PreAuthorize` 等が利用可能。
- 認証は **JWT を httpOnly Cookie に載せる方式**（stateless セッション）。`JwtService`（jjwt 0.12・access/refresh とも
  自己完結トークン）・`AuthCookieSupport`（Secure/HttpOnly/SameSite/Path/Max-Age を secure-by-default で付与）・
  `JwtAuthenticationFilter`（Cookie→SecurityContext）で構成する。
- **refresh**: `POST /api/auth/refresh`（credential 不要・refresh Cookie のみで access を再発行）。credential
  を交換する初回ログイン API（`UserDetailsService` の DB 結線含む）は #21 の範囲。refresh token はローテーションしない
  （失効管理は短命 access ＋期限切れ待ちに割り切り。revocation store は後続）。
- **CSRF**（Cookie 認証のため必須）: `CookieCsrfTokenRepository.withHttpOnlyFalse()` で `XSRF-TOKEN` Cookie を発行し、
  `CsrfTokenRequestAttributeHandler`（**Xor ではない**。cookie-to-header の raw 値をそのまま検証するため。公式 SPA
  ガイド準拠）で検証する。`CsrfCookieFilter` が毎リクエストでトークン解決を強制する（Spring Security の遅延解決
  のままだと `XSRF-TOKEN` Cookie が発行されないため）。GET は Spring Security の既定どおり CSRF 対象外。
  SameSite は `jwt.cookie.same-site`（既定 `Strict`）。SPA が same-site で配信できない場合は `Lax` に変更する。
- `JwtAuthenticationFilter` は `@Component` だが、Boot の自動フィルタ登録による二重実行を防ぐため
  `SecurityConfig` で `FilterRegistrationBean#setEnabled(false)` にしている（既知の Spring Boot 作法）。

## 例外正規化・監査ログ（AC4/AC7・SBD-10/SBD-14）

- `GlobalExceptionHandler`（`@RestControllerAdvice`）が 400/401/403/404/409/500 を `ErrorResponse`
  （code/message/path/timestamp）に正規化する。スタックトレース・内部パス・依存版数は返さない
  （`spring.web.error.*`〔Spring Boot 4 で `server.error.*` から移動〕も whitelabel フォールバック用に抑止済み）。
- **認可失敗の記録は二重経路**: URL パターン単位の拒否（`authorizeHttpRequests`）は Security フィルタチェーンの
  `AuditingAccessDeniedHandler`/`AuditingAuthenticationEntryPoint` が捕捉するが、`@PreAuthorize` 由来の拒否は
  DispatcherServlet 内で `GlobalExceptionHandler` が先に捕捉し フィルタチェーン側へは伝播しない（実機検証で判明）。
  そのため両方に `AuditLogRecorder.recordAuthzFailure` を結線している。
- `AuditLogRecorder`（`infrastructure.audit`。ファサード）は `t_audit_log`（`jpetstore-database` V00_000_006）に
  認可失敗（`AUTHZ_FAILURE`/`DENIED`）・状態変更（`STATE_CHANGE`。呼び出しは後続ドメイン Story）を記録する。
  `create_program`/`update_program` は既存の WHO Interceptor が自動補完する（認可失敗時は ProgramContext が
  空のため `"SYSTEM"` になる。許容仕様）。
  永続化の実体（`AuditLogCustomEntity`/`AuditLogCustomMapper`）はカスタムマッパー規約に従い
  `infrastructure.mybatis.custom.{entity,mapper}` に置く（手書きである理由は同クラスの Javadoc 参照。
  要点: `t_audit_log` は追記専用のため MyBatis Generator で update/delete を生成させたくない）。

## 並行制御（AC8・arch §4）

- **編集系（version 楽観ロック）**: `UPDATE t SET ..., version = version + 1 WHERE pk=:id AND version=:readVersion`。
  affected rows が 0 なら競合 → `domain.concurrency.AffectedRows.requireUpdated(rows)` が
  `OptimisticLockConflictException` を投げ、`GlobalExceptionHandler` が **HTTP 409** に統一マッピングする。
- **在庫等のガード付きアトミック減算**: `UPDATE ... WHERE qty >= :n`。affected rows が 0 は「在庫不足」等、409 とは
  異なる意味になるため `AffectedRows.requireUpdated(rows, () -> new XxxException(...))` で任意の例外に差し替える
  （実際の在庫サービスは #8 の範囲）。
- **`@Transactional` 方針**（後続ドメイン Story が従うルール）:
  - 状態変更を行うユースケース（例: 注文確定・account 編集）は Application Service のメソッドに
    `@Transactional` を付与し、**all-or-nothing** にする（例: 注文ヘッダ＋明細＋在庫減算を1トランザクションで）。
  - 参照系メソッドは `@Transactional(readOnly = true)`。
  - 複数行を更新する場合（例: カート内の複数商品の在庫減算）は **固定順**（例: `item_id` 昇順）で更新し、
    同時実行どうしのデッドロックを回避する。
  - 分離レベルは MySQL 既定（REPEATABLE READ）でよい。正しさはガード付き UPDATE の行ロック／`version` 楽観ロック
    が担保するため、分離レベルに依存させない。

## 統合テスト（Testcontainers・AC全般）

- `support.IntegrationTestBase`（Spock）が Docker 上に MySQL 8.4 を1つ起動し（Singleton container）、Spring Boot
  の Flyway 自動設定でスキーマを適用したうえでアプリケーションコンテキスト全体を起動する。
- 適用するスキーマ SQL は `jpetstore-database` の `flyway/sql` を **`src/test/resources/flyway/sql` にコピー**した
  もの（filesystem 参照ではなく backend 単体で clone してテストできるようにするため）。**`jpetstore-database` の
  スキーマ変更時は同期が必要**（同期コマンド・理由は `src/test/resources/flyway/sql/README.md` 参照）。

  ```bash
  ./gradlew syncTestSchema
  ```

- UT/IT は `@Tag("integration")` で分離する。

  ```bash
  ./gradlew test            # UT（integration タグ除外）
  ./gradlew integrationTest # IT（Docker 必要）
  ```

- 保護テストエンドポイント `GET /api/secured/ping`（`@PreAuthorize("hasRole('ADMIN')")`）は AC2/AC3/AC4/AC8 の
  secure-by-default 基盤を実証するための唯一のテスト用エンドポイント。`simulateError` パラメータ
  （`notFound`/`conflict`/`illegalArgument`/`unexpected`）で例外正規化・409 マッピングも同じエンドポイントで検証する
  （`SecurityEndToEndSpec`）。

## 正典（横断規約）

- アーキ規約: `../migration-agent-base/spec/architecture-conventions.md`（WHO カラムは §2、区分値/enum 生成は §3）
- セキュリティ基準: `../migration-agent-base/spec/security-baseline.md`（SBD-14 監査ログは WHO カラムで担保）
