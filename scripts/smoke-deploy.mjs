import assert from 'node:assert/strict';

const { APP_URL, FRONTEND_ORIGIN, ADMIN_EMAIL, ADMIN_PASSWORD } = process.env;
assert(APP_URL && FRONTEND_ORIGIN && ADMIN_EMAIL && ADMIN_PASSWORD, 'Missing smoke-test environment.');
assert.equal(process.env.SMOKE_ALLOW_WRITES, 'isolated-staging', 'Use an isolated staging database.');
const base = new URL(APP_URL);
assert(
  base.protocol === 'https:' || ['localhost', '127.0.0.1'].includes(base.hostname),
  'Use HTTPS or a local loopback address.',
);

async function request(path, expected, { token, ...options } = {}) {
  const response = await fetch(new URL(path, base), {
    ...options,
    headers: {
      Origin: FRONTEND_ORIGIN,
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
    signal: AbortSignal.timeout(30_000),
  });
  assert.equal(response.status, expected, `${options.method ?? 'GET'} ${path}`);
  return response;
}

const health = await (await request('/actuator/health', 200)).json();
assert.equal(health.status, 'UP');
assert.equal(health.components, undefined);
assert.equal(health.details, undefined);
await request('/api/v1/sectors', 401);

const preflight = await request('/api/v1/sectors', 200, {
  method: 'OPTIONS',
  headers: { 'Access-Control-Request-Method': 'GET', 'Access-Control-Request-Headers': 'authorization' },
});
assert.equal(preflight.headers.get('access-control-allow-origin'), FRONTEND_ORIGIN);
const rejectedOrigin = await fetch(new URL('/api/v1/sectors', base), {
  method: 'OPTIONS',
  headers: { Origin: 'https://unapproved.example.invalid', 'Access-Control-Request-Method': 'GET' },
  signal: AbortSignal.timeout(30_000),
});
assert.equal(rejectedOrigin.status, 403);

const login = await request('/api/v1/auth/login', 200, {
  method: 'POST',
  body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
});
const { accessToken } = await login.json();
assert(accessToken, 'Login did not return a token.');
const currentUser = await (await request('/api/v1/auth/me', 200, { token: accessToken })).json();
assert.equal(currentUser.role, 'ADMIN');

let sectorId;
try {
  const created = await request('/api/v1/sectors', 201, {
    method: 'POST',
    token: accessToken,
    body: JSON.stringify({ name: `Deploy validation ${crypto.randomUUID()}` }),
  });
  ({ id: sectorId } = await created.json());
  const persisted = await (await request(`/api/v1/sectors/${sectorId}`, 200, { token: accessToken })).json();
  assert.equal(persisted.id, sectorId);
  const invalid = await request('/api/v1/sectors', 400, {
    method: 'POST', token: accessToken, body: '{}',
  });
  assert.equal((await invalid.json()).code, 'VALIDATION_ERROR');
} finally {
  if (sectorId) await request(`/api/v1/sectors/${sectorId}`, 204, { method: 'DELETE', token: accessToken });
}

await request('/api/v1/auth/logout', 204, { method: 'POST', token: accessToken });
await request('/api/v1/auth/me', 401, { token: accessToken });
console.log('PASS: health, CORS allow/deny, JWT, protected routes, persistence, validation and logout.');
