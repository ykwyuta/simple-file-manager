// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, ensureUser, login, unique, updateFile, uploadFile } = require('../helpers');

/**
 * SEC-03: CSRF protection was disabled outright. With Basic auth, a form on any
 * other origin could delete a logged-in user's files.
 *
 * SEC-05: the version history dialog interpolated the modifier's user name into
 * an innerHTML string, so a crafted user name executed script in the browser of
 * whoever opened the history.
 */
test.describe('CSRF と XSS', () => {
  test('別オリジンからのPOSTはCSRFトークンがないため拒否される', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const victim = await createFolder(api, unique('csrf-victim'));

    await login(page, ADMIN);

    // A page on another origin that auto-submits a form at the app.
    await page.route('http://csrf-attacker.test/**', (route) =>
      route.fulfill({
        contentType: 'text/html',
        body: `<h1>Cat pictures</h1>
               <form id="f" method="POST" action="${baseURL}/delete/${victim.id}"></form>
               <script>document.getElementById('f').submit()</script>`,
      }),
    );
    await page.goto('http://csrf-attacker.test/');
    await page.waitForTimeout(1500);

    // The folder is still there.
    const { isVisible } = require('../helpers');
    expect(await isVisible(api, victim), 'folder must survive the cross-site POST').toBe(true);
    await api.dispose();
  });

  test('CSRFトークン付きの正規フォームからは削除できる', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const target = await createFolder(api, unique('csrf-legit'));

    await login(page, ADMIN);
    await page.goto('/');
    const row = page.locator('tr', { hasText: target.name });
    await expect(row).toHaveCount(1);

    page.once('dialog', (d) => d.accept());
    await row.locator('button[aria-label="ゴミ箱に移動"]').click();
    await expect(page.locator('.alert-success')).toContainText('ゴミ箱に移動しました');
    await api.dispose();
  });

  test('ページはCSRFトークンをmetaタグで公開し、フォームに埋め込む', async ({ page }) => {
    await login(page, ADMIN);
    const token = await page.getAttribute('meta[name="_csrf"]', 'content');
    expect(token, 'CSRF token meta tag').toBeTruthy();
    const hidden = await page.locator('form input[name="_csrf"]').first().getAttribute('value');
    expect(hidden).toBe(token);
  });

  test('ユーザー名に含まれるHTMLはスクリプトとして実行されない', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);

    // The input filter is the first line of defence: a name like this is
    // rejected outright now.
    const rejected = await api.post('/api/users', {
      data: { username: '<img src=x onerror=window.__XSS__=1>', password: 'pw-12345678' },
    });
    expect(rejected.status(), 'HTML in a user name must be rejected').toBe(400);

    // The rendering path is the second: build a history entry and prove the
    // dialog escapes whatever text it is given.
    const attacker = { username: unique('mallory'), password: 'mal-1234567' };
    await ensureUser(api, { ...attacker, groups: ['users'] });
    const attackerApi = await apiAs(playwright, baseURL, attacker);

    const shared = await createFolder(api, unique('xss-shared'), { permissions: '777' });
    await api.put(`/api/files/folders/${shared.id}/versioning`, { data: { enabled: true } });
    const file = await uploadFile(api, 'shared.txt', 'v1', {
      parentFolderId: shared.id,
      permissions: '666',
    });
    await updateFile(attackerApi, file.id, 'shared.txt', 'v2');

    await login(page, ADMIN);
    await page.goto(`/?folderId=${shared.id}`);
    await page.locator('tr', { hasText: 'shared.txt' })
      .locator('button[aria-label="バージョン履歴"]').click();

    const historyRow = page.locator('#versionHistoryContent tbody tr').first();
    await expect(historyRow).toBeVisible();
    await expect(historyRow.locator('td').nth(1)).toHaveText(attacker.username);

    // Nothing scripted ran, and the modifier cell holds text, not markup.
    expect(await page.evaluate(() => window.__XSS__)).toBeUndefined();
    const cellHtml = await historyRow.locator('td').nth(1).innerHTML();
    expect(cellHtml).not.toContain('<img');
    expect(cellHtml).not.toContain('<script');

    await attackerApi.dispose();
    await api.dispose();
  });

  test('ファイル名に含まれるHTMLはエスケープして表示される', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const name = '<b>bold<b> &amp; <i>italic.txt';
    await uploadFile(api, name, 'contents');

    await login(page, ADMIN);
    await page.goto('/');
    const cell = page.locator('tbody tr td').filter({ hasText: 'bold' }).first();
    await expect(cell).toContainText(name);
    expect(await cell.innerHTML()).not.toContain('<b>bold</b>');
    await api.dispose();
  });

  test('アップロードしたファイルはattachmentとして返される (インラインXSS防止)', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const file = await uploadFile(api, 'payload.html', '<script>window.__XSS__=1</script>');
    const response = await api.get(`/api/files/${file.id}`);
    expect(response.headers()['content-disposition']).toContain('attachment');
    expect(response.headers()['content-type']).toContain('application/octet-stream');
    expect(response.headers()['x-content-type-options']).toBe('nosniff');
    await api.dispose();
  });
});
