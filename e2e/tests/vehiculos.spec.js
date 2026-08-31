// @ts-check
const { test, expect } = require('@playwright/test');
const { uiLogin, irAlModulo, evidencia, uid } = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';

test.describe('Vehiculos', () => {
  test('CP-03 Crear vehiculo con datos validos aparece en la lista en estado Disponible', async ({ page }) => {
    const placa = `E2E${uid()}`;

    await uiLogin(page, ADMIN_USER, ADMIN_PASS);
    await irAlModulo(page, 'vehiculos');

    await page.locator('#btnNuevoVehiculo').click();
    await expect(page.locator('#vehiculoModalOverlay')).toHaveClass(/open/);

    await page.locator('#vehiculoPlaca').fill(placa);
    await page.locator('#vehiculoMarca').selectOption('Volvo Trucks');
    // El select de modelo se puebla dinamicamente al cambiar de marca (actualizarModelos()).
    await page.locator('#vehiculoModelo').selectOption({ index: 1 });
    await page.locator('#vehiculoTipo').selectOption('Tráiler');
    await page.locator('#vehiculoAnio').fill('2022');
    await page.locator('#vehiculoColor').selectOption('Blanco');
    // #vehiculoEstado ya queda en "Disponible" por defecto (openForm()).
    await page.locator('#vehiculoKilometraje').fill('1500');
    await page.locator('#vehiculoCapacidad').fill('20');

    await page.locator('#vehiculoForm button[type="submit"]').click();
    await expect(page.locator('#vehiculoModalOverlay')).not.toHaveClass(/open/);

    // Se busca por la placa unica para no depender de en que pagina/orden cae la tarjeta.
    await page.locator('#vehiculoBuscar').fill(placa);
    const card = page.locator('.vehicle-card', { hasText: placa });
    await expect(card).toBeVisible();
    await expect(card.locator('.badge')).toHaveText('Disponible');

    await evidencia(page, 'CP-03-crear-vehiculo');
  });

  test('CP-04 Crear vehiculo con anio invalido (1800) es rechazado y no se crea', async ({ page }) => {
    const placa = `E2E${uid()}`;

    await uiLogin(page, ADMIN_USER, ADMIN_PASS);
    await irAlModulo(page, 'vehiculos');

    const vehiculoRequests = [];
    page.on('request', (request) => {
      if (request.method() === 'POST' && request.url().includes('/api/vehiculos')) vehiculoRequests.push(request);
    });

    await page.locator('#btnNuevoVehiculo').click();
    await page.locator('#vehiculoPlaca').fill(placa);
    await page.locator('#vehiculoMarca').selectOption('Volvo Trucks');
    await page.locator('#vehiculoModelo').selectOption({ index: 1 });
    await page.locator('#vehiculoTipo').selectOption('Tráiler');
    await page.locator('#vehiculoAnio').fill('1800');
    await page.locator('#vehiculoColor').selectOption('Blanco');
    await page.locator('#vehiculoKilometraje').fill('1500');
    await page.locator('#vehiculoCapacidad').fill('20');

    await page.locator('#vehiculoForm button[type="submit"]').click();

    // validate() de vehiculos.js bloquea el envio antes de llamar al backend.
    await expect(page.locator('#fieldVehiculoAnio')).toHaveClass(/has-error/);
    await expect(page.locator('#fieldVehiculoAnio .field-error')).toContainText('Ingresa un año entre 1980');
    await expect(page.locator('#vehiculoModalOverlay')).toHaveClass(/open/);
    expect(vehiculoRequests, 'no deberia haberse llamado a POST /api/vehiculos').toHaveLength(0);

    // Tampoco aparece en la lista (el modal sigue abierto, pero se confirma
    // ademas que la busqueda por placa no encuentra nada creado).
    await page.locator('#vehiculoModalClose').click();
    await page.locator('#vehiculoBuscar').fill(placa);
    await expect(page.locator('.vehicle-card', { hasText: placa })).toHaveCount(0);

    await evidencia(page, 'CP-04-vehiculo-anio-invalido');
  });
});
