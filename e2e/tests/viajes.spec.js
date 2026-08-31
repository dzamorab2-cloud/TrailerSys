// @ts-check
const { test, expect } = require('@playwright/test');
const { API_BASE, uid, apiLogin, authHeaders, apiPost, uiLogin, irAlModulo, evidencia } = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';
const FECHA_FUTURA = '2027-01-01T08:00:00';

async function crearClienteVehiculoConductor(request, token, sufijo) {
  const cliente = await apiPost(request, token, '/clientes', {
    nombre: `Cliente ${sufijo}`, identificacion: `ID${sufijo}`, estado: 'Activo',
    telefono: '0999999999', direccion: 'Direccion E2E',
  });
  const vehiculo = await apiPost(request, token, '/vehiculos', {
    placa: `E2E${sufijo}`, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
    color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
  });
  const conductor = await apiPost(request, token, '/conductores', {
    nombres: `Conductor ${sufijo}`, identificacion: `COND${sufijo}`, telefono: '0999999999',
    licenciaNumero: `LIC${sufijo}`, licenciaCategoria: 'E', licenciaVencimiento: '2030-01-01', estado: 'Disponible',
  });
  return { cliente, vehiculo, conductor };
}

test.describe('Viajes', () => {
  test('CP-05 Crear viaje con vehiculo ya asignado a otro viaje activo es rechazado (409)', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);

    const { cliente, vehiculo, conductor } = await crearClienteVehiculoConductor(request, adminToken, sufijo);
    // Un segundo conductor distinto para el intento en conflicto: lo que se
    // quiere probar es el conflicto por VEHICULO, no que ademas coincida el
    // conductor.
    const conductor2 = await apiPost(request, adminToken, '/conductores', {
      nombres: `Conductor B ${sufijo}`, identificacion: `CONDB${sufijo}`, telefono: '0999999999',
      licenciaNumero: `LICB${sufijo}`, licenciaCategoria: 'E', licenciaVencimiento: '2030-01-01', estado: 'Disponible',
    });

    const origen = `CP05-Origen-${sufijo}`;
    const destino = `CP05-Destino-${sufijo}`;

    const viaje1 = await apiPost(request, adminToken, '/viajes', {
      vehiculoId: vehiculo.id, conductorId: conductor.id, clienteId: cliente.id,
      origen, destino, fechaSalida: FECHA_FUTURA, estado: 'Programado',
    });
    expect(viaje1.id).toBeTruthy();

    // Intento de un segundo viaje con el MISMO vehiculo, ya activo en viaje1.
    const respuestaConflicto = await request.post(`${API_BASE}/viajes`, {
      headers: authHeaders(adminToken),
      data: {
        vehiculoId: vehiculo.id, conductorId: conductor2.id, clienteId: cliente.id,
        origen: `${origen}-B`, destino: `${destino}-B`, fechaSalida: FECHA_FUTURA, estado: 'Programado',
      },
    });
    expect(respuestaConflicto.status()).toBe(409);
    const cuerpoConflicto = await respuestaConflicto.json();
    expect(cuerpoConflicto.message).toMatch(/vehículo/i);

    // Evidencia visible: buscando por el origen unico de viaje1 solo aparece ese, no un segundo.
    await uiLogin(page, ADMIN_USER, ADMIN_PASS);
    await irAlModulo(page, 'viajes');
    await page.locator('#viajeBuscar').fill(origen);
    await expect(page.locator('#viajeGrid .item-card', { hasText: origen })).toHaveCount(1);

    await evidencia(page, 'CP-05-viaje-vehiculo-ocupado-409');
  });

  test('CP-06 Reasignar un viaje a otro vehiculo: el anterior vuelve a Disponible y el nuevo pasa a En Ruta', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);

    const cliente = await apiPost(request, adminToken, '/clientes', {
      nombre: `Cliente CP06 ${sufijo}`, identificacion: `ID06${sufijo}`, estado: 'Activo',
      telefono: '0999999999', direccion: 'Direccion E2E',
    });
    const vehiculoA = await apiPost(request, adminToken, '/vehiculos', {
      placa: `E2E06A${sufijo}`, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
      color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
    });
    const vehiculoB = await apiPost(request, adminToken, '/vehiculos', {
      placa: `E2E06B${sufijo}`, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
      color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
    });
    const conductor = await apiPost(request, adminToken, '/conductores', {
      nombres: `Conductor CP06 ${sufijo}`, identificacion: `COND06${sufijo}`, telefono: '0999999999',
      licenciaNumero: `LIC06${sufijo}`, licenciaCategoria: 'E', licenciaVencimiento: '2030-01-01', estado: 'Disponible',
    });

    // Origen/destino deben ser ciudades reales del catalogo de Ecuador: el
    // formulario de EDICION los muestra en un <select> (ecuador-locations.js),
    // no en un input libre. Un string arbitrario no calzaria con ninguna
    // <option> al abrir el modal de editar, dejando el select vacio y
    // bloqueando el envio por validacion de cliente ("El origen es
    // obligatorio."). Para ubicar la tarjeta se busca por la placa del
    // vehiculo (unica), no por el origen/destino (compartidos con otros datos).
    await apiPost(request, adminToken, '/viajes', {
      vehiculoId: vehiculoA.id, conductorId: conductor.id, clienteId: cliente.id,
      origen: 'Quito', destino: 'Guayaquil', fechaSalida: FECHA_FUTURA, estado: 'Programado',
    });

    await uiLogin(page, ADMIN_USER, ADMIN_PASS);
    await irAlModulo(page, 'viajes');
    await page.locator('#viajeBuscar').fill(vehiculoA.placa);
    const card = page.locator('#viajeGrid .item-card', { hasText: vehiculoA.placa });
    await expect(card).toBeVisible();
    await card.locator('button[data-action="editar"]').click();
    await expect(page.locator('#viajeModalOverlay')).toHaveClass(/open/);

    // Reasignar SOLO el vehiculo (se deja el mismo conductor, como haria un
    // coordinador que solo necesita cambiar la unidad).
    await page.locator('#viajeVehiculoBuscar').fill(vehiculoB.placa);
    await page.locator('#viajeVehiculoResultados .autocomplete-item').first().waitFor({ state: 'visible' });
    await page.locator('#viajeVehiculoResultados .autocomplete-item').first().click();

    await page.locator('#viajeForm button[type="submit"]').click();
    // Si el guardado falla, viajes.js muestra un alert() (auto-dismiss de
    // Playwright) y el modal se queda abierto: esa es la senal de que la
    // reasignacion no se completo.
    await expect(page.locator('#viajeModalOverlay')).not.toHaveClass(/open/);

    await irAlModulo(page, 'vehiculos');
    await page.locator('#vehiculoBuscar').fill(vehiculoA.placa);
    await expect(page.locator('.vehicle-card', { hasText: vehiculoA.placa }).locator('.badge')).toHaveText('Disponible');
    await page.locator('#vehiculoBuscar').fill(vehiculoB.placa);
    await expect(page.locator('.vehicle-card', { hasText: vehiculoB.placa }).locator('.badge')).toHaveText('En Ruta');

    await evidencia(page, 'CP-06-reasignar-viaje-vehiculo');
  });
});
