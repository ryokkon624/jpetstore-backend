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
| `presentation.rest`                          | REST コントローラ（例: `PingController`）        |
| `application.service`                        | 業務サービス（WHO 自動付与の pointcut 対象）     |
| `domain.enums`                               | 区分値 enum（`EnumGenerator` の**生成物**）      |
| `infrastructure.audit`                       | WHO カラム自動付与（AOP + MyBatis Interceptor）  |
| `infrastructure.mybatis.generated.{entity,mapper}` | MyBatis Generator の**生成物**             |
| `config`                                     | Spring 設定（`SecurityConfig` 等）               |
| `tool`                                       | 開発ツール（`EnumGenerator`）                    |

## 前提

`jpetstore-database` リポジトリで Docker Compose による MySQL 起動と Flyway マイグレーション（`flywayMigrate`）が完了していること。DB 接続は `jdbc:mysql://localhost:3306/jpetstore_db`（user/pass = `jpetstore`/`jpetstore`、ローカル開発用）。

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

## 正典（横断規約）

- アーキ規約: `../migration-agent-base/spec/architecture-conventions.md`（WHO カラムは §2、区分値/enum 生成は §3）
- セキュリティ基準: `../migration-agent-base/spec/security-baseline.md`（SBD-14 監査ログは WHO カラムで担保）
