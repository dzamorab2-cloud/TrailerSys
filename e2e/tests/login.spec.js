// @ts-check
const { test, expect } = require('@playwright/test');
const { evidencia } = require('./helpers');

/**
 * Suite E2E del login de TrailerSys (index.html -> js/auth.js).
 *
 * Requiere el backend Spring Boot corriendo en http://localhost:8080 con
 * PostgreSQL y los datos sembrados por DataSeeder (usuario "admin" /
 * "admin1234"). Ver README.md de esta carpeta para los pasos previos.
 */

const USUARIO_VALIDO = 'admin';
const PASSWORD_VALIDO = 'admin1234';

async function login(page, usuario, password) {
  await page.goto('/index.html');
  await page.locator('#usuario').fill(usuario);
  await page.locator('#contrasena').fill(password);
  await page.locator('#loginForm button[type="submit"]').click();
}

test.describe('Login', () => {
  test('credenciales validas: navega fuera de index.html hacia app.html y muestra el dashboard', async ({ page }) => {
    await login(page, USUARIO_VALIDO, PASSWORD_VALIDO);

    // auth.js hace window.location.href = "app.html" tras un login exitoso.
    await expect(page).toHaveURL(/\/app\.html$/);

    // navigation.js activa el modulo "dashboard" por defecto y pinta la sesion en el topbar.
    await expect(page.locator('#module-dashboard')).toBeVisible();
    await expect(page.locator('#module-dashboard h1')).toHaveText('Dashboard');
    await expect(page.locator('#userName')).toHaveText(USUARIO_VALIDO);
  });

  test('contrasena incorrecta: muestra el error real del backend y NO navega ni recarga index.html', async ({ page }) => {
    await login(page, USUARIO_VALIDO, 'password-incorrecta');

    // Bug ya corregido (INFORME_CORRECCIONES.md, seccion 3): un 401 de login
    // no debe tratarse como "sesion expirada" ni forzar una redireccion que
    // recargue la propia pagina de login.
    const alert = page.locator('#loginAlert');
    await expect(alert).toBeVisible();

    const mensaje = await page.locator('#loginAlertText').textContent();
    expect(mensaje).toMatch(/usuario o contraseña incorrectos/i);
    expect(mensaje).not.toMatch(/sesión expirada/i);

    // Sigue en index.html: no hubo redireccion ni recarga.
    await expect(page).toHaveURL(/\/index\.html$/);
    await expect(page.locator('#loginForm')).toBeVisible();
  });

  test('campos vacios: no llama al backend y marca ambos campos como obligatorios', async ({ page }) => {
    await page.goto('/index.html');

    const loginRequests = [];
    page.on('request', (request) => {
      if (request.url().includes('/api/auth/login')) loginRequests.push(request);
    });

    await page.locator('#loginForm button[type="submit"]').click();

    // auth.js valida en el propio cliente antes de llamar a trailersysApiRequest:
    // con los dos campos vacios, la peticion nunca deberia dispararse.
    await expect(page.locator('#loginAlert')).toBeVisible();
    await expect(page.locator('#loginAlertText')).toHaveText('Usuario y contraseña son obligatorios.');
    await expect(page.locator('#fieldUsuario')).toHaveClass(/has-error/);
    await expect(page.locator('#fieldContrasena')).toHaveClass(/has-error/);

    expect(loginRequests, 'no deberia haberse llamado a /api/auth/login').toHaveLength(0);
    await expect(page).toHaveURL(/\/index\.html$/);
  });

  test('login exitoso guarda el token en sessionStorage', async ({ page }) => {
    await login(page, USUARIO_VALIDO, PASSWORD_VALIDO);
    await expect(page).toHaveURL(/\/app\.html$/);

    const sesionGuardada = await page.evaluate(() => sessionStorage.getItem('trailersys_session'));
    expect(sesionGuardada).not.toBeNull();

    const sesion = JSON.parse(/** @type {string} */ (sesionGuardada));
    expect(sesion.token).toBeTruthy();
    expect(sesion.username).toBe(USUARIO_VALIDO);
    expect(sesion.role).toBe('administrador');

    // Y localStorage se mantiene intacto: la sesion vive en sessionStorage, no ahi.
    const localStorageVacio = await page.evaluate(() => localStorage.getItem('trailersys_session'));
    expect(localStorageVacio).toBeNull();
  });
});

/**
 * CP-01 y CP-02: casos de login redactados para la actividad de la
 * asignatura (ver enunciado). Se agregan como casos propios ademas de los
 * 4 anteriores (que ya cubrian el mismo flujo con otro enfoque) para no
 * alterar el diseño de ninguno de los dos.
 */
test.describe('Casos de la actividad: Login', () => {
  test('CP-01 Login exitoso: admin/admin1234 redirige a app.html y muestra el Dashboard', async ({ page }) => {
    await login(page, USUARIO_VALIDO, PASSWORD_VALIDO);

    await expect(page).toHaveURL(/\/app\.html$/);
    await expect(page.locator('#module-dashboard')).toBeVisible();
    await expect(page.locator('#module-dashboard h1')).toHaveText('Dashboard');

    await evidencia(page, 'CP-01-login-exitoso');
  });

  test('CP-02 Login con clave incorrecta: muestra "Usuario o contraseña incorrectos" sin recargar la pagina', async ({ page }) => {
    await login(page, USUARIO_VALIDO, 'claveMala');

    const alert = page.locator('#loginAlert');
    await expect(alert).toBeVisible();
    await expect(page.locator('#loginAlertText')).toHaveText(/usuario o contraseña incorrectos/i);

    // "Sin recargar la pagina": sigue en index.html con el formulario intacto.
    await expect(page).toHaveURL(/\/index\.html$/);
    await expect(page.locator('#loginForm')).toBeVisible();

    await evidencia(page, 'CP-02-login-clave-incorrecta');
  });
});
