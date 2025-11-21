## 📁 ファイル管理システム要件定義 (README)

### 1\. 🎯 目的

Spring Boot、JPA、Thymeleafを用いて、**ユーザーグループ**と**ユーザー**に基づいた**Linux類似のアクセス権限モデル**を持つ、堅牢でスケーラブルなファイル管理システムを構築する。ファイル本体は外部ストレージに保存し、データベースはメタデータ管理に特化する。

### 2\. ✨ 主要機能要件

| No. | 機能カテゴリ | 機能概要 | 備考 |
| :--- | :--- | :--- | :--- |
| 2.1 | **ファイル/フォルダ操作** | アップロード、ダウンロード、リネーム、移動、削除（論理削除） | |
| 2.2 | **フォルダ階層管理** | フォルダの作成、階層構造での表示、ナビゲーション | |
| 2.3 | **ファイル検索** | ファイル名、メタデータによる検索 | |
| 2.4 | **ユーザー/グループ管理** | ユーザー、グループの作成、編集、削除。ユーザーのグループ所属管理 | (管理者機能として実装) |
| 2.5 | **権限管理** | ファイル/フォルダに対するLinux風パーミッション設定、参照 | |

### 3\. ⚙️ 技術スタックと構成

| カテゴリ | 技術/サービス | 備考 |
| :--- | :--- | :--- |
| **フレームワーク** | Spring Boot | Webアプリケーションの基盤 |
| **画面** | Thymeleaf | サーバーサイドテンプレートエンジン |
| **O/Rマッピング** | Spring Data JPA (Hibernate) | データ永続化 |
| **データベース** | H2 Database | 開発環境でPostgreSQL互換モードで使用 |
| **外部ストレージ** | S3互換ストレージ (MinIOを想定) | ファイル本体の保存 |
| **コンテナ化** | Docker, Docker Compose | 環境構築の自動化 |

### 4\. 🔒 権限管理の詳細仕様

ファイルおよびフォルダごとに、**所有者**、**所属グループ**、**その他のユーザー**に対して、以下の**Linuxパーミッションに類似した権限**を設定します。

| 権限 | 記号 | 説明 |
| :--- | :--- | :--- |
| **読み取り** | `r` (4) | ファイルの内容を読み取る、フォルダの内容を一覧表示する |
| **書き込み** | `w` (2) | ファイルの内容を変更・上書きする、フォルダ内でファイル/フォルダを作成・削除する |
| **実行** | `x` (1) | ファイルを実行する (このシステムでは未使用)、フォルダにアクセスする/移動する |

  * **権限フィールド**: DBのファイル/フォルダのメタデータに、**所有者権限**、**グループ権限**、**その他権限**の3つのフィールド（例: 8進数`750`など）を持たせる。

### 5\. 🏷️ メタデータの詳細仕様

ファイルおよびフォルダには、検索性を高めるために以下の**メタデータ**を付与できるようにします。

| No. | フィールド名 | データ型 | 説明 | 備考 |
| :--- | :--- | :--- | :--- | :--- |
| 5.1 | `name` | String | ファイル/フォルダ名 | 必須 |
| 5.2 | `is_directory` | Boolean | フォルダであるか否か | 必須 |
| 5.3 | `owner_user_id` | Long | 所有ユーザーID | 必須 |
| 5.4 | `owner_group_id`| Long | 所有グループID | 必須 |
| 5.5 | `permissions` | String / Integer | Linuxパーミッション (例: `750`) | 必須 |
| 5.6 | `storage_key` | String | S3互換ストレージ内のキー | ファイルの場合必須 |
| 5.7 | `custom_tags` | JSON / Text | ユーザーが任意に設定できる検索用タグ | 任意。複数設定可能。 |
| 5.8 | `description` | Text | ファイル/フォルダの簡単な説明 | 任意 |
| 5.9 | `created_at` | Timestamp | 作成日時 | 自動設定 |
| 5.10| `updated_at` | Timestamp | 最終更新日時 | 自動設定 |
| 5.11| `deleted_at` | Timestamp | 論理削除日時 | 論理削除時に設定 |

### 6\. 🗑️ 削除処理の詳細仕様

#### 6.1. 論理削除 (Soft Delete)

  * ユーザーがファイルを削除操作すると、DBのメタデータレコードの\*\*`deleted_at`フィールドに現在時刻が設定\*\*され、**ファイル本体はS3互換ストレージに残されたまま**ユーザーインターフェースから非表示になります。

#### 6.2. 物理削除 (Hard Delete)

  * **物理削除スケジュール**: サービスとして、`deleted_at`が設定されたレコードを定期的にチェックするバッチ処理（例: Spring Scheduler）を用意します。
  * **猶予期間**: `deleted_at`が設定されてから**7日間**（または設定可能期間）が経過したレコードに対し、以下の処理を実行します。
    1.  S3互換ストレージから**ファイル本体を物理削除**する。
    2.  DBから**メタデータレコードを物理削除**する。

### 7\. ☁️ S3互換ストレージ連携

  * **ファイル保存**: ファイルアップロード時、ファイル本体はS3互換サービス（例: MinIO）に保存され、その一意のキー（`storage_key`）のみがDBに保存されます。
  * **ファイル取得**: ファイルダウンロード時、DBの`storage_key`を用いてS3互換サービスからファイルを取得し、ユーザーに提供します。
  * **利用ライブラリ**: Spring Cloud AWS S3またはMinIOクライアントライブラリを使用します。

### 8\. 🐳 Docker Compose ファイル構成案

開発環境を容易に起動するため、以下のサービスを含む`docker-compose.yml`を提供します。

```yaml
version: '3.8'
services:
  # 1. PostgreSQL (H2互換DB)
  db:
    image: postgres:14-alpine
    container_name: file-manager-db
    environment:
      POSTGRES_USER: user
      POSTGRES_PASSWORD: password
      POSTGRES_DB: filemanager_db
    # H2互換モードを実現するためには、Spring Boot側でPostgreSQLのDialectを使用し、
    # H2の起動オプションでPostgreSQL互換モードを有効にする（これはH2を直接実行する場合）。
    # 本番環境への移行を見据え、DockerではPostgreSQLを使用することを推奨します。
    # 開発環境でH2を使う場合は、アプリケーションと同一コンテナ内でファイルベースで実行するのが一般的です。
    # ここでは、PostgreSQL互換性を確認しながら開発できるよう、本番を想定したPostgreSQLコンテナを配置します。
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  # 2. S3互換ストレージ (MinIO)
  minio:
    image: minio/minio
    container_name: file-manager-minio
    ports:
      - "9000:9000"
      - "9001:9001" # Web UI
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  # 3. Spring Bootアプリケーション
  app:
    build: .
    container_name: file-manager-app
    ports:
      - "8080:8080"
    depends_on:
      - db
      - minio
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/filemanager_db
      SPRING_DATASOURCE_USERNAME: user
      SPRING_DATASOURCE_PASSWORD: password
      # MinIO接続設定
      S3_ENDPOINT_URL: http://minio:9000
      S3_ACCESS_KEY: minioadmin
      S3_SECRET_KEY: minioadmin
      S3_BUCKET_NAME: files

volumes:
  postgres_data:
  minio_data:
```

-----

### 補足 1. ユーザー認証・認可

  * **認証**: ユーザー認証（ログイン/ログアウト）機能が必要です。Spring Securityによるフォーム認証を採用します。
  * **認可**: 認証されたユーザーが、特定のリソース（ファイル/フォルダ）へのアクセス権限を持っているかチェックする必要があります。権限チェックロジックはサービス層に実装し、Linux風パーミッションに基づき判定します。

### 補足 2. URLパスの管理

  * ファイルシステムの階層構造を表現するため、\*\*`parent_folder_id`\*\*フィールドを導入し、フォルダ間の親子関係を管理します。ルートフォルダは`parent_folder_id`がNULLとなります。

### 補足 3. データ量とスケーラビリティ

  * ファイル管理システムの性質上、メタデータ（DB）のレコード数が増加しやすいため、検索フィールド（`name`, `custom_tags`など）には適切な**インデックス**を設定します。

