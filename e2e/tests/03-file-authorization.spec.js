// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, ensureUser, unique, updateFile, uploadFile } = require('../helpers');

/**
 * SEC-02: restoreFileVersion resolved the version id on its own, without
 * checking it belonged to the file being restored. A user with write access to
 * any file could graft another user's stored object onto it and read the
 * contents.
 *
 * SEC-09: administrators had no read override, so they could not see or manage
 * files owned by anyone else.
 */
test.describe('ファイルの認可', () => {
  const alice = { username: unique('alice'), password: 'alice-12345' };
  const bob = { username: unique('bob'), password: 'bob-1234567' };

  /** @type {import('@playwright/test').APIRequestContext} */ let adminApi;
  /** @type {import('@playwright/test').APIRequestContext} */ let aliceApi;
  /** @type {import('@playwright/test').APIRequestContext} */ let bobApi;

  let aliceSecretFile;
  let aliceVersionId;

  test.beforeAll(async ({ playwright, baseURL }) => {
    adminApi = await apiAs(playwright, baseURL, ADMIN);
    await ensureUser(adminApi, { ...alice, groups: ['alice-team'] });
    await ensureUser(adminApi, { ...bob, groups: ['bob-team'] });
    aliceApi = await apiAs(playwright, baseURL, alice);
    bobApi = await apiAs(playwright, baseURL, bob);

    // Alice keeps a private, versioned file that nobody else may read.
    const vault = await createFolder(aliceApi, unique('alice-vault'), { permissions: '700' });
    await aliceApi.put(`/api/files/folders/${vault.id}/versioning`, { data: { enabled: true } });
    aliceSecretFile = await uploadFile(aliceApi, 'secret.txt', 'ALICE-SECRET-V1', {
      parentFolderId: vault.id,
      permissions: '600',
    });
    await updateFile(aliceApi, aliceSecretFile.id, 'secret.txt', 'ALICE-SECRET-V2');
    const versions = await (await aliceApi.get(`/api/files/${aliceSecretFile.id}/versions`)).json();
    aliceVersionId = versions[0].id;
    expect(aliceVersionId).toBeTruthy();
  });

  test.afterAll(async () => {
    await adminApi?.dispose();
    await aliceApi?.dispose();
    await bobApi?.dispose();
  });

  test('他人の非公開ファイルは読めない', async () => {
    const response = await bobApi.get(`/api/files/${aliceSecretFile.id}`);
    expect(response.status()).toBe(403);
    expect(await response.text()).not.toContain('ALICE-SECRET');
  });

  test('他人の非公開ファイルは一覧に現れない', async () => {
    const files = await (await bobApi.get('/api/files')).json();
    expect(files.map((f) => f.name)).not.toContain('secret.txt');
  });

  test('他ファイルのバージョンIDを指定した復元は拒否される', async () => {
    // Bob owns this file outright, so the only thing standing between him and
    // Alice's data is the version-to-file binding.
    const bobFolder = await createFolder(bobApi, unique('bob-space'), { permissions: '700' });
    await bobApi.put(`/api/files/folders/${bobFolder.id}/versioning`, { data: { enabled: true } });
    const bobFile = await uploadFile(bobApi, 'mine.txt', 'BOB-OWN-CONTENT', {
      parentFolderId: bobFolder.id,
      permissions: '600',
    });

    const restore = await bobApi.post(`/api/files/${bobFile.id}/restore/${aliceVersionId}`);
    expect(restore.status()).toBe(404);

    const contents = await (await bobApi.get(`/api/files/${bobFile.id}`)).text();
    expect(contents).toBe('BOB-OWN-CONTENT');
    expect(contents).not.toContain('ALICE-SECRET');
  });

  test('自分のファイルのバージョン復元は成功する', async () => {
    const folder = await createFolder(aliceApi, unique('alice-versioned'), { permissions: '700' });
    await aliceApi.put(`/api/files/folders/${folder.id}/versioning`, { data: { enabled: true } });
    const file = await uploadFile(aliceApi, 'doc.txt', 'VERSION-ONE', {
      parentFolderId: folder.id,
      permissions: '600',
    });
    await updateFile(aliceApi, file.id, 'doc.txt', 'VERSION-TWO');

    const versions = await (await aliceApi.get(`/api/files/${file.id}/versions`)).json();
    const restore = await aliceApi.post(`/api/files/${file.id}/restore/${versions[0].id}`);
    expect(restore.status()).toBe(200);
    expect(await (await aliceApi.get(`/api/files/${file.id}`)).text()).toBe('VERSION-ONE');
  });

  test('書き込み権限のないファイルは変更・削除できない', async () => {
    expect((await bobApi.put(`/api/files/${aliceSecretFile.id}/name`, {
      data: { newName: 'pwned.txt' },
    })).status()).toBe(403);
    expect((await bobApi.delete(`/api/files/${aliceSecretFile.id}`)).status()).toBe(403);
  });

  test('書き込み権限のないフォルダにはアップロードできない', async () => {
    const readOnly = await createFolder(aliceApi, unique('read-only'), { permissions: '755' });
    const response = await bobApi.post('/api/files', {
      multipart: {
        file: { name: 'intruder.txt', mimeType: 'text/plain', buffer: Buffer.from('nope') },
        parentFolderId: String(readOnly.id),
        permissions: '644',
      },
    });
    expect(response.status()).toBe(403);
  });

  test('管理者は他人のファイルも参照・管理できる', async () => {
    // SEC-09: without an admin override the administrator could not see this at all.
    const response = await adminApi.get(`/api/files/${aliceSecretFile.id}`);
    expect(response.status()).toBe(200);
    expect(await response.text()).toBe('ALICE-SECRET-V2');
  });

  test('グループ権限で共有されたファイルは同じグループから読める', async ({ playwright, baseURL }) => {
    const teammate = { username: unique('teammate'), password: 'mate-123456' };
    await ensureUser(adminApi, { ...teammate, groups: ['alice-team'] });
    const mateApi = await apiAs(playwright, baseURL, teammate);

    const shared = await createFolder(aliceApi, unique('team-shared'), { permissions: '750' });
    const file = await uploadFile(aliceApi, 'team.txt', 'TEAM-CONTENT', {
      parentFolderId: shared.id,
      permissions: '640',
    });

    expect(await (await mateApi.get(`/api/files/${file.id}`)).text()).toBe('TEAM-CONTENT');
    // Read, but not write: the group digit is 4.
    expect((await mateApi.delete(`/api/files/${file.id}`)).status()).toBe(403);
    await mateApi.dispose();
  });
});
