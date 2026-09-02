# 運用ガイド

## 1. 起動方法

### 1.1 ローカル開発 (依存サービスなし)

```bash
mvn package -DskipTests
java -jar target/file-manager-0.0.1-SNAPSHOT.jar --spring.profiles.active=h2
```

`h2` プロファイルはプロファイルグループで `local-storage` を含む。DB は
インメモリ H2、ファイル実体はローカルファイルシステム (`LocalFileStorage`) に
保存されるため、PostgreSQL も S3 互換ストレージも不要。
初期管理者は `admin` / `admin`。

### 1.2 Docker Compose (PostgreSQL + Garage)

```bash
cp .env.example .env   # 値を設定する
docker compose --env-file .env up --build
```

秘密情報はすべて `.env` から渡す。`docker-compose.yml` にはコミットしない。

Garage の初回セットアップ (バケットとアクセスキーの作成) は README の
「7.1 初回セットアップ」を参照。

## 2. ストレージ

`FileStorage` インターフェースの実装をプロファイルで切り替える。

| プロファイル | 実装 | 用途 |
| :--- | :--- | :--- |
| (既定) | `S3FileStorage` | 本番。S3 互換ストレージ |
| `local-storage` | `LocalFileStorage` | 開発・テスト。単一ノード、冗長性なし |

`LocalFileStorage` は本番用ではない。

## 3. スキーマ管理

現在は `spring.jpa.hibernate.ddl-auto=update` でエンティティからスキーマを
生成している。本番では Flyway または Liquibase を導入し、`validate` に切り替える
ことを推奨する。インデックスは `FileEntity` の `@Table(indexes = ...)` で定義済み
(`parent_folder_id`, `owner_user_id`, `owner_group_id`, `name`, `deleted_at`)。

## 4. 削除処理

論理削除 → 猶予期間 → 物理削除の 2 段階。

| 設定 | 環境変数 | 既定値 |
| :--- | :--- | :--- |
| バッチ実行時刻 (cron) | `DELETION_CRON` | `0 0 2 * * *` (毎日 2:00) |
| 保持期間 (日) | `DELETION_RETENTION_DAYS` | `7` |

`ScheduledDeletionService` の挙動:

- フォルダを論理削除すると配下も同じタイムスタンプで論理削除される。
  復元も同じ単位で行われる。
- 物理削除では、ファイル本体に加えて**全バージョンの実体**とバージョン履歴行を
  削除する。
- 子を親より先に削除する。途中で失敗しても親を失った行が残らない。
- ストレージからの削除に失敗した場合は DB 行を残し、次回実行で再試行する。
  ログには `retryNextRun` の件数が出力される。

### 監視すべき点

- `Scheduled deletion job finished. deleted=N, retryNextRun=M` の `M` が
  連続して 0 より大きい場合、ストレージ側の障害または権限不足を疑う。
- 論理削除された行が保持期間を大きく超えて残り続けていないか。

## 5. ログ

| 出力 | レベル | 備考 |
| :--- | :--- | :--- |
| サービス層のメソッド呼び出しと引数 | DEBUG | 引数にはファイル名等の利用者データが含まれる |
| HTTP リクエスト開始・終了 | DEBUG | |
| 例外 | ERROR | スタックトレース付き |
| 削除バッチの結果 | INFO | |

引数や戻り値を INFO で出さないのは、利用者データがアプリケーションログに
恒久的に残るのを避けるため。監査が必要な場合は「誰がどのファイルに何をしたか」を
記録する専用の監査ログを別途設計すること。

初期管理者のパスワードはログに出力しない。既定値のまま起動した場合のみ、
変更を促す警告を出す。

## 6. バックアップとリストア

2 つのデータストアを**同時点で**取得する必要がある。片方だけ戻すと、実体のない
メタデータ (ダウンロードが失敗する) や、参照されない実体 (容量を消費し続ける)
が生じる。

1. アプリケーションを停止するか書き込みを止める
2. PostgreSQL を `pg_dump` で取得
3. オブジェクトストレージのバケットを同期 (`rclone sync` 等)
4. 書き込みを再開

### 不整合が起きた場合

- **メタデータはあるが実体がない**: ダウンロードが失敗する。該当行を特定し、
  バックアップから実体を戻すか、行を論理削除する。
- **実体はあるがメタデータがない**: 参照されない孤児オブジェクト。
  `files.storage_key` と `file_history.storage_key` の和集合に含まれない
  キーが対象。

## 7. API ドキュメント

Swagger UI: `/swagger-ui.html` (管理者のみ)
OpenAPI JSON: `/v3/api-docs`
