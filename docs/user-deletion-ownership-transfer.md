# ユーザ削除時のファイル所有権移転機能の実装

## 概要
ユーザが削除された際に、そのユーザが所有していたすべてのファイルとフォルダの所有権を自動的にadminユーザに移転する機能を実装しました。

## 実装内容

### 1. FileRepository の拡張
**ファイル**: `src/main/java/com/example/filemanager/repository/FileRepository.java`

特定のユーザが所有するすべてのファイル・フォルダを取得するメソッドを追加しました。

```java
/**
 * Finds all files owned by a specific user (including soft-deleted ones).
 *
 * @param owner The owner user entity.
 * @return A list of FileEntity objects owned by the specified user.
 */
List<FileEntity> findAllByOwner(com.example.filemanager.domain.User owner);
```

このメソッドは、論理削除されたファイルも含めて、指定されたユーザが所有するすべてのファイル・フォルダを返します。

### 2. UserService の更新
**ファイル**: `src/main/java/com/example/filemanager/service/UserService.java`

#### 2.1 依存関係の追加
`FileRepository` を依存関係として追加し、コンストラクタインジェクションで注入するようにしました。

```java
private final FileRepository fileRepository;

public UserService(UserRepository userRepository, GroupRepository groupRepository,
        PasswordEncoder passwordEncoder, FileRepository fileRepository) {
    this.userRepository = userRepository;
    this.groupRepository = groupRepository;
    this.passwordEncoder = passwordEncoder;
    this.fileRepository = fileRepository;
}
```

#### 2.2 deleteUser メソッドの拡張
ユーザ削除処理に、ファイル所有権の移転ロジックを追加しました。

```java
public void deleteUser(Long id) {
    User user = userRepository.findById(id)
            .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    if ("admin".equals(user.getUsername())) {
        throw new IllegalArgumentException("Cannot delete admin user");
    }
    
    // Transfer ownership of all files/folders to admin user
    User adminUser = userRepository.findByUsername("admin")
            .orElseThrow(() -> new UserNotFoundException("Admin user not found"));
    
    List<FileEntity> ownedFiles = fileRepository.findAllByOwner(user);
    for (FileEntity file : ownedFiles) {
        file.setOwner(adminUser);
        fileRepository.save(file);
    }
    
    userRepository.deleteById(id);
}
```

## 処理フロー

1. 削除対象のユーザを取得
2. adminユーザでないことを確認（adminユーザは削除不可）
3. adminユーザをデータベースから取得
4. 削除対象ユーザが所有するすべてのファイル・フォルダを取得
5. 各ファイル・フォルダの所有者をadminユーザに変更して保存
6. ユーザを削除

## データ整合性の保証

- **トランザクション管理**: `@Transactional` アノテーションにより、ファイル所有権の移転とユーザ削除が同一トランザクション内で実行されます。エラーが発生した場合は自動的にロールバックされます。

- **論理削除されたファイルも含む**: `findAllByOwner` メソッドは論理削除されたファイルも取得するため、ゴミ箱内のファイルも適切に所有権が移転されます。

- **adminユーザの存在確認**: adminユーザが存在しない場合は例外をスローし、処理を中断します。

## 今後の拡張可能性

この実装は、次の改善機能の基盤となります：
- グループ削除時のファイル所有グループの移転
- 管理者による所有者・所有グループの変更機能
- フォルダ配下の一括所有権変更機能
