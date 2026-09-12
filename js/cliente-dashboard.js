(function () {
  const session = trailersysGetSession();
  if (session?.role !== "cliente") return;

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // Se muestra la vista personalizada y se oculta la generica (la de
  // Administrador/Coordinador/etc.) - mismo patron que
  // js/conductor-dashboard.js.
  $("dashboardGenericView").hidden = true;
  $("dashboardClienteView").hidden = false;

  const ESTADO_COLOR = {
    Pendiente: "var(--color-warning)",
    Asignada: "var(--color-info)",
    "En Tránsito": "var(--color-primary)",
    Entregada: "var(--color-success)",
    Cancelada: "var(--color-danger)",
  };

  function renderStats(pedidos, reclamosAbiertos) {
    const activos = pedidos.filter((p) => p.estado !== "Entregada" && p.estado !== "Cancelada").length;
    const enTransito = pedidos.filter((p) => p.estado === "En Tránsito").length;
    const entregados = pedidos.filter((p) => p.estado === "Entregada").length;
    const card = (icon, value, label, tone = "") =>
      `<div class="stat-card stat-card-action ${tone}"><div class="stat-card-icon"><i class="bi ${icon}"></i></div><div><div class="stat-card-value">${value}</div><div class="stat-card-label">${label}</div></div></div>`;
    $("clienteDashboardStats").innerHTML =
      card("bi-box-seam", activos, "Pedidos activos") +
      card("bi-truck", enTransito, "En tránsito", "info") +
      card("bi-check-circle", entregados, "Entregados", "success") +
      card("bi-exclamation-triangle", reclamosAbiertos, "Reclamos abiertos", reclamosAbiertos ? "danger" : "");
  }

  // Donut "Pedidos por estado" - mismo patron que el donut "Viajes por
  // estado" de js/conductor-dashboard.js, adaptado a los estados de una
  // Carga en vez de un Viaje.
  function renderDonut(pedidos) {
    const datos = ["Pendiente", "Asignada", "En Tránsito", "Entregada", "Cancelada"]
      .map((estado) => [estado, pedidos.filter((p) => p.estado === estado).length]);
    const total = datos.reduce((sum, [, cantidad]) => sum + cantidad, 0);
    const donut = $("clienteDonutChart");
    const legend = $("clienteDonutLegend");

    if (total === 0) {
      donut.style.background = "var(--color-border, #e5e5e5)";
      legend.innerHTML = '<p class="dashboard-empty">Todavía no tienes pedidos registrados.</p>';
      return;
    }

    let acumulado = 0;
    const stops = datos
      .filter(([, cantidad]) => cantidad > 0)
      .map(([estado, cantidad]) => {
        const inicio = (acumulado / total) * 360;
        acumulado += cantidad;
        const fin = (acumulado / total) * 360;
        return `${ESTADO_COLOR[estado]} ${inicio}deg ${fin}deg`;
      });
    donut.style.background = `conic-gradient(${stops.join(", ")})`;
    legend.innerHTML = datos.map(([estado, cantidad]) => `
      <div class="dashboard-donut-legend-item">
        <span class="dashboard-donut-dot" style="background:${ESTADO_COLOR[estado]}"></span>
        <span>${esc(estado)}</span>
        <strong>${cantidad}</strong>
      </div>`).join("");
  }

  // "Pedidos por mes" (ultimos 6 meses) - a diferencia del conductor, aca
  // no hay un endpoint de "resumen" en el backend que ya lo traiga armado
  // (PedidoClienteController no tiene uno), asi que se calcula en el
  // propio frontend a partir de "fechaCreacion" de cada pedido ya cargado.
  function pedidosPorMes(pedidos) {
    const ahora = new Date();
    const meses = [];
    for (let i = 5; i >= 0; i--) meses.push(new Date(ahora.getFullYear(), ahora.getMonth() - i, 1));
    return meses.map((d) => ({
      label: d.toLocaleDateString("es-EC", { month: "short" }).replace(".", "").replace(/^./, (c) => c.toUpperCase()),
      value: pedidos.filter((c) => {
        if (!c.fechaCreacion) return false;
        const f = new Date(c.fechaCreacion);
        return f.getFullYear() === d.getFullYear() && f.getMonth() === d.getMonth();
      }).length,
    }));
  }

  function renderTendencia(pedidos) {
    trailersysRenderAreaChart($("clienteBarChart"), pedidosPorMes(pedidos), { color: "var(--color-primary)" });
  }

  // --- Mapa grande: un marcador simulado por cada pedido "En Curso" ---
  // "Solo visual": a diferencia del mapa del detalle de un pedido puntual
  // (ver calcularPosicionSimulada en js/pedidos.js), este panorama con
  // varios viajes a la vez no corrige la posicion con lo que el conductor
  // reporto a mano (GET /mis-cargas/{id}/ubicacion-reportada) - eso
  // multiplicaria una peticion extra por viaje en cada actualizacion solo
  // para un vistazo decorativo del dashboard. Se simula interpolando el
  // tiempo transcurrido desde la salida sobre la ruta ya calculada (o la
  // linea recta origen-destino si todavia no hay una), igual formula que
  // pedidos.js pero sin ese ajuste fino.
  let mapaInstance = null;
  let marcadores = [];
  let actualizacionIntervalId = null;
  const VEHICULO_ICON = L.divIcon({
    className: "vehicle-marker",
    html: '<i class="bi bi-truck"></i>',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });
  // Con un solo pedido en el mapa el origen/destino se intuyen mirando los
  // extremos de la linea, pero con varios a la vez (justo lo que pidio el
  // usuario probar) hacia falta marcarlos aparte para poder distinguir
  // donde empieza y termina cada ruta - antes el unico marcador era el del
  // vehiculo, que ademas queda pegado al destino en cuanto la simulacion
  // llega al 100% de progreso (ver calcularPosicionSimulada).
  const ORIGEN_ICON = L.divIcon({
    className: "ruta-punto-marker ruta-punto-origen",
    html: '<i class="bi bi-circle-fill"></i>',
    iconSize: [16, 16],
    iconAnchor: [8, 8],
  });
  const DESTINO_ICON = L.divIcon({
    className: "ruta-punto-marker ruta-punto-destino",
    html: '<i class="bi bi-flag-fill"></i>',
    iconSize: [22, 22],
    iconAnchor: [6, 20],
  });

  function calcularPosicionSimulada(viaje) {
    const pathRaw = viaje.ruta?.path?.length >= 2 ? viaje.ruta.path
      : (viaje.ruta?.origenLat != null && viaje.ruta?.destinoLat != null)
        ? [{ lat: viaje.ruta.origenLat, lng: viaje.ruta.origenLng }, { lat: viaje.ruta.destinoLat, lng: viaje.ruta.destinoLng }]
        : null;
    if (!pathRaw || pathRaw.length < 2 || !viaje.fechaSalida || !viaje.ruta.duracionMin) return null;
    const path = pathRaw.map((p) => (Array.isArray(p) ? p : [p.lat, p.lng]));
    const salida = new Date(viaje.fechaSalida).getTime();
    const duracionMs = viaje.ruta.duracionMin * 60000;
    const progreso = Math.min(1, Math.max(0, (Date.now() - salida) / duracionMs));
    const posicionExacta = progreso * (path.length - 1);
    const indiceBase = Math.floor(posicionExacta);
    const indiceSiguiente = Math.min(path.length - 1, indiceBase + 1);
    const fraccionLocal = posicionExacta - indiceBase;
    const p1 = path[indiceBase], p2 = path[indiceSiguiente];
    return [p1[0] + (p2[0] - p1[0]) * fraccionLocal, p1[1] + (p2[1] - p1[1]) * fraccionLocal];
  }

  // Se destruye no solo al cerrar (no hay "cerrar" aca), sino cada vez que
  // el cliente sale del modulo Dashboard (ver el listener de
  // module-activated mas abajo): a diferencia del mapa decorativo de
  // Ecuador que usan los demas roles, este tiene un setInterval corriendo
  // de fondo que no tiene sentido dejar vivo mientras el cliente esta
  // viendo "Mis pedidos" u otro modulo.
  function destruirMapa() {
    if (actualizacionIntervalId) { clearInterval(actualizacionIntervalId); actualizacionIntervalId = null; }
    if (mapaInstance) { mapaInstance.remove(); mapaInstance = null; }
    marcadores = [];
  }

  function mostrarMapaVacio(mensaje) {
    $("clienteDashboardMapa").innerHTML = `<div class="route-map-placeholder"><i class="bi bi-truck"></i><p>${esc(mensaje)}</p></div>`;
  }

  async function renderMapaGrande(enCurso) {
    destruirMapa();
    const conRuta = enCurso.filter((t) => t.viaje.ruta);
    if (conRuta.length === 0) {
      mostrarMapaVacio(enCurso.length === 0
        ? "No tienes pedidos en ruta en este momento."
        : "Tus pedidos en ruta todavía no tienen una ubicación calculada.");
      return;
    }

    const contenedor = $("clienteDashboardMapa");
    contenedor.innerHTML = "";
    mapaInstance = L.map(contenedor, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { attribution: "&copy; colaboradores de OpenStreetMap", maxZoom: 18 }).addTo(mapaInstance);

    const todosLosPuntos = [];
    for (const { carga, viaje } of conRuta) {
      // Igual que renderMapa() en js/seguimiento.js/js/pedidos.js: si el
      // viaje no trae un "path" real (nadie calculo la ruta con el boton
      // de Administrador), se pide una vez a OSRM en vez de quedarse con
      // la simulacion mas tosca de una linea recta.
      if (!viaje.ruta.path || viaje.ruta.path.length < 2) {
        const ruta = await trailersysGetRoute({ lat: viaje.ruta.origenLat, lng: viaje.ruta.origenLng }, { lat: viaje.ruta.destinoLat, lng: viaje.ruta.destinoLng });
        if (ruta) viaje.ruta.path = ruta.path;
      }
      const puntos = viaje.ruta.path?.length
        ? viaje.ruta.path.map((p) => (Array.isArray(p) ? p : [p.lat, p.lng]))
        : [[viaje.ruta.origenLat, viaje.ruta.origenLng], [viaje.ruta.destinoLat, viaje.ruta.destinoLng]];
      todosLosPuntos.push(...puntos);
      const etiquetaRuta = `Pedido #${carga.id} · ${esc(carga.origen)} → ${esc(carga.destino)}`;
      L.polyline(puntos, { color: "#e8590c", weight: 4, opacity: 0.85 }).addTo(mapaInstance);
      L.marker(puntos[0], { icon: ORIGEN_ICON }).addTo(mapaInstance).bindTooltip(`Origen · ${esc(carga.origen)}`);
      L.marker(puntos[puntos.length - 1], { icon: DESTINO_ICON }).addTo(mapaInstance).bindTooltip(`Destino · ${esc(carga.destino)}`);
      const posicion = calcularPosicionSimulada(viaje) || puntos[0];
      const marker = L.marker(posicion, { icon: VEHICULO_ICON }).addTo(mapaInstance).bindTooltip(etiquetaRuta);
      marcadores.push({ marker, viaje });
    }

    if (todosLosPuntos.length) mapaInstance.fitBounds(L.latLngBounds(todosLosPuntos), { padding: [30, 30] });
    setTimeout(() => mapaInstance?.invalidateSize(), 200);

    if (marcadores.length) {
      actualizacionIntervalId = setInterval(() => {
        marcadores.forEach(({ marker, viaje }) => {
          const pos = calcularPosicionSimulada(viaje);
          if (pos) marker.setLatLng(pos);
        });
      }, 4000);
    }
  }

  async function cargar() {
    try {
      const [perfil, pedidos] = await Promise.all([
        trailersysApiRequest("GET", "/mis-cargas/perfil"),
        trailersysApiRequest("GET", "/mis-cargas"),
      ]);
      $("clienteDashboardSaludo").textContent = `Hola, ${perfil.nombre}`;

      const viajesPorCarga = {};
      await Promise.all(pedidos.filter((c) => c.estado !== "Pendiente").map(async (c) => {
        try { viajesPorCarga[c.id] = await trailersysApiRequest("GET", `/mis-cargas/${c.id}/viaje`); }
        catch { viajesPorCarga[c.id] = null; }
      }));

      const reclamosAbiertos = Object.values(viajesPorCarga).filter((v) => v?.estadoReclamoCliente === "ABIERTO").length;
      renderStats(pedidos, reclamosAbiertos);
      renderDonut(pedidos);
      renderTendencia(pedidos);

      const enCurso = pedidos
        .filter((c) => viajesPorCarga[c.id]?.estado === "En Curso")
        .map((c) => ({ carga: c, viaje: viajesPorCarga[c.id] }));
      await renderMapaGrande(enCurso);
    } catch (e) {
      mostrarMapaVacio(e.message || "No se pudo cargar la información.");
    }
  }

  // Sin llamada a cargar() aca afuera (a diferencia de conductor-
  // dashboard.js): navigation.js siempre dispara "dashboard" una vez al
  // cargar la pagina (activateModule("dashboard") al final de su propio
  // IIFE), asi que agregar una segunda llamada eager aca solo produce dos
  // ejecuciones de cargar() solapadas - inofensivo en conductor-
  // dashboard.js (no construye nada con estado async), pero acá las dos
  // construcciones concurrentes del mapa grande (cada una con su propio
  // "await" a OSRM) pisaban el mapaInstance/marcadores compartido de la
  // otra a medio construir, dejando un mapa con el zoom mal calculado.
  window.addEventListener("trailersys:module-activated", (e) => {
    if (e.detail?.module === "dashboard") cargar();
    else destruirMapa();
  });
})();
