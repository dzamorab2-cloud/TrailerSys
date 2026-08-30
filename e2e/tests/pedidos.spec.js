// @ts-check
const { test, expect } = require('@playwright/test');
const { uid, apiLogin, crearClienteConUsuario, uiLogin, irAlModulo, evidencia } = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';

test.describe('Pedidos (autoservicio Cliente)', () => {
  test('CP-09 Cliente crea un pedido y aparece en su propia lista en estado Pendiente', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);

    const username = `e2ecliente${sufijo}`;
    const password = 'clave1234';
    await crearClienteConUsuario(request, adminToken, {
      nombreCliente: `Cliente Pedido ${sufijo}`,
      identificacionCliente: `IDPED${sufijo}`,
      username,
      password,
    });

    await uiLogin(page, username, password);
    await irAlModulo(page, 'pedidos');

    const descripcion = `Pedido E2E ${sufijo}`;

    await page.locator('#btnNuevoPedido').click();
    await expect(page.locator('#pedidoModalOverlay')).toHaveClass(/open/);

    await page.locator('#pedidoDescripcion').fill(descripcion);
    await page.locator('#pedidoTipo').fill('Carga general');
    await page.locator('#pedidoPeso').fill('500');
    await page.locator('#pedidoOrigen').selectOption('Quito');
    await page.locator('#pedidoDestino').selectOption('Guayaquil');
    await page.locator('#pedidoObservaciones').fill('Pedido creado por la suite E2E');

    await page.locator('#pedidoForm button[type="submit"]').click();
    await expect(page.locator('#pedidoModalOverlay')).not.toHaveClass(/open/);

    await page.locator('#pedidoBuscar').fill(descripcion);
    const card = page.locator('.pedido-card', { hasText: descripcion });
    await expect(card).toBeVisible();
    await expect(card.locator('.badge').first()).toHaveText('Pendiente');

    await evidencia(page, 'CP-09-cliente-crea-pedido');
  });
});
