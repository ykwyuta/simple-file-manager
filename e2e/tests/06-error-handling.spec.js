// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, ensureUser, login, unique, uploadFile } = require('../helpers');

/**
 * API-01: a catch-all @ExceptionHandler(Exception.class) turned every failure
 * into 500 with the raw exception text — 403s, 404s, validation errors, and even
 * the 404 for /favicon.ico. Clients could not tell the cases apart.
 *
 * API-02 / SEC-10: upload limits and name validation.
 */
test.describe('エラーハンドリング', () => {
  /** @type {import('@playwright/test').APIRequestContext} */ let api;

  test.beforeAll(async ({ playwright, baseURL }) => {
    api = await apiAs(playwright, baseURL, ADMIN);
  });
  test.afterAll(async () => api?.dispose());

  test('存在しないファイルは404を返す', async () => {
    const response = await api.get('/api/files/99999999');
    expect(response.status()).toBe(404);
    expect((await response.json()).status).toBe(404);
  });

  test('権限のない操作は403を返す', async ({ playwright, baseURL }) => {
    const outsider = { username: unique('outsider'), password: 'out-1234567' };
    await ensureUser(api, { ...outsider, groups: ['users'] });
    const outsiderApi = await apiAs(playwright, baseURL, outsider);

    const priv = await createFolder(api, unique('private'), { permissions: '700' });
    const response = await outsiderApi.get(`/api/files/${priv.id}`);
    expect(response.status()).toBe(403);
    await outsiderApi.dispose();
  });

  test('不正なパーミッションは400を返し、内部情報を漏らさない', async () => {
    const response = await api.post('/api/files/folders', {
      data: { name: unique('bad-perm'), permissions: '999' },
    });
    expect(response.status()).toBe(400);
    const body = await response.text();
    expect(body).not.toContain('org.springframework');
    expect(body).not.toContain('com.example.filemanager.controller');
  });

  test('名前の重複は409を返す', async () => {
    const name = unique('dup');
    await createFolder(api, name);
    const response = await api.post('/api/files/folders', { data: { name, permissions: '755' } });
    expect(response.status()).toBe(409);
  });

  test('パス区切りを含む名前は400で拒否される', async () => {
    for (const bad of ['../../etc/passwd', 'a/b.txt', '..', '.']) {
      const response = await api.post('/api/files', {
        multipart: {
          file: { name: bad, mimeType: 'text/plain', buffer: Buffer.from('x') },
          permissions: '644',
        },
      });
      expect(response.status(), `name "${bad}" must be rejected`).toBe(400);
    }
  });

  test('長すぎる名前は400で拒否される', async () => {
    const response = await api.post('/api/files/folders', {
      data: { name: 'a'.repeat(300), permissions: '755' },
    });
    expect(response.status()).toBe(400);
  });

  test('静的リソースの404が500にならない', async ({ request }) => {
    // The catch-all handler used to answer this with 500 on every page load.
    const response = await request.get('/no-such-static-file.png');
    expect(response.status()).not.toBe(500);
  });

  test('favicon は認証なしで取得できる', async ({ request }) => {
    const response = await request.get('/favicon.svg');
    expect(response.status()).toBe(200);
  });

  test('アップロード上限は1MBより大きい', async () => {
    // Spring Boot's default is 1MB, which is unusably small for a file manager.
    const twoMegabytes = Buffer.alloc(2 * 1024 * 1024, 0x41);
    const file = await uploadFile(api, unique('large') + '.bin', twoMegabytes);
    expect(file.sizeBytes).toBe(twoMegabytes.length);
  });

  test('画面のエラーメッセージは日本語で理由を説明する', async ({ page }) => {
    const name = unique('ui-dup');
    await createFolder(api, name);

    await login(page, ADMIN);
    await page.goto('/');
    await page.click('button:has-text("新規フォルダ")');
    await page.fill('#folderName', name);
    await page.click('#folderModal button[type=submit]');
    await expect(page.locator('.alert-error')).toContainText('既に存在します');
  });
});
