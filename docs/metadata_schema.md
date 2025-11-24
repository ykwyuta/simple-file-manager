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
| `permissions` | `INTEGER` | Linux風パーミッション (例: 755) | `NOT NULL`, 3桁の10進数表記 |
| `storage_key` | `VARCHAR(1024)`| S3互換ストレージ内での一意なキー | ファイルの場合 `NOT NULL` |
| `custom_tags` | `TEXT` | ユーザー定義の検索用タグ | カンマ区切りのテキスト形式 |
| `description` | `TEXT` | ファイル/フォルダの説明 | |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | 作成日時 | `NOT NULL`, デフォルトで現在時刻 |
| `updated_at` | `TIMESTAMP WITH TIME ZONE` | 最終更新日時 | `NOT NULL`, デフォルトで現在時刻 |
| `deleted_at` | `TIMESTAMP WITH TIME ZONE` | 論理削除日時 | `NULL`許容 |
| `versioning_enabled` | `BOOLEAN` | バージョン管理が有効か | フォルダの場合のみ使用 |
| `is_locked` | `BOOLEAN` | ファイルがロックされているか | デフォルト `false` |
| `locked_by_user_id` | `BIGINT` | ロックしたユーザーのID | `FOREIGN KEY (users.id)`, ロック時のみ設定 |
| `locked_at` | `TIMESTAMP WITH TIME ZONE` | ロック日時 | ロック時のみ設定 |

## 3. `file_history` テーブル定義

バージョン管理が有効なフォルダ内のファイルの履歴を保存するテーブル。

| カラム名 | データ型 | 説明 | 制約 / 備考 |
| :--- | :--- | :--- | :--- |
| `id` | `BIGINT` | 一意な識別子 | `PRIMARY KEY`, `AUTO_INCREMENT` |
| `file_entity_id` | `BIGINT` | 対象ファイルのID | `FOREIGN KEY (files.id)`, `NOT NULL` |
| `version` | `INTEGER` | バージョン番号 | `NOT NULL` |
| `storage_key` | `VARCHAR(1024)` | このバージョンのS3ストレージキー | `NOT NULL` |
| `modifier_user_id` | `BIGINT` | 更新したユーザーのID | `FOREIGN KEY (users.id)`, `NOT NULL` |
| `created_at` | `TIMESTAMP WITH TIME ZONE` | バージョン作成日時 | `NOT NULL`, デフォルトで現在時刻 |

## 4. インデックス戦略

検索パフォーマンスとデータ整合性を確保するため、以下のインデックスを設定する。

| インデックス対象カラム | インデックス種類 | 目的 |
| :--- | :--- | :--- |
| `(parent_folder_id, name)` | `UNIQUE` | 同一フォルダ内での名前の重複を防止 |
| `owner_user_id` | `INDEX` | 特定ユーザーの所有アイテム検索を高速化 |
| `owner_group_id` | `INDEX` | 特定グループの所属アイテム検索を高速化 |
| `name` | `INDEX` (or Full-Text) | ファイル名での検索を高速化 |
| `custom_tags` | `GIN` (PostgreSQL) | JSONB形式のタグ検索を高速化 |
| `deleted_at` | `INDEX` | 論理削除されたアイテム（ゴミ箱機能など）や、物理削除対象の検索を高速化 |

## 5. 関連テーブル

-   **users**: ユーザー情報を格納。[ユーザー・グループ管理設計書](./user_group_management.md)参照。
-   **groups**: グループ情報を格納。[ユーザー・グループ管理設計書](./user_group_management.md)参照。
-   **user_groups**: ユーザーとグループの多対多関係を格納する中間テーブル。

## 6. 考慮事項

-   **データ型**: `custom_tags` には`TEXT`型を使用し、カンマ区切りの文字列として保存します。アプリケーション側でパースして使用します。
-   **一貫性**: ファイルのメタデータは、ファイルアップロード時にS3互換ストレージから取得した値を設定し、実体との一貫性を保つ。
-   **拡張性**: バージョン管理機能は`file_history`テーブルで実装済みです。ファイルロック機能も`files`テーブルに実装済みです。
