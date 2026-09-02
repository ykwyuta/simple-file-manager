// @ts-check
const { test, expect, devices } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, ensureUser, login, unique, uploadFile } = require('../helpers');

/**
 * UX-02: paging filtered the page in Java after the database had already paged
 * it, so the item count came from the unfiltered query. A user who could see two
 * of twenty-nine files was shown "1 / 2 ページ (全 29 件)".
 *
 * UX-04 / UX-05 / UX-06 / UX-08: viewport, local icons, size column, labels.
 */
test.describe('ユーザビリティ', () => {
  test('ページネーションの件数は閲覧できる件数と一致する', async ({ page, playwright, baseURL }) => {
    const adminApi = await apiAs(playwright, baseURL, ADMIN);
    const viewer = { username: unique('viewer'), password: 'view-123456' };
    await ensureUser(adminApi, { ...viewer, groups: ['viewers'] });
    const viewerApi = await apiAs(playwright, baseURL, viewer);

    // A folder the viewer can enter, holding files only the admin can read plus
    // two the viewer can read.
    const root = await createFolder(adminApi, unique('paging'), { permissions: '755' });
    for (let i = 0; i < 25; i++) {
      await createFolder(adminApi, `hidden-${i}`, { parentFolderId: root.id, permissions: '700' });
    }
    await createFolder(adminApi, 'visible-a', { parentFolderId: root.id, permissions: '755' });
    await createFolder(adminApi, 'visible-b', { parentFolderId: root.id, permissions: '755' });

    // The API agrees with what the viewer can actually open.
    const listed = await (await viewerApi.get(`/api/files?parentId=${root.id}`)).json();
    expect(listed).toHaveLength(2);

    await login(page, viewer);
    await page.goto(`/?folderId=${root.id}`);
    const rows = page.locator('tbody tr');
    await expect(rows).toHaveCount(2);

    // 2 visible items fit on one page, so no pagination is offered at all.
    await expect(page.locator('.pagination-container')).toHaveCount(0);

    // The administrator sees all 27 and therefore does get paging.
    await login(page, ADMIN);
    await page.goto(`/?folderId=${root.id}`);
    await expect(page.locator('.pagination-container')).toContainText('全 27 件');

    await viewerApi.dispose();
    await adminApi.dispose();
  });

  test('ページ送りしても総数と表示が一致する', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const root = await createFolder(api, unique('pages'), { permissions: '755' });
    for (let i = 0; i < 23; i++) {
      await createFolder(api, `item-${String(i).padStart(2, '0')}`, { parentFolderId: root.id });
    }

    await login(page, ADMIN);
    await page.goto(`/?folderId=${root.id}&page=0&size=20`);
    await expect(page.locator('tbody tr')).toHaveCount(20);
    await expect(page.locator('.pagination-container')).toContainText('全 23 件');

    await page.click('.pagination-container a:has-text("次へ")');
    await expect(page.locator('tbody tr')).toHaveCount(3);
    await api.dispose();
  });

  test('viewportメタタグがあり、モバイル幅で横スクロールしない', async ({ browser }) => {
    const context = await browser.newContext({ ...devices['iPhone 13'] });
    const page = await context.newPage();
    await login(page, ADMIN);

    await expect(page.locator('meta[name="viewport"]')).toHaveCount(1);
    const widths = await page.evaluate(() => ({
      client: document.documentElement.clientWidth,
      scroll: document.documentElement.scrollWidth,
    }));
    // The page renders at device width, not a scaled-down 980px desktop layout.
    expect(widths.client).toBeLessThan(500);
    expect(widths.scroll).toBeLessThanOrEqual(widths.client + 1);
    await context.close();
  });

  test('html要素にlang属性がある', async ({ page }) => {
    await login(page, ADMIN);
    expect(await page.getAttribute('html', 'lang')).toBe('ja');
  });

  test('アイコンは外部CDNに依存しない', async ({ page }) => {
    const external = [];
    page.on('request', (r) => {
      const url = r.url();
      if (!url.startsWith('http://localhost') && !url.startsWith('data:')) external.push(url);
    });
    await login(page, ADMIN);
    await page.goto('/');
    expect(external, 'the page must render from its own origin alone').toEqual([]);
    // And the sprite really is there.
    await expect(page.locator('svg symbol#i-folder')).toHaveCount(1);
  });

  test('すべての操作ボタンにアクセシブル名がある', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    await uploadFile(api, unique('labelled') + '.txt', 'x');
    await login(page, ADMIN);
    await page.goto('/');

    const unnamed = await page.evaluate(() =>
      [...document.querySelectorAll('table td button, table td a')]
        .filter((el) => !el.textContent.trim() && !el.getAttribute('aria-label') && !el.getAttribute('title'))
        .length,
    );
    expect(unnamed).toBe(0);
    await api.dispose();
  });

  test('フォームの入力欄にはラベルが結び付いている', async ({ page }) => {
    await login(page, ADMIN);
    const unlabelled = await page.evaluate(() =>
      [...document.querySelectorAll('input, select, textarea')]
        .filter((el) => el.type !== 'hidden' && !el.labels?.length && !el.getAttribute('aria-label'))
        .map((el) => el.name || el.id || el.type),
    );
    expect(unlabelled).toEqual([]);
  });

  test('ファイルサイズが一覧に表示される', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const folder = await createFolder(api, unique('sized'));
    await uploadFile(api, 'measured.txt', 'x'.repeat(1234), { parentFolderId: folder.id });

    await login(page, ADMIN);
    await page.goto(`/?folderId=${folder.id}`);
    await expect(page.locator('tr', { hasText: 'measured.txt' }).locator('td.numeric'))
      .toContainText('1,234');
    await api.dispose();
  });

  test('一覧はフォルダが先、その後に名前順で並ぶ', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const root = await createFolder(api, unique('sorted'));
    await uploadFile(api, 'b-file.txt', 'x', { parentFolderId: root.id });
    await uploadFile(api, 'a-file.txt', 'x', { parentFolderId: root.id });
    await createFolder(api, 'z-folder', { parentFolderId: root.id });
    await createFolder(api, 'm-folder', { parentFolderId: root.id });

    await login(page, ADMIN);
    await page.goto(`/?folderId=${root.id}`);
    const names = await page.locator('tbody tr td:first-child').allInnerTexts();
    expect(names.map((n) => n.trim())).toEqual(['m-folder', 'z-folder', 'a-file.txt', 'b-file.txt']);
    await api.dispose();
  });

  test('空のフォルダには空状態のメッセージが出る', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const empty = await createFolder(api, unique('empty'));
    await login(page, ADMIN);
    await page.goto(`/?folderId=${empty.id}`);
    await expect(page.locator('.empty-state')).toContainText('まだ何もありません');
    await api.dispose();
  });

  test('検索は条件なしで全件を返さない', async ({ page }) => {
    await login(page, ADMIN);
    await page.goto('/search');
    await expect(page.locator('.empty-state')).toContainText('検索条件を入力してください');
    await expect(page.locator('tbody tr')).toHaveCount(1); // the empty-state row
  });

  test('検索はファイル名で絞り込める', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const needle = unique('needle');
    await uploadFile(api, `${needle}.txt`, 'findme');

    await login(page, ADMIN);
    await page.goto('/search');
    await page.fill('#searchQuery', needle);
    await page.click('button:has-text("検索")');
    await expect(page.locator('tbody tr')).toHaveCount(1);
    await expect(page.locator('tbody')).toContainText(needle);
    await api.dispose();
  });

  test('LIKEのワイルドカードは文字として検索される', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const literal = unique('pct') + '-100%-report.txt';
    await uploadFile(api, literal, 'x');
    await uploadFile(api, unique('pct') + '-other.txt', 'x');

    // "%" used to be passed through to LIKE unescaped and matched everything.
    const hits = await (await api.get('/api/files/search?name=100%25-report')).json();
    expect(hits.map((f) => f.name)).toEqual([literal]);
    await api.dispose();
  });
});
