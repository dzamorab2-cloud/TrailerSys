// @ts-check
/**
 * Utilidades compartidas entre los specs de la suite E2E. No es un archivo
 * de tests (no termina en .spec.js), asi que Playwright no lo ejecuta.
 */
const API_BASE = 'http://localhost:8080/api';

/** Sufijo corto y unico para no colisionar con datos de otras corridas (placas, identificaciones, usernames son unique en la BD). */
function uid() {
  return `${Date.now().toString().slice(-8)}${Math.floor(Math.random() * 90 + 10)}`;
}

/** Login contra el backend real via API (sin pasar por la UI). Devuelve el token JWT. */
async function apiLogin(request, username, password) {
  const res = await request.post(`${API_BASE}/auth/login`, { data: { username, password } });
  if (!res.ok()) {
    throw new Error(`Login API fallo para "${username}": ${res.status()} ${await res.text()}`);
  }
  const body = await res.json();
  return body.token;
}

function authHeaders(token) {
  return { Authorization: `Bearer ${token}` };
}

/** POST con manejo de error consistente: lanza con el body si la respuesta no es 2xx. */
async function apiPost(request, token, path, data) {
  const res = await request.post(`${API_BASE}${path}`, { headers: authHeaders(token), data });
  if (!res.ok()) {
    throw new Error(`POST ${path} fallo: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}

async function apiGet(request, token, path) {
  const res = await request.get(`${API_BASE}${path}`, { headers: authHeaders(token) });
  if (!res.ok()) {
    throw new Error(`GET ${path} fallo: ${res.status()} ${await res.text()}`);
  }
  return res.json();
}

/** Login por la UI real (index.html -> app.html), igual que hace un usuario. */
async function uiLogin(page, username, password) {
  await page.goto('/index.html');
  await page.locator('#usuario').fill(username);
  await page.locator('#contrasena').fill(password);
  await page.locator('#loginForm button[type="submit"]').click();
  await page.waitForURL(/\/app\.html$/);
}

/** Cambia de modulo usando el link del sidebar (data-module="..."). */
async function irAlModulo(page, moduleName) {
  await page.locator(`.nav-link[data-module="${moduleName}"]`).click();
  await page.locator(`#module-${moduleName}`).waitFor({ state: 'visible' });
}

/** Guarda una captura de pantalla de evidencia con nombre de caso claro. */
async function evidencia(page, nombre) {
  await page.screenshot({ path: `evidencias/${nombre}.png`, fullPage: true });
}

/** Crea un cliente vinculado a un usuario CLIENTE recien creado (via API, como admin) y devuelve sus credenciales + ids. */
async function crearClienteConUsuario(request, adminToken, { nombreCliente, identificacionCliente, username, password }) {
  const cliente = await apiPost(request, adminToken, '/clientes', {
    nombre: nombreCliente,
    identificacion: identificacionCliente,
    estado: 'Activo',
    telefono: '0999999999',
    direccion: 'Direccion de prueba E2E',
  });
  await apiPost(request, adminToken, '/usuarios', {
    username,
    password,
    nombre: nombreCliente,
    rol: 'CLIENTE',
    activo: true,
    clienteId: cliente.id,
  });
  return cliente;
}

/**
 * Crea un Conductor vinculado a un usuario CONDUCTOR recien creado (via API,
 * como admin) y devuelve el Conductor + sus credenciales. Necesario porque
 * ViajeService.verificarPropioViajeSiEsConductor / SeguimientoService ya no
 * dejan que un usuario CONDUCTOR confirme la llegada ni gestione eventos de
 * un viaje que no sea del Conductor vinculado a su propia cuenta (Usuario.conductor):
 * reutilizar la cuenta compartida "conductor"/"conductor1234" (vinculada a
 * "Luis Herrera" por DataSeeder) fallaria con 403 para un viaje de prueba
 * con un Conductor distinto.
 */
async function crearConductorConUsuario(request, adminToken, { nombresConductor, identificacionConductor, username, password }) {
  const conductor = await apiPost(request, adminToken, '/conductores', {
    nombres: nombresConductor,
    identificacion: identificacionConductor,
    telefono: '0999999999',
    licenciaNumero: `LIC${identificacionConductor}`,
    licenciaCategoria: 'E',
    licenciaVencimiento: '2030-01-01',
    estado: 'Disponible',
  });
  await apiPost(request, adminToken, '/usuarios', {
    username,
    password,
    nombre: nombresConductor,
    rol: 'CONDUCTOR',
    activo: true,
    conductorId: conductor.id,
  });
  return conductor;
}

module.exports = {
  API_BASE,
  uid,
  apiLogin,
  authHeaders,
  apiPost,
  apiGet,
  uiLogin,
  irAlModulo,
  evidencia,
  crearClienteConUsuario,
  crearConductorConUsuario,
};
