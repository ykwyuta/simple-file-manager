# 設計書: S3互換ストレージ連携

## 1. 概要

ファイル本体の永続化先として、S3互換オブジェクトストレージ（開発環境ではGarage）を利用するための連携仕様を定義する。
アプリケーションはファイルの実データを直接管理せず、メタデータのみをDBに保持し、ファイルの保存と取得はS3 APIを介して行う。

## 2. 利用ライブラリ

-   **`io.awspring.cloud:spring-cloud-aws-starter-s3`**
    -   Spring BootアプリケーションからS3互換ストレージへ容易にアクセスするためのライブラリ。
    -   `S3Template` や `S3Client` などのBeanを提供し、ファイル操作を抽象化する。

## 3. 設定項目

アプリケーションがS3互換ストレージに接続するために必要な設定値を `application.properties` または環境変数で管理する。

| プロパティキー | 環境変数例 | 説明 |
| :--- | :--- | :--- |
| `spring.cloud.aws.s3.endpoint` | `S3_ENDPOINT_URL` | S3互換ストレージのエンドポイントURL (例: `http://garage:3900`) |
| `spring.cloud.aws.s3.credentials.access-key` | `S3_ACCESS_KEY` | アクセスキーID |
| `spring.cloud.aws.s3.credentials.secret-key` | `S3_SECRET_KEY` | シークレットアクセスキー |
| `spring.cloud.aws.region.static` | `AWS_REGION` | AWSリージョン (S3互換ストレージでは不要な場合もあるが、ライブラリの仕様上設定が必要な場合がある。例: `us-east-1`) |
| `application.s3.bucket-name` | `S3_BUCKET_NAME` | ファイルを保存するバケット名 (例: `files`) |

## 4. ファイル操作の実装

### 4.1. ファイルアップロード

1.  ユーザーからのアップロードリクエスト（`MultipartFile`）を受け取る。
2.  **一意なストレージキー (`storage_key`) を生成する。**
    -   方法: `UUID.randomUUID().toString()` + `_` + `元のファイル名`
    -   これにより、異なるユーザーが同じファイル名でアップロードしてもキーが衝突しない。
3.  `S3Template.upload(bucketName, storageKey, multipartFile.getInputStream(), objectMetadata)` を呼び出す。
    -   `ObjectMetadata` には `Content-Type` (MIMEタイプ) や `Content-Length` (ファイルサイズ) を設定する。
4.  アップロードが成功したら、生成した `storageKey` とその他メタデータ（ファイル名、サイズ、MIMEタイプなど）をDBに保存する。

### 4.2. ファイルダウンロード

1.  ユーザーからのダウンロードリクエストを受け、DBから対象ファイルのメタデータ（特に `storage_key`）を取得する。
2.  `S3Template.download(bucketName, storageKey)` を呼び出して、S3から`S3Object`を取得する。
3.  取得したオブジェクトの入力ストリーム (`S3ObjectInputStream`) を、`HttpServletResponse` の出力ストリームに書き込む。
4.  レスポンスヘッダーには、ダウンロード時のファイル名を示す `Content-Disposition` や、`Content-Type`, `Content-Length` を適切に設定する。

### 4.3. ファイル削除 (物理削除)

1.  [削除処理設計書](./delete_process.md)に基づき、物理削除対象のファイルの `storage_key` をDBから取得する。
2.  `S3Template.deleteObject(bucketName, storageKey)` を呼び出して、S3上のオブジェクトを削除する。
3.  削除が成功したら、DBのメタデータレコードを削除する。

## 5. エラーハンドリングと堅牢性

-   **接続エラー**: S3エンドポイントへの接続に失敗した場合、リトライ処理を実装するか、サービスが一時的に利用不可であることを示すエラーを返す。
-   **認証エラー**: アクセスキーが無効な場合、設定の誤りとして致命的なエラーログを記録し、サービスを正常に起動させない（Fail-fast）。
-   **アップロード/ダウンロード中のエラー**: ストリームの読み書き中にエラーが発生した場合、中途半端なファイルが残らないように処理を中断し、適切に例外をハンドリングする。
-   **トランザクション管理**:
    -   ファイルのアップロード処理において、「S3への保存」と「DBへのメタデータ保存」は一連の処理と見なされるべきである。
    -   厳密なトランザクション管理は困難なため（分散トランザクションとなる）、以下のような補償トランザクションのロジックを実装する。
        -   **ケースA**: S3保存成功後、DB保存に失敗した場合 -> 保存したS3オブジェクトを削除する処理を試みる。
        -   **ケースB**: DB保存成功後、S3保存に失敗した場合 (通常は順序が逆) -> DBレコードを削除する。
    -   または、S3への保存が完了してからDBに書き込む順序を徹底し、失敗した場合はユーザーにリトライを促すのが現実的な実装となる。
