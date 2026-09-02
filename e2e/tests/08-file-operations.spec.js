// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, ensureUser, login, unique, updateFile, uploadFile } = require('../helpers');

/**
 * Regression coverage for the features the fixes touch, so a security change
 * cannot quietly break ordinary use: upload, download, rename, move, tags,
 * permissions, versioning and locking.
 */
test.describe('ファイル操作', () => {
  /** @type {import('@playwright/test').APIRequestContext} */ let api;

  test.beforeAll(async ({ playwright, baseURL }) => {
    api = await apiAs(playwright, baseURL, ADMIN);
  });
  test.afterAll(async () => api?.dispose());

  test('アップロードしたファイルをそのまま取得できる', async () => {
    const body = 'ハローワールド\nline two\n';
    const file = await uploadFile(api, unique('roundtrip') + '.txt', body);
    expect(file.sizeBytes).toBe(Buffer.byteLength(body));
    expect(file.contentType).toBe('text/plain');
    expect(await (await api.get(`/api/files/${file.id}`)).text()).toBe(body);
  });

  test('日本語のファイル名を扱える', async () => {
    const name = '日本語ファイル名.txt';
    const file = await uploadFile(api, name, 'なかみ');
    expect(file.name).toBe(name);
    const download = await api.get(`/api/files/${file.id}`);
    expect(download.headers()['content-disposition']).toContain("UTF-8''");
    expect(await download.text()).toBe('なかみ');
  });

  test('フォルダ階層を作って辿れる', async () => {
    const parent = await createFolder(api, unique('level1'));
    const child = await createFolder(api, 'level2', { parentFolderId: parent.id });
    await uploadFile(api, 'leaf.txt', 'leaf', { parentFolderId: child.id });

    const inChild = await (await api.get(`/api/files?parentId=${child.id}`)).json();
    expect(inChild.map((f) => f.name)).toEqual(['leaf.txt']);
  });

  test('リネームと移動ができる', async () => {
    const from = await createFolder(api, unique('from'));
    const to = await createFolder(api, unique('to'));
    const file = await uploadFile(api, 'before.txt', 'x', { parentFolderId: from.id });

    const renamed = await api.put(`/api/files/${file.id}/name`, { data: { newName: 'after.txt' } });
    expect(renamed.status()).toBe(200);
    expect((await renamed.json()).name).toBe('after.txt');

    const moved = await api.put(`/api/files/${file.id}/parent`, { data: { newParentId: to.id } });
    expect(moved.status()).toBe(200);
    expect((await moved.json()).parentFolderId).toBe(to.id);
  });

  test('タグを付けて検索できる', async () => {
    const tag = unique('tag');
    const file = await uploadFile(api, unique('tagged') + '.txt', 'x');
    expect((await api.put(`/api/files/${file.id}/tags`, { data: { tags: `${tag},report` } })).status()).toBe(200);

    const hits = await (await api.get(`/api/files/search?tags=${tag}`)).json();
    expect(hits.map((f) => f.id)).toContain(file.id);
  });

  test('所有者はパーミッションを変更できる', async ({ page }) => {
    // Scoped to its own folder so the row is on the first page regardless of
    // what other specs have left at the root.
    const folder = await createFolder(api, unique('chmod-scope'));
    const file = await uploadFile(api, unique('chmod') + '.txt', 'x', {
      parentFolderId: folder.id,
      permissions: '600',
    });
    await login(page, ADMIN);
    await page.goto(`/?folderId=${folder.id}`);

    const row = page.locator('tr', { hasText: file.name });
    await row.locator('code.permission-badge').click();
    await page.fill('#permissionInput', '640');
    await page.click('#permissionForm button[type=submit]');
    await expect(page.locator('.alert-success')).toContainText('パーミッションを変更しました');

    const listed = await (await api.get(`/api/files?parentId=${folder.id}`)).json();
    expect(listed.find((f) => f.id === file.id).permissions).toBe(640);
  });

  test('バージョン管理フォルダでは更新のたびに履歴が増える', async () => {
    const folder = await createFolder(api, unique('versioned'));
    await api.put(`/api/files/folders/${folder.id}/versioning`, { data: { enabled: true } });
    const file = await uploadFile(api, 'doc.txt', 'v1', { parentFolderId: folder.id });

    await updateFile(api, file.id, 'doc.txt', 'v2');
    await updateFile(api, file.id, 'doc.txt', 'v3');

    const versions = await (await api.get(`/api/files/${file.id}/versions`)).json();
    expect(versions).toHaveLength(2);
    expect(versions[0].version).toBe(2);
    expect(versions[0].modifier).toBe('admin');
    expect(await (await api.get(`/api/files/${file.id}`)).text()).toBe('v3');
  });

  test('バージョン管理外のフォルダでは履歴を作らず上書きする', async () => {
    const folder = await createFolder(api, unique('plain'));
    const file = await uploadFile(api, 'doc.txt', 'first', { parentFolderId: folder.id });
    await updateFile(api, file.id, 'doc.txt', 'second');

    expect(await (await api.get(`/api/files/${file.id}/versions`)).json()).toHaveLength(0);
    expect(await (await api.get(`/api/files/${file.id}`)).text()).toBe('second');
  });

  test('ロック中のファイルは他のユーザーが変更できない', async ({ playwright, baseURL }) => {
    const other = { username: unique('locker'), password: 'lock-123456' };
    await ensureUser(api, { ...other, groups: ['users'] });
    const otherApi = await apiAs(playwright, baseURL, other);

    const folder = await createFolder(api, unique('lockable'), { permissions: '777' });
    await api.put(`/api/files/folders/${folder.id}/versioning`, { data: { enabled: true } });
    const file = await uploadFile(api, 'locked.txt', 'x', {
      parentFolderId: folder.id,
      permissions: '666',
    });

    expect((await api.put(`/api/files/${file.id}/lock`, { data: { locked: true } })).status()).toBe(204);

    const blocked = await otherApi.put(`/api/files/${file.id}/name`, { data: { newName: 'stolen.txt' } });
    expect(blocked.status()).toBe(423);

    // The holder can still work on the file they locked, and can release it.
    expect((await api.put(`/api/files/${file.id}/name`, { data: { newName: 'renamed-by-holder.txt' } }))
      .status()).toBe(200);
    expect((await api.put(`/api/files/${file.id}/lock`, { data: { locked: false } })).status()).toBe(204);
    expect((await otherApi.put(`/api/files/${file.id}/name`, { data: { newName: 'ok.txt' } })).status()).toBe(200);
    await otherApi.dispose();
  });

  test('画面からアップロードするとサイズと一緒に一覧に現れる', async ({ page }) => {
    const folder = await createFolder(api, unique('ui-upload'));
    await login(page, ADMIN);
    await page.goto(`/?folderId=${folder.id}`);

    await page.click('button:has-text("アップロード")');
    await page.setInputFiles('#uploadFile', {
      name: 'from-ui.txt',
      mimeType: 'text/plain',
      buffer: Buffer.from('uploaded through the browser'),
    });
    await page.click('#uploadModal button[type=submit]');

    await expect(page.locator('.alert-success')).toContainText('アップロードしました');
    const row = page.locator('tr', { hasText: 'from-ui.txt' });
    await expect(row).toHaveCount(1);
    await expect(row.locator('td.numeric')).toContainText('28');
  });

  test('画面からフォルダを作成できる', async ({ page }) => {
    const parent = await createFolder(api, unique('ui-folder-scope'));
    const name = unique('ui-folder');
    await login(page, ADMIN);
    await page.goto(`/?folderId=${parent.id}`);
    await page.click('button:has-text("新規フォルダ")');
    await page.fill('#folderName', name);
    await page.click('#folderModal button[type=submit]');
    await expect(page.locator('.alert-success')).toContainText('フォルダを作成しました');
    await expect(page.locator('tr', { hasText: name })).toHaveCount(1);
  });
});
