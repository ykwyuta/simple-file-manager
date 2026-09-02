// @ts-check
const { expect } = require('@playwright/test');

const ADMIN = { username: 'admin', password: 'admin' };

/** Basic-auth header value, for the stateless /api/** chain. */
function basic({ username, password }) {
  return 'Basic ' + Buffer.from(`${username}:${password}`).toString('base64');
}

/**
 * An API request context authenticated as one user.
 *
 * The API chain answers 401 without a `WWW-Authenticate` challenge, so the
 * header has to be sent preemptively — which is exactly what a real API client
 * does and what a browser will not do on a cross-site request.
 */
async function apiAs(playwright, baseURL, user) {
  return playwright.request.newContext({
    baseURL,
    extraHTTPHeaders: { Authorization: basic(user) },
  });
}

/** Signs a browser page in through the login form. */
async function login(page, user) {
  await page.goto('/login');
  await page.fill('#username', user.username);
  await page.fill('#password', user.password);
  await Promise.all([page.waitForURL('**/'), page.click('button[type=submit]')]);
  await expect(page.locator('.current-user')).toHaveText(user.username);
}

/** Creates a user (and its group if needed) as the administrator. */
async function ensureUser(api, { username, password, groups = [] }) {
  const groupIds = [];
  for (const name of groups) {
    const existing = await (await api.get('/api/groups')).json();
    const found = existing.find((g) => g.name === name);
    if (found) {
      groupIds.push(found.id);
      continue;
    }
    const created = await api.post('/api/groups', { data: { name } });
    expect(created.status(), `create group ${name}`).toBe(201);
    groupIds.push((await created.json()).id);
  }
  const res = await api.post('/api/users', { data: { username, password, groupIds } });
  expect([201, 409], `create user ${username}`).toContain(res.status());
  const users = await (await api.get('/api/users')).json();
  return users.find((u) => u.username === username);
}

/** Creates a folder and returns its API representation. */
async function createFolder(api, name, { parentFolderId = null, permissions = '755' } = {}) {
  const res = await api.post('/api/files/folders', { data: { name, parentFolderId, permissions } });
  expect(res.status(), `create folder ${name}: ${await res.text()}`).toBe(201);
  return res.json();
}

/** Uploads an in-memory file and returns its API representation. */
async function uploadFile(api, name, contents, { parentFolderId = null, permissions = '644' } = {}) {
  const multipart = {
    file: { name, mimeType: 'text/plain', buffer: Buffer.from(contents) },
    permissions,
  };
  if (parentFolderId !== null) multipart.parentFolderId = String(parentFolderId);
  const res = await api.post('/api/files', { multipart });
  expect(res.status(), `upload ${name}: ${await res.text()}`).toBe(201);
  return res.json();
}

/** Replaces a file's contents, creating a version when the folder is versioned. */
async function updateFile(api, id, name, contents) {
  const res = await api.put(`/api/files/${id}`, {
    multipart: { file: { name, mimeType: 'text/plain', buffer: Buffer.from(contents) } },
  });
  expect(res.status(), `update ${id}: ${await res.text()}`).toBe(200);
  return res.json();
}

/**
 * Whether an item is visible to this user.
 *
 * `GET /api/files/{id}` downloads, and a folder cannot be downloaded, so
 * existence is probed through the listing endpoint for folders and the download
 * endpoint for files.
 */
async function isVisible(api, item) {
  const path = item.directory ? `/api/files?parentId=${item.id}` : `/api/files/${item.id}`;
  return (await api.get(path)).status() === 200;
}

/** A name unique to this run, so specs never collide on the shared server. */
function unique(prefix) {
  return `${prefix}-${Date.now().toString(36)}-${Math.floor(Math.random() * 1e4)}`;
}

module.exports = {
  ADMIN,
  apiAs,
  basic,
  createFolder,
  ensureUser,
  isVisible,
  login,
  unique,
  updateFile,
  uploadFile,
};
