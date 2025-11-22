# 設計書: メタデータスキーマ

## 1. 概要

本システムで管理するファイルおよびフォルダのメタデータに関するデータベーススキーマの仕様を定義する。
このスキーマは、ファイルシステムの階層構造、権限管理、検索機能の基盤となる。

## 2. `files` テーブル定義案

ファイルとフォルダの両方を格納する単一のテーブルとして設計する。`is_directory` フィールドで両者を区別する。

| カラム名 | データ型 | 説明 | 制約 / 備考 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | 一意な識別子 | `PRIMARY KEY`, `AUTO_INCREMENT` |
| `name` | `VARCHAR(255)` | ファイル/フォルダ名 | `NOT NULL` |
| `is_directory` | `BOOLEAN` | フォルダであるか否か (`true`=フォルダ) | `NOT NULL` |
| `parent_folder_id`| `BIGINT` | 親フォルダのID | `FOREIGN KEY (files.id)`, ルートフォルダは`NULL` |
| `owner_user_id` | `BIGINT` | 所有ユーザーのID | `FOREIGN KEY (users.id)`, `NOT NULL` |
| `owner_group_id`| `BIGINT` | 所有グループのID | `FOREIGN KEY (groups.id)`, `NOT NULL` |
| `permissions` | `VARCHAR(3)` | 8進数表現のLinux風パーミッション (例: '750') | `NOT NULL` |
| `storage_key` | `VARCHAR(1024)`| S3互換ストレージ内での一意なキー | ファイルの場合 `NOT NULL` |
| `file_size` | `BIGINT` | ファイルサイズ（バイト単位） | ファイルの場合のみ |
| `mime_type` | `VARCHAR(255)` | MIMEタイプ (例: 'image/jpeg') | ファイルの場合のみ |
| `custom_tags` | `JSONB` / `TEXT` | ユーザー定義の検索用タグ | PostgreSQLの`JSONB`を推奨 |
| `description` | `TEXT` | ファイル/フォルダの説明 | |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 作成日時 | `NOT NULL`, デフォルトで現在時刻 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 最終更新日時 | `NOT NULL`, デフォルトで現在時刻 |
| `deleted_at` | `TIMESTAMP WITH TIME ZONE` | 論理削除日時 | `NULL`許容 |

## 3. インデックス戦略

検索パフォーマンスとデータ整合性を確保するため、以下のインデックスを設定する。

| インデックス対象カラム | インデックス種類 | 目的 |
| :--- | :--- | :--- |
| `(parent_folder_id, name)` | `UNIQUE` | 同一フォルダ内での名前の重複を防止 |
| `owner_user_id` | `INDEX` | 特定ユーザーの所有アイテム検索を高速化 |
| `owner_group_id` | `INDEX` | 特定グループの所属アイテム検索を高速化 |
| `name` | `INDEX` (or Full-Text) | ファイル名での検索を高速化 |
| `custom_tags` | `GIN` (PostgreSQL) | JSONB形式のタグ検索を高速化 |
| `deleted_at` | `INDEX` | 論理削除されたアイテム（ゴミ箱機能など）や、物理削除対象の検索を高速化 |

## 4. 関連テーブル

-   **users**: ユーザー情報を格納。[ユーザー・グループ管理設計書](./user_group_management.md)参照。
-   **groups**: グループ情報を格納。[ユーザー・グループ管理設計書](./user_group_management.md)参照。
-   **user_groups**: ユーザーとグループの多対多関係を格納する中間テーブル。

## 5. 考慮事項

-   **データ型**: `custom_tags` にはPostgreSQLの`JSONB`型を使用することで、タグの追加・削除や特定のタグを含むアイテムの検索が効率的に行える。汎用性を考えるなら`TEXT`型も選択肢となるが、アプリケーション側での処理が複雑になる。
-   **一貫性**: `file_size` や `mime_type` などの情報は、ファイルアップロード時にS3互換ストレージから取得した値を設定し、実体との一貫性を保つ。
-   **拡張性**: 将来的にバージョン管理機能などを追加する場合、このテーブルを拡張するか、別テーブルでバージョン情報を管理するかを検討する必要がある。
