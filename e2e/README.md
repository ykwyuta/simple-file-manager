# エンドツーエンドテスト

実際に起動したアプリケーションに対して Playwright で検証する。ブラウザ操作と
API 呼び出しの両方を含む。

## 実行方法

```bash
# 1. アプリケーションをビルドする
mvn -DskipTests package

# 2. テストを実行する (アプリケーションは Playwright が自動起動する)
cd e2e
npm ci
npx playwright install --with-deps chromium
npm test
```

サーバーは `h2` プロファイル (インメモリ DB + ファイルシステム保存) で起動する
ため、PostgreSQL も S3 互換ストレージも不要。

| 環境変数 | 用途 |
| :--- | :--- |
| `E2E_PORT` | 起動ポート (既定 8080) |
| `E2E_BASE_URL` | 接続先の上書き |
| `E2E_NO_SERVER` | 自動起動せず、既に動いているサーバーに接続する |
| `CHROMIUM_PATH` | インストール済み Chromium を使う |

レポート: `npx playwright show-report`

## 構成

| ファイル | 対象 |
| :--- | :--- |
| `01-authentication.spec.js` | フォームログイン、ログアウト、API の Basic 認証と 401 応答 |
| `02-privilege-escalation.spec.js` | ユーザー・グループ管理 API の権限、管理画面へのアクセス |
| `03-file-authorization.spec.js` | パーミッション判定、バージョン復元の帰属チェック、管理者特権 |
| `04-csrf-and-xss.spec.js` | クロスサイト POST の遮断、ユーザー名・ファイル名のエスケープ |
| `05-delete-and-restore.spec.js` | 論理削除と復元のカスケード、ゴミ箱の表示単位 |
| `06-error-handling.spec.js` | ステータスコードの妥当性、内部情報の非露出、アップロード上限 |
| `07-usability.spec.js` | ページング件数の整合、モバイル対応、アクセシビリティ、並び順 |
| `08-file-operations.spec.js` | アップロード・移動・タグ・バージョン・ロックの回帰 |

各 spec の冒頭コメントに、そのテストが守っている問題を記載している。

## 注意

- テストは 1 つのサーバーを共有するため直列実行 (`workers: 1`)。
- 名前は `unique()` ヘルパーで一意にし、spec 間で衝突しないようにしている。
- 件数を断定するテストは、ルート直下ではなく専用フォルダを作ってその中で行う。
