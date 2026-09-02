// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, basic, ensureUser, login, unique } = require('../helpers');

/**
 * UX-01 / DOC-01: the app authenticated with HTTP Basic and had no way to sign
 * out, while the README specified form login with logout.
 *
 * SEC-03: the API chain must not send a Basic challenge, because a browser that
 * caches Basic credentials replays them on cross-site requests.
 */
test.describe('認証とセッション', () => {
  test('未認証のアクセスはログイン画面に送られる', async ({ page }) => {
    const response = await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
    expect(response?.status()).toBe(200);
    await expect(page.locator('#username')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
  });

  test('誤った資格情報ではログインできず、理由が表示される', async ({ page }) => {
    await page.goto('/login');
    await page.fill('#username', 'admin');
    await page.fill('#password', 'wrong-password');
    await page.click('button[type=submit]');
    await expect(page.locator('.alert-error')).toContainText('ユーザー名またはパスワードが正しくありません');
    await expect(page).toHaveURL(/\/login\?error/);
  });

  test('ログインするとユーザー名が表示され、ログアウトできる', async ({ page }) => {
    await login(page, ADMIN);
    await expect(page.locator('.current-user')).toHaveText('admin');

    await page.click('button:has-text("ログアウト")');
    await expect(page).toHaveURL(/\/login\?logout/);
    await expect(page.locator('.alert-success')).toContainText('ログアウトしました');

    // The session is really gone, not just navigated away from.
    await page.goto('/');
    await expect(page).toHaveURL(/\/login$/);
  });

  test('APIの401はBasicチャレンジを返さない (ブラウザに資格情報をキャッシュさせない)', async ({ request }) => {
    const response = await request.get('/api/files');
    expect(response.status()).toBe(401);
    const headers = response.headers();
    expect(headers['www-authenticate']).toBeUndefined();
  });

  test('APIは事前送信されたBasic資格情報を受け付ける', async ({ request }) => {
    const response = await request.get('/api/files', {
      headers: { Authorization: basic(ADMIN) },
    });
    expect(response.status()).toBe(200);
  });

  test('APIチェーンはセッションCookieを発行しない (ステートレス)', async ({ request }) => {
    const response = await request.get('/api/files', {
      headers: { Authorization: basic(ADMIN) },
    });
    const setCookie = response.headers()['set-cookie'] || '';
    expect(setCookie).not.toContain('JSESSIONID');
  });

  test('作成したユーザーでログインできる', async ({ page, playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const username = unique('login-user');
    await ensureUser(api, { username, password: 'pw-12345678', groups: ['users'] });
    await api.dispose();

    await login(page, { username, password: 'pw-12345678' });
    await expect(page.locator('.current-user')).toHaveText(username);
  });
});
