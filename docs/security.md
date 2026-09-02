# セキュリティ設計

本システムの認証・認可モデルと、本番運用前に必ず行う設定をまとめる。

## 1. 認証

| 対象 | 方式 | 実装 |
| :--- | :--- | :--- |
| ブラウザ (Thymeleaf 画面) | セッション + フォームログイン | `SecurityConfig#webFilterChain` |
| API (`/api/**`) | HTTP Basic (ステートレス) | `SecurityConfig#apiFilterChain` |

フィルターチェーンを 2 本に分けているのは、CSRF 対策と API の使いやすさを両立させるため。

- **画面側**はセッション Cookie (`HttpOnly` / `SameSite=Strict`) を使い、CSRF トークンを必須とする。
- **API 側**はステートレスで、セッション Cookie を発行しない。さらに 401 応答に
  `WWW-Authenticate: Basic` を**付けない** (`ApiAuthenticationEntryPoint`)。
  これによりブラウザが資格情報をキャッシュせず、第三者サイトからのリクエストに
  自動付与することもないため、この系統では CSRF が成立しない。
  `curl -u` や HTTP クライアントのように Authorization ヘッダーを事前送信する
  クライアントは影響を受けない。

ブラウザの画面が使う JSON エンドポイントは `/web/api/**` に置く。`/api/**` に置くと
セッションではなく Basic 認証の対象になってしまうため。

パスワードは BCrypt でハッシュ化して保存する (`SecurityConfig#passwordEncoder`)。

## 2. 認可

### 2.1 ファイル・フォルダ

Linux 風の 3 桁パーミッション (例 `750`) を**10 進数の整数**として保存する。
`755` は「10 進の 755」であり 8 進表記ではない。

適用順は所有者 → グループ → その他で、**最初に一致した 1 つだけ**が適用される。
所有者は、グループやその他により広い権限があっても、所有者桁で拒否される。

判定は 2 か所で行い、両者は同じ規則を実装している。

| 用途 | 実装 |
| :--- | :--- |
| 単一エンティティの判定 | `PermissionService` |
| 一覧・検索の絞り込み (SQL) | `FileSpecification#isAllowedFor` |

一覧と検索を SQL 側で絞るのは、ページングの件数を正しくするため。Java 側で
後から絞ると総件数が絞り込み前の値になり、「全 29 件」と表示しながら 2 件しか
出ない状態になる。

### 2.2 管理者

`admins` グループの所属者はパーミッション判定をバイパスする
(`PermissionService#isAdmin`)。管理者特権の定義はこの 1 か所だけに置く。

### 2.3 URL レベルの認可

| パス | 必要な権限 |
| :--- | :--- |
| `/login`, `/css/**`, `/favicon.*` | 不要 |
| `/admin/**` | `ROLE_ADMINS` |
| `/api/users/**`, `/api/groups/**` | `ROLE_ADMINS` |
| `/h2-console/**`, `/swagger-ui/**`, `/v3/api-docs/**` | `ROLE_ADMINS` |
| その他 | 認証済み |

ユーザー・グループ管理 API は必ず管理者限定にすること。ここが開いていると、
一般ユーザーが `POST /api/users/{id}/groups/{adminsGroupId}` を 1 回叩くだけで
自分を管理者に昇格できる。

## 3. 入力の検証

| 対象 | 規則 | 実装 |
| :--- | :--- | :--- |
| ファイル名・フォルダ名 | 255 文字以内。`/` `\` `.` `..` と制御文字は不可 | `FileService#validateName` |
| ユーザー名 | 64 文字以内、`[A-Za-z0-9._@-]` のみ | `User` / `UserRequest` |
| グループ名 | 64 文字以内、`[A-Za-z0-9._-]` のみ | `Group` / `GroupRequest` |
| パーミッション | 各桁 0〜7 の 3 桁 | `FileService#parsePermissions` |

ユーザー名を制限しているのは、表示のたびにエスケープ漏れを心配しなくて済む
ようにするため。画面側でも `textContent` を使い HTML として解釈させない。

## 4. 保存とダウンロード

- ストレージキーは UUID のみ。利用者が付けた名前はキーに含めない
  (表示名は DB が持つ)。プラットフォームのファイル名エンコーディングに依存せず、
  キーの位置に信頼できない入力が入り込む余地もなくなる。
- ダウンロードは常に `Content-Disposition: attachment` と
  `X-Content-Type-Options: nosniff` を付ける。アップロードされた HTML を
  同一オリジンでインライン表示すると、それ自体が格納型 XSS になる。

## 5. 秘密情報

リポジトリに秘密情報を入れない。`.env.example` を `.env` にコピーして設定する
(`.env` は `.gitignore` 済み)。

| 変数 | 用途 |
| :--- | :--- |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | データベース |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | オブジェクトストレージ |
| `GARAGE_RPC_SECRET` / `GARAGE_ADMIN_TOKEN` / `GARAGE_METRICS_TOKEN` | Garage |
| `APP_BOOTSTRAP_ADMIN_PASSWORD` | 初期管理者のパスワード |

## 6. 本番投入前チェックリスト

- [ ] `APP_BOOTSTRAP_ADMIN_PASSWORD` を設定する (既定の `admin` のままにしない)
- [ ] `app.bootstrap.demo-user=false` (既定値) のままにする
- [ ] `src/main/resources/user.csv` を配置しない (平文パスワードを含むため)
- [ ] `spring.h2.console.enabled=false` (既定値) を確認する
- [ ] `COOKIE_SECURE=true` を設定する (TLS 終端がある場合)
- [ ] `.env` の値をすべて実際の値に置き換える
- [ ] `spring.jpa.hibernate.ddl-auto` を `validate` にし、マイグレーションで
      スキーマを管理する (docs/operations.md 参照)
- [ ] 過去にリポジトリへコミットされた S3 キーと Garage トークンを失効させる

## 7. 回帰テスト

本ドキュメントに書かれた性質は `e2e/tests/` で実際に検証している。

| 観点 | テスト |
| :--- | :--- |
| 認証・ログアウト・Basic チャレンジ非送出 | `01-authentication.spec.js` |
| 権限昇格の防止 | `02-privilege-escalation.spec.js` |
| ファイル認可・バージョン復元の帰属 | `03-file-authorization.spec.js` |
| CSRF・XSS | `04-csrf-and-xss.spec.js` |
| 削除と復元のカスケード | `05-delete-and-restore.spec.js` |
| エラー時のステータスコードと情報漏えい | `06-error-handling.spec.js` |
