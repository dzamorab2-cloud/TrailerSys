// @ts-check
const { test, expect } = require('@playwright/test');
const {
  uid, apiLogin, apiPost, apiGet, uiLogin, irAlModulo, evidencia, crearConductorConUsuario,
} = require('./helpers');

const ADMIN_USER = 'admin';
const ADMIN_PASS = 'admin1234';
// Cuenta de prueba sembrada por DataSeeder (rol COORDINADOR, sin Conductor/
// Cliente vinculado). Coordinador sigue teniendo "seguimiento" en modules Y
// manage (js/roles.js) despues del cambio que le quito ese modulo al
// Conductor, asi que es el rol correcto para probar el formulario manual de
// eventos hoy.
const COORDINADOR_USER = 'coordinador';
const COORDINADOR_PASS = 'coordinador1234';

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

test.describe('Seguimiento', () => {
  test('CP-07 Evento de seguimiento con fecha futura (Coordinador) es rechazado', async ({ page, request }) => {
    // El Conductor ya NO tiene "seguimiento" en su lista de modulos
    // (js/roles.js: modules: ["dashboard", "mis-viajes"], manage: []) desde
    // que se le dio su propia "Mis viajes" (js/mis-viajes.js) - ese modulo
    // ni siquiera aparece en su sidebar, y su pantalla no tiene ningun
    // formulario para registrar un evento manual (Salida/Parada/Llegada/...).
    // El formulario de eventos con la misma validacion de "fecha no futura"
    // (SeguimientoEventoRequest.fechaHora es @PastOrPresent en el backend, y
    // seguimiento.js repite el chequeo en el cliente) solo lo sigue teniendo
    // Administrador/Coordinador, que es a quien se le prueba aqui.
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);
    const { origen } = await crearViajeEnCurso(request, adminToken, sufijo);

    await uiLogin(page, COORDINADOR_USER, COORDINADOR_PASS);
    await irAlModulo(page, 'seguimiento');
    await page.locator('#seguimientoBuscar').fill(origen);
    const card = page.locator('#seguimientoGrid .item-card', { hasText: origen });
    await expect(card).toBeVisible();
    await card.locator('button[data-action="detalle"]').click();
    await expect(page.locator('#seguimientoModalOverlay')).toHaveClass(/open/);

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

  test('CP-08 Conductor confirma la llegada de un viaje En Curso: la llegada queda registrada pero el viaje sigue En Curso', async ({ page, request }) => {
    // Flujo real desde "feat: llegada automatica, revision del cliente y
    // finalizacion explicita del viaje": confirmar-entrega YA NO finaliza el
    // viaje (ViajeService.confirmarEntrega -> registrarLlegada, sin tocar
    // viaje.estado). Solo marca entregaConfirmada=true; el viaje se queda
    // "En Curso" hasta que el cliente revisa la carga (Mis pedidos) y
    // Coordinador/Administrador lo cierra explicitamente con el nuevo boton
    // "Finalizar viaje" (Seguimiento). El escenario original de este caso
    // ("el viaje finaliza") ya no aplica: se ajusta aqui al resultado real.
    //
    // Ademas, el Conductor ya no confirma la llegada desde Seguimiento (ese
    // modulo no le aparece mas): lo hace desde su propia pantalla "Mis
    // viajes" (js/mis-viajes.js), con el mismo boton/endpoint de antes.
    //
    // Y ViajeService.verificarPropioViajeSiEsConductor ahora exige que el
    // viaje sea del Conductor vinculado a la cuenta que confirma - por eso
    // se crea aqui un Conductor+Usuario dedicados en vez de reutilizar la
    // cuenta compartida "conductor" (vinculada a "Luis Herrera" por
    // DataSeeder), que fallaria con 403 contra un viaje de otro conductor.
    const sufijo = uid();
    const adminToken = await apiLogin(request, ADMIN_USER, ADMIN_PASS);

    const cliente = await apiPost(request, adminToken, '/clientes', {
      nombre: `Cliente SEG08 ${sufijo}`, identificacion: `IDSEG08${sufijo}`, estado: 'Activo',
      telefono: '0999999999', direccion: 'Direccion E2E',
    });
    const vehiculo = await apiPost(request, adminToken, '/vehiculos', {
      placa: `E2ESEG08${sufijo}`, marca: 'Volvo Trucks', modelo: 'VNL 860', tipo: 'Tráiler', anio: 2022,
      color: 'Blanco', estado: 'Disponible', kilometraje: 1000, capacidad: 20,
    });
    const username = `e2econductor${sufijo}`;
    const password = 'clave1234';
    const conductor = await crearConductorConUsuario(request, adminToken, {
      nombresConductor: `Conductor SEG08 ${sufijo}`,
      identificacionConductor: `CONDSEG08${sufijo}`,
      username,
      password,
    });

    const origen = `CPSEG08-Origen-${sufijo}`;
    const viaje = await apiPost(request, adminToken, '/viajes', {
      vehiculoId: vehiculo.id, conductorId: conductor.id, clienteId: cliente.id,
      origen, destino: 'CPSEG08-Destino', fechaSalida: '2026-01-01T08:00:00', estado: 'En Curso',
    });

    await uiLogin(page, username, password);
    await irAlModulo(page, 'mis-viajes');
    await page.locator('#misViajesBuscar').fill(origen);
    const card = page.locator('#misViajesGrid .item-card', { hasText: origen });
    await expect(card).toBeVisible();
    await card.locator('button[data-action="detalle"]').click();
    await expect(page.locator('#misViajesDetalleOverlay')).toHaveClass(/open/);

    const btnConfirmar = page.locator('#misViajesReporteEntrega button[data-action="confirmar-entrega"]');
    await expect(btnConfirmar).toBeVisible();

    page.once('dialog', (dialog) => dialog.accept('Entrega confirmada por prueba E2E'));
    await btnConfirmar.click();

    // Todavia no hay revision del cliente: el badge indica que se espera esa revision.
    await expect(page.locator('#misViajesReporteEntrega .badge-warning')).toHaveText(/esperando revisión del cliente/i);

    const viajeActualizado = await apiGet(request, adminToken, `/viajes/${viaje.id}`);
    expect(viajeActualizado.estado).toBe('En Curso');
    expect(viajeActualizado.entregaConfirmada).toBe(true);
    expect(viajeActualizado.entregaConfirmadaCliente).toBe(false);

    await evidencia(page, 'CP-08-conductor-confirma-llegada');
  });
});
