// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, ensureUser, login, unique } = require('../helpers');

/**
 * SEC-01: user and group management lived at /api/users and /api/groups, outside
 * the /admin/** rule that was the only authorization in place. Any authenticated
 * user could create and delete accounts and add themselves to the admins group.
 */
test.describe('権限昇格の防止', () => {
  const member = { username: unique('member'), password: 'member-12345' };

  test.beforeAll(async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    await ensureUser(api, { ...member, groups: ['users'] });
    await api.dispose();
  });

  test('一般ユーザーはユーザー一覧を取得できない', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, member);
    expect((await api.get('/api/users')).status()).toBe(403);
    expect((await api.get('/api/groups')).status()).toBe(403);
    await api.dispose();
  });

  test('一般ユーザーはユーザーを作成・削除できない', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, member);
    const created = await api.post('/api/users', {
      data: { username: 'evil', password: 'evil-12345' },
    });
    expect(created.status()).toBe(403);
    expect((await api.delete('/api/users/1')).status()).toBe(403);
    await api.dispose();
  });

  test('一般ユーザーは自分をadminsグループに追加できない', async ({ playwright, baseURL }) => {
    const adminApi = await apiAs(playwright, baseURL, ADMIN);
    const groups = await (await adminApi.get('/api/groups')).json();
    const adminsGroup = groups.find((g) => g.name === 'admins');
    const users = await (await adminApi.get('/api/users')).json();
    const self = users.find((u) => u.username === member.username);

    const api = await apiAs(playwright, baseURL, member);
    const escalate = await api.post(`/api/users/${self.id}/groups/${adminsGroup.id}`);
    expect(escalate.status()).toBe(403);

    // And the membership really did not change.
    const after = await (await adminApi.get('/api/users')).json();
    const stillMember = after.find((u) => u.username === member.username);
    expect(stillMember.groups.map((g) => g.name)).not.toContain('admins');

    await api.dispose();
    await adminApi.dispose();
  });

  test('一般ユーザーは管理画面を開けない', async ({ page }) => {
    await login(page, member);
    const response = await page.goto('/admin/users');
    expect(response?.status()).toBe(403);
  });

  test('管理者向けのナビゲーションは一般ユーザーに表示されない', async ({ page }) => {
    await login(page, member);
    await expect(page.locator('a[href="/admin/users"]')).toHaveCount(0);
    await expect(page.locator('a[href="/admin/groups"]')).toHaveCount(0);
  });

  test('管理者には管理画面へのナビゲーションが表示される', async ({ page }) => {
    await login(page, ADMIN);
    await expect(page.locator('a[href="/admin/users"]')).toBeVisible();
    await expect(page.locator('a[href="/admin/groups"]')).toBeVisible();
    expect((await page.goto('/admin/users'))?.status()).toBe(200);
  });

  test('管理者はユーザーとグループを管理できる', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    expect((await api.get('/api/users')).status()).toBe(200);
    const username = unique('managed');
    const created = await api.post('/api/users', {
      data: { username, password: 'pw-12345678' },
    });
    expect(created.status()).toBe(201);
    const id = (await created.json()).id;
    expect((await api.delete(`/api/users/${id}`)).status()).toBe(204);
    await api.dispose();
  });

  test('adminアカウント自体は削除できない', async ({ playwright, baseURL }) => {
    const api = await apiAs(playwright, baseURL, ADMIN);
    const users = await (await api.get('/api/users')).json();
    const admin = users.find((u) => u.username === 'admin');
    const response = await api.delete(`/api/users/${admin.id}`);
    expect(response.status()).toBe(400);
    expect(await response.text()).toContain('admin ユーザーは削除できません');
    await api.dispose();
  });
});
