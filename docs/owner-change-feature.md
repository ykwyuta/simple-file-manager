# 所有者・所有グループ変更機能

## 概要

管理者（`admins`グループのメンバー）がファイルやフォルダの所有者および所有グループを任意のユーザー・グループに変更できる機能です。フォルダの場合、配下のすべてのファイル・フォルダに対して再帰的に所有権を変更するオプションも提供します。

## 実装内容

### 1. ChangeOwnerRequest DTO

**ファイル**: `src/main/java/com/example/filemanager/controller/dto/ChangeOwnerRequest.java`

所有者変更リクエストのデータ転送オブジェクト。

```java
public class ChangeOwnerRequest {
    @NotNull
    private Long ownerUserId;
    
    @NotNull
    private Long ownerGroupId;
    
    private boolean recursive;
    
    // getters and setters
}
```

### 2. FileService の拡張

**ファイル**: `src/main/java/com/example/filemanager/service/FileService.java`

#### 2.1 changeOwner メソッド

管理者権限のチェックを行い、ファイル/フォルダの所有者と所有グループを変更します。

```java
@Transactional
public FileEntity changeOwner(@NonNull Long fileId, @NonNull Long newOwnerId, 
    @NonNull Long newGroupId, boolean recursive) {
    // 現在のユーザーが admins グループに所属しているかチェック
    User currentUser = (User) SecurityContextHolder.getContext()
        .getAuthentication().getPrincipal();
    boolean isAdmin = currentUser.getGroups().stream()
        .anyMatch(g -> "admins".equals(g.getName()));
    
    if (!isAdmin) {
        throw new AccessDeniedException("Only admins can change file ownership.");
    }
    
    // 所有者と所有グループを変更
    FileEntity fileEntity = fileRepository.findByIdAndDeletedAtIsNull(fileId)
        .orElseThrow(() -> new ResourceNotFoundException("File not found"));
    
    User newOwner = userRepository.findById(newOwnerId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    Group newGroup = groupRepository.findById(newGroupId)
        .orElseThrow(() -> new ResourceNotFoundException("Group not found"));
    
    fileEntity.setOwner(newOwner);
    fileEntity.setGroup(newGroup);
    FileEntity savedFile = fileRepository.save(fileEntity);
    
    // recursive フラグが true で、対象がフォルダの場合は配下も変更
    if (recursive && fileEntity.isDirectory()) {
        changeOwnerRecursive(fileEntity, newOwner, newGroup);
    }
    
    return savedFile;
}
```

#### 2.2 changeOwnerRecursive メソッド

フォルダ配下のすべてのファイル・フォルダに対して再帰的に所有権を変更します。

```java
private void changeOwnerRecursive(FileEntity parent, User newOwner, Group newGroup) {
    List<FileEntity> children = fileRepository.findAllByParentAndDeletedAtIsNull(parent);
    for (FileEntity child : children) {
        child.setOwner(newOwner);
        child.setGroup(newGroup);
        fileRepository.save(child);
        if (child.isDirectory()) {
            changeOwnerRecursive(child, newOwner, newGroup);
        }
    }
}
```

### 3. FileController の更新

**ファイル**: `src/main/java/com/example/filemanager/controller/FileController.java`

REST API エンドポイントを追加しました。

```java
@PutMapping("/{id}/owner")
public ResponseEntity<FileEntity> changeOwner(
        @PathVariable Long id,
        @Valid @RequestBody ChangeOwnerRequest request) {
    FileEntity updatedFile = fileService.changeOwner(
        Objects.requireNonNull(id),
        Objects.requireNonNull(request.getOwnerUserId()),
        Objects.requireNonNull(request.getOwnerGroupId()),
        request.isRecursive()
    );
    return ResponseEntity.ok(updatedFile);
}
```

### 4. WebController の更新

**ファイル**: `src/main/java/com/example/filemanager/controller/WebController.java`

Web UI 用のエンドポイントを追加しました。

- `GET /web/api/users`: ユーザー一覧取得
- `GET /web/api/groups`: グループ一覧取得
- `PUT /web/files/{id}/owner`: 所有者変更

### 5. home.html の更新

**ファイル**: `src/main/resources/templates/home.html`

ファイル一覧画面に「Change Owner/Group」モーダルを追加し、管理者ユーザーが所有者と所有グループを変更できるようにしました。

- ユーザー選択ドロップダウン
- グループ選択ドロップダウン
- 再帰的変更オプション（フォルダの場合のみ表示）

## 処理フロー

1. 管理者ユーザーがファイル/フォルダの「Change Owner/Group」ボタンをクリック
2. モーダルが開き、現在の所有者と所有グループが表示される
3. 新しい所有者と所有グループを選択
4. フォルダの場合、「Apply to all contents」チェックボックスで再帰的変更を選択可能
5. 「Change」ボタンをクリックして変更を実行
6. サーバー側で管理者権限をチェック
7. 所有者と所有グループを変更
8. recursive フラグが true の場合、配下のすべてのファイル・フォルダも変更
9. 成功メッセージを表示してファイル一覧を更新

## セキュリティ

- **管理者権限チェック**: `admins` グループに所属しているユーザーのみが所有権を変更できます
- **トランザクション管理**: `@Transactional` アノテーションにより、変更が原子的に実行されます
- **入力検証**: `@Valid` と `@NotNull` アノテーションによりリクエストデータを検証します

## 関連機能

この機能は、以下の機能と連携しています：

- [ユーザー削除時の所有権移転](./user-deletion-ownership-transfer.md): ユーザー削除時に自動的に admin ユーザーに所有権を移転
- グループ削除時の所有グループ移転: グループ削除時に自動的に admins グループに所有グループを移転
