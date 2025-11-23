# H2 Database Configuration

このプロジェクトは、開発・テスト用にH2データベースをサポートしています。

## 設定ファイル

### 1. `application.properties` (デフォルト)
デフォルトでH2データベースを使用する設定になっています。

### 2. `application-h2.properties` (H2プロファイル)
H2データベース専用の詳細設定です。開発時に使用します。

### 3. `application-postgres.properties` (PostgreSQLプロファイル)
本番環境用のPostgreSQL設定です。

## 使用方法

### H2データベースで起動する場合（デフォルト）

```bash
mvn spring-boot:run
```

または、H2プロファイルを明示的に指定:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

### PostgreSQLで起動する場合

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

または、環境変数で設定:

```bash
export SPRING_PROFILES_ACTIVE=postgres
mvn spring-boot:run
```

## H2 コンソールへのアクセス

アプリケーション起動後、以下のURLでH2コンソールにアクセスできます:

```
http://localhost:8080/h2-console
```

### 接続情報

- **JDBC URL**: `jdbc:h2:mem:filemanagerdb`
- **ユーザー名**: `sa`
- **パスワード**: (空欄)
- **ドライバークラス**: `org.h2.Driver`

## データベース設定の詳細

### H2データベース (開発・テスト用)

- **タイプ**: インメモリデータベース
- **永続化**: なし（アプリケーション停止時にデータは消去されます）
- **DDL自動生成**: `create-drop` (H2プロファイル使用時)
- **SQLログ**: 有効 (H2プロファイル使用時)

### PostgreSQL (本番環境用)

- **タイプ**: リレーショナルデータベース
- **永続化**: あり
- **DDL自動生成**: `update`
- **SQLログ**: 無効

## 環境変数での設定（PostgreSQL）

PostgreSQLを使用する場合、以下の環境変数で接続情報を設定できます:

```bash
DB_URL=jdbc:postgresql://localhost:5432/filemanager
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

## 依存関係

`pom.xml`に以下の依存関係が追加されています:

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

## 注意事項

1. H2データベースはインメモリで動作するため、アプリケーションを再起動するとデータは失われます
2. 開発・テスト目的でのみH2を使用してください
3. 本番環境ではPostgreSQLプロファイルを使用してください
4. H2コンソールは開発時のみ有効にし、本番環境では無効化してください
