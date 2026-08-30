// @ts-check
const { test, expect } = require('@playwright/test');
const { uid, apiLogin, apiPost, uiLogin, irAlModulo, evidencia } = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';

test.describe('Busqueda en catalogos', () => {
  test('CP-10 Buscar un vehiculo que NO esta en las primeras 24 filas igual aparece', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);

    const datosVehiculo = (placa) => ({
      placa, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
      color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
    });

    // /api/paginas/vehiculos ordena por id descendente (el mas nuevo primero):
    // se crea el vehiculo objetivo PRIMERO y despues 26 mas, de forma que
    // esos 26 (con id mayor) ocupen toda la primera pagina de 24 y el
    // objetivo quede fuera de ella.
    const placaObjetivo = `E2ETGT${sufijo}`;
    await apiPost(request, adminToken, '/vehiculos', datosVehiculo(placaObjetivo));

    const posteriores = Array.from({ length: 26 }, (_, i) => `E2EAFTER${sufijo}${i}`.slice(0, 20));
    await Promise.all(posteriores.map((placa) => apiPost(request, adminToken, '/vehiculos', datosVehiculo(placa))));

    await uiLogin(page, ADMIN_USER, ADMIN_PASS);
    await irAlModulo(page, 'vehiculos');

    // Sin buscar: la primera pagina (24 filas, orden por id desc) no deberia
    // incluir el objetivo, confirmando que la precondicion del caso es real.
    await expect(page.locator('.vehicle-card', { hasText: placaObjetivo })).toHaveCount(0);

    await page.locator('#vehiculoBuscar').fill(placaObjetivo);
    await expect(page.locator('.vehicle-card', { hasText: placaObjetivo })).toBeVisible();

    await evidencia(page, 'CP-10-busqueda-fuera-de-primeras-24');
  });
});
