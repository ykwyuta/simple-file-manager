// @ts-check
const { test, expect } = require('@playwright/test');
const { ADMIN, apiAs, createFolder, isVisible, login, unique, uploadFile } = require('../helpers');

/**
 * SEC-06: soft-delete stamped only the item itself. Deleting a folder left its
 * children "not deleted": absent from every listing and from the trash, still
 * downloadable by id, and never picked up by the hard-delete job.
 *
 * TEST-CASE.md 3.4 specifies the cascading restore this now implements.
 */
test.describe('削除と復元', () => {
  /** @type {import('@playwright/test').APIRequestContext} */ let api;

  test.beforeAll(async ({ playwright, baseURL }) => {
    api = await apiAs(playwright, baseURL, ADMIN);
  });
  test.afterAll(async () => api?.dispose());

  /** parent / child.txt and parent / nested / grandchild.txt */
  async function buildTree() {
    const parent = await createFolder(api, unique('tree'));
    const child = await uploadFile(api, 'child.txt', 'CHILD', { parentFolderId: parent.id });
    const nested = await createFolder(api, 'nested', { parentFolderId: parent.id });
    const grandchild = await uploadFile(api, 'grandchild.txt', 'GRANDCHILD', {
      parentFolderId: nested.id,
    });
    return { parent, child, nested, grandchild };
  }

  test('フォルダを削除すると配下も削除され、IDで直接取得できなくなる', async () => {
    const { parent, child, nested, grandchild } = await buildTree();

    expect((await api.delete(`/api/files/${parent.id}`)).status()).toBe(204);

    for (const [label, item] of Object.entries({ child, nested, grandchild })) {
      expect(await isVisible(api, item), `${label} must not be reachable after the parent is deleted`)
        .toBe(false);
    }
  });

  test('ゴミ箱には削除の起点だけが表示される', async () => {
    const { parent, child } = await buildTree();
    await api.delete(`/api/files/${parent.id}`);

    const trash = await (await api.get('/api/files/trash')).json();
    const ids = trash.map((f) => f.id);
    expect(ids, 'the deleted folder is the restore point').toContain(parent.id);
    expect(ids, 'its children are restored with it, not separately').not.toContain(child.id);
  });

  test('フォルダを復元すると配下も一緒に戻る', async () => {
    const { parent, child, nested, grandchild } = await buildTree();
    await api.delete(`/api/files/${parent.id}`);

    expect((await api.post(`/api/files/${parent.id}/restore`)).status()).toBe(200);

    for (const [label, item] of Object.entries({ parent, child, nested, grandchild })) {
      expect(await isVisible(api, item), `${label} must come back with the parent`).toBe(true);
    }
    expect(await (await api.get(`/api/files/${grandchild.id}`)).text()).toBe('GRANDCHILD');
  });

  test('先に個別削除したファイルは親の復元で戻らない', async () => {
    const { parent, child } = await buildTree();

    // Deleted on its own first: a different deletion, so a different timestamp.
    await api.delete(`/api/files/${child.id}`);
    await new Promise((r) => setTimeout(r, 50));
    await api.delete(`/api/files/${parent.id}`);

    await api.post(`/api/files/${parent.id}/restore`);

    expect(await isVisible(api, parent)).toBe(true);
    expect(
      await isVisible(api, child),
      'a file the user threw away separately stays in the trash',
    ).toBe(false);

    const trash = await (await api.get('/api/files/trash')).json();
    expect(trash.map((f) => f.id)).toContain(child.id);
  });

  test('親が削除されたままの子は単独で復元できない', async () => {
    const { parent, child } = await buildTree();
    await api.delete(`/api/files/${child.id}`);
    await new Promise((r) => setTimeout(r, 50));
    await api.delete(`/api/files/${parent.id}`);

    const response = await api.post(`/api/files/${child.id}/restore`);
    expect(response.status()).toBe(400);
    expect(await response.text()).toContain('親フォルダ');
  });

  test('ゴミ箱画面から復元できる', async ({ page }) => {
    const folder = await createFolder(api, unique('ui-trash'));
    await api.delete(`/api/files/${folder.id}`);

    await login(page, ADMIN);
    await page.goto('/trash');
    const row = page.locator('tr', { hasText: folder.name });
    await expect(row).toHaveCount(1);
    await row.locator('button[type=submit]').click();
    await expect(page.locator('.alert-success')).toContainText('復元しました');

    expect(await isVisible(api, folder)).toBe(true);
  });
});
