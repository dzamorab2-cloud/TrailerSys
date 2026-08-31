// @ts-check
const { test, expect } = require('@playwright/test');
const { uid, apiLogin, apiPost, apiGet, uiLogin, irAlModulo, evidencia } = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';
// Cuenta de prueba sembrada por DataSeeder (rol CONDUCTOR). La autorizacion
// de confirmar-entrega / crear eventos de seguimiento es solo por ROL (ver
// ViajeController/SeguimientoController), no exige que el usuario sea "el"
// conductor asignado a ese viaje en particular.
const CONDUCTOR_USER = 'conductor';
const CONDUCTOR_PASS = 'conductor1234';

async function crearViajeEnCurso(request, adminToken, sufijo) {
  const cliente = await apiPost(request, adminToken, '/clientes', {
    nombre: `Cliente SEG ${sufijo}`, identificacion: `IDSEG${sufijo}`, estado: 'Activo',
    telefono: '0999999999', direccion: 'Direccion E2E',
  });
  const vehiculo = await apiPost(request, adminToken, '/vehiculos', {
    placa: `E2ESEG${sufijo}`, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
    color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
  });
  const conductor = await apiPost(request, adminToken, '/conductores', {
    nombres: `Conductor SEG ${sufijo}`, identificacion: `CONDSEG${sufijo}`, telefono: '0999999999',
    licenciaNumero: `LICSEG${sufijo}`, licenciaCategoria: 'E', licenciaVencimiento: '2030-01-01', estado: 'Disponible',
  });
  const origen = `CPSEG-Origen-${sufijo}`;
  const destino = `CPSEG-Destino-${sufijo}`;
  const viaje = await apiPost(request, adminToken, '/viajes', {
    vehiculoId: vehiculo.id, conductorId: conductor.id, clienteId: cliente.id,
    origen, destino, fechaSalida: '2026-01-01T08:00:00', estado: 'En Curso',
  });
  return { viaje, origen, destino };
}

async function abrirSeguimientoDelViaje(page, origen) {
  await irAlModulo(page, 'seguimiento');
  await page.locator('#seguimientoBuscar').fill(origen);
  const card = page.locator('#seguimientoGrid .item-card', { hasText: origen });
  await expect(card).toBeVisible();
  await card.locator('button[data-action="detalle"]').click();
  await expect(page.locator('#seguimientoModalOverlay')).toHaveClass(/open/);
}

test.describe('Seguimiento', () => {
  test('CP-07 Evento de seguimiento con fecha futura (Conductor) es rechazado', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);
    const { origen } = await crearViajeEnCurso(request, adminToken, sufijo);

    await uiLogin(page, CONDUCTOR_USER, CONDUCTOR_PASS);
    await abrirSeguimientoDelViaje(page, origen);

    const eventoRequests = [];
    page.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/api/seguimiento/eventos')) eventoRequests.push(req);
    });

    // datetime-local muy en el futuro; el input tiene max=ahora pero el
    // formulario tiene novalidate, asi que la validacion la hace el propio JS.
    await page.locator('#seguimientoFecha').fill('2030-01-01T10:00');
    await page.locator('#seguimientoUbicacion').fill('Km 10 via E2E');
    await page.locator('#seguimientoEventoForm button[type="submit"]').click();

    await expect(page.locator('#fieldSeguimientoFecha')).toHaveClass(/has-error/);
    await expect(page.locator('#fieldSeguimientoFecha .field-error')).toHaveText('La fecha y hora no pueden ser futuras.');
    expect(eventoRequests, 'no deberia haberse llamado a POST /api/seguimiento/eventos').toHaveLength(0);

    await evidencia(page, 'CP-07-evento-fecha-futura-rechazado');
  });

  test('CP-08 Conductor confirma llegada de un viaje En Curso: el viaje finaliza', async ({ page, request }) => {
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);
    const { viaje, origen } = await crearViajeEnCurso(request, adminToken, sufijo);

    await uiLogin(page, CONDUCTOR_USER, CONDUCTOR_PASS);
    await abrirSeguimientoDelViaje(page, origen);

    const btnConfirmar = page.locator('#seguimientoReporteEntrega button[data-action="confirmar-entrega"]');
    await expect(btnConfirmar).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept('Entrega confirmada por prueba E2E'));
    await btnConfirmar.click();

    await expect(page.locator('#seguimientoReporteEntrega .badge-success')).toHaveText(/llegada confirmada/i);

    // El viaje pasa a Finalizado y queda pendiente de validacion del supervisor
    // (entregaValidada sigue en false: ese es un paso aparte).
    const viajeActualizado = await apiGet(request, adminToken, `/viajes/${viaje.id}`);
    expect(viajeActualizado.estado).toBe('Finalizado');
    expect(viajeActualizado.entregaConfirmada).toBe(true);
    expect(viajeActualizado.entregaValidada).toBe(false);

    await evidencia(page, 'CP-08-conductor-confirma-llegada');
  });
});
