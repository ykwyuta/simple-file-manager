# ドキュメント一覧

このディレクトリには、ファイル管理システムの設計書と実装ドキュメントが含まれています。

## 設計書

### データベース関連

- **[metadata_schema.md](./metadata_schema.md)** - メタデータスキーマ
  - `files` テーブル定義
  - `file_history` テーブル定義（バージョン管理用）
  - インデックス戦略
  - 関連テーブル

- **[H2_DATABASE.md](./H2_DATABASE.md)** - H2データベース設定
  - 開発環境でのH2データベース使用方法
  - PostgreSQLプロファイルへの切り替え方法
  - H2コンソールへのアクセス方法

### 機能設計

- **[user_group_management.md](./user_group_management.md)** - ユーザー・グループ管理
  - ユーザーとグループのデータモデル
  - CRUD操作の仕様
  - ユーザー削除時の所有権移転（実装済み）
  - グループ削除時の所有グループ移転（実装済み）

- **[permission_management.md](./permission_management.md)** - 権限管理
  - Linux風パーミッションモデル
  - 権限チェックロジック
  - 操作と要求権限のマッピング

- **[file_folder_operations.md](./file_folder_operations.md)** - ファイル・フォルダ操作
  - アップロード、ダウンロード、リネーム、移動、削除の仕様
  - 画面フローとAPIエンドポイント

- **[folder_hierarchy.md](./folder_hierarchy.md)** - フォルダ階層管理
  - 階層構造のデータモデル
  - フォルダ作成、階層表示、ナビゲーション機能

- **[delete_process.md](./delete_process.md)** - 削除処理
  - 論理削除（Soft Delete）の仕様
  - 物理削除（Hard Delete）のバッチ処理
  - ゴミ箱機能

- **[search_feature.md](./search_feature.md)** - ファイル検索機能
  - キーワード検索
  - タグによる絞り込み
  - 検索パフォーマンスの考慮事項

- **[s3_integration.md](./s3_integration.md)** - S3互換ストレージ連携
  - Garage（S3互換ストレージ）との連携仕様
  - ファイルアップロード、ダウンロード、削除の実装
  - エラーハンドリングとトランザクション管理

## 実装ドキュメント

- **[user-deletion-ownership-transfer.md](./user-deletion-ownership-transfer.md)** - ユーザー削除時の所有権移転機能
  - ユーザー削除時にファイル所有権を admin ユーザーに自動移転
  - FileRepository、UserService の実装詳細
  - 処理フローとデータ整合性の保証

- **[owner-change-feature.md](./owner-change-feature.md)** - 所有者・所有グループ変更機能
  - 管理者による所有者・所有グループの変更機能
  - 再帰的変更オプション（フォルダ配下の一括変更）
  - ChangeOwnerRequest DTO、FileService、FileController の実装詳細

## ドキュメントの読み方

1. **新規参加者向け**: まず [README.md](../README.md) を読んで全体像を把握してから、各設計書を参照してください。
2. **機能実装者向け**: 実装する機能に関連する設計書と実装ドキュメントを参照してください。
3. **データベース設計者向け**: [metadata_schema.md](./metadata_schema.md) と [H2_DATABASE.md](./H2_DATABASE.md) を参照してください。

## 更新履歴

- 2025-11-24: ドキュメントを `doc/` から `docs/` に移動
- 2025-11-24: 実装に合わせてドキュメントを更新
  - パーミッションの表記を8進数から10進数に変更
  - ユーザーエンティティから is_admin フィールドを削除（グループベースの管理者判定に変更）
  - バージョン管理とファイルロック機能のフィールドを追加
  - 所有権移転機能のドキュメントを追加
