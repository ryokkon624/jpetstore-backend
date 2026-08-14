# テスト用スキーマ SQL（`jpetstore-database` からのコピー）

このディレクトリのファイル（`V00_000_*.sql`）は `jpetstore-database` リポジトリの
`flyway/sql/` を**コピーしたもの**です。Testcontainers 統合テスト（`IntegrationTestBase`）が
MySQL コンテナにスキーマを適用するために使用します。

- **コピー元**: `../jpetstore-database/flyway/sql/`（filesystem 参照ではなく、backend の
  test resources に取り込んで独立させている。理由: backend リポジトリ単体で `git clone` して
  テストが回せるようにするため）
- **同期が必要なタイミング**: `jpetstore-database` の `flyway/sql/` にマイグレーションが
  追加・変更されたら、このディレクトリも追従させる必要がある。同期を忘れると統合テストが
  古いスキーマ（存在しないテーブル/列）を前提に実行され、`jpetstore-database` 側の実スキーマと
  乖離したまま気づかない恐れがある。
- **同期コマンド**: リポジトリを兄弟ディレクトリ（`../jpetstore-database`）に clone 済みの状態で
  以下を実行する。

  ```
  ./gradlew syncTestSchema
  ```

  （`build.gradle` の `syncTestSchema` タスク。`../jpetstore-database/flyway/sql/*.sql` を
  このディレクトリへ上書きコピーする。）

- `flyway/sql-test`（開発用シードデータ・repeatable migration）はコピー対象外。統合テストの
  フィクスチャは各テストが自己完結で INSERT する方針とする（テスト間の暗黙依存を避けるため）。
