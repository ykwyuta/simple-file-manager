# API リファレンス

対話的なドキュメントは Swagger UI (`/swagger-ui.html`, 管理者のみ) と
OpenAPI JSON (`/v3/api-docs`) で提供している。本ドキュメントは全体像と
エラーコードの規約をまとめる。

## 1. 認証

`/api/**` は**ステートレスな HTTP Basic 認証**。Authorization ヘッダーを
事前送信すること (401 応答に `WWW-Authenticate` は含まれない。理由は
[security.md](./security.md) 参照)。

```bash
curl -u admin:admin http://localhost:8080/api/files
```

ブラウザ画面が使う JSON エンドポイントは `/web/api/**` にあり、セッションで
認証する。API クライアントは `/api/**` を使うこと。

## 2. エンドポイント

### 2.1 ファイル・フォルダ (`/api/files`)

| メソッド | パス | 説明 | 必要な権限 |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/files?parentId=` | フォルダ内の一覧 (読める項目のみ) | 対象フォルダの読み取り |
| `POST` | `/api/files` | アップロード (multipart: `file`, `parentFolderId`, `permissions`) | 親フォルダの書き込み |
| `GET` | `/api/files/{id}` | ダウンロード | 読み取り |
| `PUT` | `/api/files/{id}` | 内容の更新 (multipart: `file`) | 書き込み |
| `DELETE` | `/api/files/{id}` | 論理削除 (配下も一緒に削除) | 書き込み |
| `PUT` | `/api/files/{id}/name` | リネーム | 書き込み |
| `PUT` | `/api/files/{id}/parent` | 移動 | 移動元と移動先の書き込み |
| `PUT` | `/api/files/{id}/tags` | タグ更新 | 書き込み |
| `PUT` | `/api/files/{id}/owner` | 所有者・グループの変更 | 管理者のみ |
| `PUT` | `/api/files/{id}/lock` | ロック・解除 | 書き込み。解除はロック保持者のみ |
| `POST` | `/api/files/folders` | フォルダ作成 | 親フォルダの書き込み |
| `PUT` | `/api/files/folders/{id}/versioning` | バージョン管理の切り替え | 書き込み |
| `GET` | `/api/files/{id}/versions` | バージョン履歴 | 読み取り |
| `POST` | `/api/files/{id}/restore/{versionId}` | バージョン復元 | 書き込み |
| `GET` | `/api/files/trash` | ゴミ箱 (削除の起点のみ) | 読み取り |
| `POST` | `/api/files/{id}/restore` | ゴミ箱から復元 (配下も一緒に) | 書き込み |
| `GET` | `/api/files/search?name=&tags=` | 検索 | 読み取り |

`versionId` は必ず `{id}` に属するバージョンでなければならない。他ファイルの
バージョン ID を指定すると 404 になる。

### 2.2 ユーザー・グループ (管理者のみ)

| メソッド | パス | 説明 |
| :--- | :--- | :--- |
| `GET` / `POST` | `/api/users` | 一覧 / 作成 |
| `GET` / `PUT` / `DELETE` | `/api/users/{id}` | 取得 / 更新 / 削除 |
| `POST` / `DELETE` | `/api/users/{userId}/groups/{groupId}` | グループへの追加 / 削除 |
| `GET` / `POST` | `/api/groups` | 一覧 / 作成 |
| `GET` / `PUT` / `DELETE` | `/api/groups/{id}` | 取得 / 更新 / 削除 |

レスポンスにパスワードハッシュは含まれない。ユーザー削除時は所有ファイルが
`admin` に、グループ削除時は所有グループが `admins` に移管される。

## 3. エラー応答

失敗はすべて RFC 7807 の `application/problem+json` で返る。

```json
{
  "type": "about:blank",
  "title": "Forbidden",
  "status": 403,
  "detail": "このファイルにアクセスする権限がありません。",
  "instance": "/api/files/42"
}
```

| ステータス | 意味 | 例 |
| :--- | :--- | :--- |
| `400` | 入力が不正 | パーミッションが `999`、名前にパス区切り、名前が 255 文字超 |
| `401` | 未認証 | Authorization ヘッダーなし |
| `403` | 権限なし | 他人の非公開ファイル、非管理者によるユーザー管理 |
| `404` | 対象なし | 存在しない ID、他ファイルのバージョン ID |
| `409` | 重複 | 同一フォルダ内の同名、ユーザー名・グループ名の重複 |
| `413` | サイズ超過 | アップロード上限超え |
| `423` | ロック中 | 他ユーザーがロックしたファイルの変更 |
| `500` | サーバー内部エラー | 定型メッセージのみを返し、詳細はサーバーログに記録 |

`500` の応答本文に例外メッセージやスタックトレースは含まれない。

## 4. 制限値

| 項目 | 既定値 | 設定 |
| :--- | :--- | :--- |
| 1 ファイルの最大サイズ | 100MB | `MAX_FILE_SIZE` |
| 1 リクエストの最大サイズ | 120MB | `MAX_REQUEST_SIZE` |
| 名前の最大長 | 255 文字 | — |
| ユーザー名・グループ名の最大長 | 64 文字 | — |
