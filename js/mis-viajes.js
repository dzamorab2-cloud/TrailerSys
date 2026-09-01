(function () {
  const session = trailersysGetSession();
  if (session?.role !== "conductor") return;

  const ESTADO_BADGE = {
    Programado: "badge-info",
    "En Curso": "badge-warning",
    Finalizado: "badge-success",
    Cancelado: "badge-danger",
  };

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // Convierte la "ruta" del backend (origenLat/origenLng/... + path como
  // {lat,lng}) a la forma que espera Leaflet, igual que viajes.js/seguimiento.js.
  function fromApiRuta(rutaDto) {
    if (!rutaDto) return null;
    return {
      origenCoords: { lat: rutaDto.origenLat, lng: rutaDto.origenLng },
      destinoCoords: { lat: rutaDto.destinoLat, lng: rutaDto.destinoLng },
      distanciaKm: rutaDto.distanciaKm,
      duracionMin: rutaDto.duracionMin,
      path: rutaDto.path ? rutaDto.path.map((p) => [p.lat, p.lng]) : null,
    };
  }

  let viajesCache = [];
  let currentPage = 0;
  let pageMeta = null;
  let leafletMapInstance = null;
  let viajeActualId = null;

  const grid = $("misViajesGrid");
  const emptyState = $("misViajesEmptyState");
  const emptyTitle = $("misViajesEmptyTitle");
  const emptyText = $("misViajesEmptyText");
  const resultsCount = $("misViajesResultsCount");
  const inputBuscar = $("misViajesBuscar");
  const filtroEstado = $("misViajesEstadoFiltro");

  const detalleOverlay = $("misViajesDetalleOverlay");
  const detalleTitle = $("misViajesDetalleTitle");
  const detalleMeta = $("misViajesDetalleMeta");
  const resumen = $("misViajesResumen");
  const meta = $("misViajesMeta");
  const mapaContainer = $("misViajesMapaContainer");
  const reporteEntrega = $("misViajesReporteEntrega");

  function eta(viaje) {
    if (!viaje.ruta || !viaje.fechaSalida) return null;
    const salida = new Date(viaje.fechaSalida);
    if (Number.isNaN(salida.getTime())) return null;
    return new Date(salida.getTime() + viaje.ruta.duracionMin * 60000);
  }

  function renderCard(viaje) {
    const badgeClass = ESTADO_BADGE[viaje.estado] || "badge-neutral";
    const distanciaLabel = viaje.estado === "Finalizado" ? "recorridos" : "por recorrer";
    const distanciaText = viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km ${distanciaLabel}` : "Ruta no calculada";
    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-signpost-split"></i>
          <div class="item-banner-title">
            <div class="item-title">${esc(viaje.origen)} → ${esc(viaje.destino)}</div>
            <div class="item-subtitle">${esc(viaje.vehiculoPlaca)} · ${esc(viaje.clienteNombre)}</div>
          </div>
        </div>
        <div class="item-body">
          <div class="item-meta">
            <span class="badge ${badgeClass}">${esc(viaje.estado)}</span>
            <span><i class="bi bi-signpost"></i>${distanciaText}</span>
            <span><i class="bi bi-clock"></i>${trailersysFormatDateTime(viaje.fechaSalida)}</span>
          </div>
          <div class="item-actions">
            <button type="button" class="btn btn-ghost btn-block" data-action="detalle" data-id="${viaje.id}">
              <i class="bi bi-eye"></i> Ver detalle
            </button>
          </div>
        </div>
      </article>`;
  }

  function actualizarKpis(resumenData) {
    if (!resumenData) return;
    $("misViajesKpiProgramados").textContent = resumenData.viajesProgramados;
    $("misViajesKpiEnCurso").textContent = resumenData.viajesEnCurso;
    $("misViajesKpiFinalizados").textContent = resumenData.viajesFinalizados;
    $("misViajesKpiKm").textContent = Math.round(resumenData.kmRecorridos).toLocaleString("es-EC");
  }

  async function cargarResumen() {
    try {
      actualizarKpis(await trailersysApiRequest("GET", "/mis-viajes/resumen"));
    } catch {
      // Las tarjetas KPI se quedan en 0 si no se pudo cargar el resumen;
      // el listado de abajo sigue funcionando igual.
    }
  }

  async function render() {
    let pagina;
    try {
      const params = new URLSearchParams({ page: currentPage, size: 24, search: inputBuscar.value.trim() });
      if (filtroEstado.value) params.set("estado", filtroEstado.value);
      pagina = await trailersysApiRequest("GET", `/mis-viajes?${params}`);
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      trailersysRenderPager(resultsCount, null);
      emptyTitle.textContent = "No se pudo cargar tus viajes";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    pageMeta = pagina;
    const viajes = pagina.content;
    viajes.forEach((v) => { v.ruta = fromApiRuta(v.ruta); });
    viajesCache = viajes;

    if (viajes.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      trailersysRenderPager(resultsCount, null);
      const hayFiltro = Boolean(inputBuscar.value.trim() || filtroEstado.value);
      emptyTitle.textContent = hayFiltro ? "Sin resultados" : "Todavía no tienes viajes asignados";
      emptyText.textContent = hayFiltro
        ? "Ningún viaje coincide con la búsqueda o el filtro aplicado."
        : "Cuando se te asigne un viaje, aparecerá aquí.";
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${Number(pagina.totalElements).toLocaleString("es-EC")} viaje${pagina.totalElements === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pagina, (page) => { currentPage = page; render(); });
    grid.innerHTML = viajes.map(renderCard).join("");
  }

  function renderResumen(viaje) {
    const distanciaLabel = viaje.estado === "Finalizado" ? "Distancia recorrida" : "Distancia por recorrer";
    const distanciaText = viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km` : "—";
    const duracionText = viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "—";
    const salidaText = viaje.fechaSalida ? trailersysFormatDateTime(viaje.fechaSalida) : "—";
    const etaDate = eta(viaje);
    const etaText = etaDate ? trailersysFormatDateTime(etaDate) : "—";
    resumen.innerHTML = `
      <div class="route-stat"><div class="route-stat-label">${distanciaLabel}</div><div class="route-stat-value">${distanciaText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Duración estimada</div><div class="route-stat-value">${duracionText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Salida</div><div class="route-stat-value">${salidaText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Llegada estimada (ETA)</div><div class="route-stat-value">${etaText}</div></div>
    `;
  }

  function renderReporteEntrega(viaje) {
    if (viaje.estado !== "En Curso" || viaje.entregaConfirmada) {
      reporteEntrega.hidden = true;
      reporteEntrega.innerHTML = "";
      return;
    }
    reporteEntrega.hidden = false;
    reporteEntrega.innerHTML = `<div class="delivery-report-actions"><button type="button" class="btn btn-primary" data-action="confirmar-entrega"><i class="bi bi-flag-fill"></i> Confirmar llegada</button></div>`;
  }

  reporteEntrega.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action='confirmar-entrega']");
    if (!button || !viajeActualId) return;
    const observacion = window.prompt("Observación de la llegada (opcional):", "");
    if (observacion === null) return;
    button.disabled = true;
    try {
      await trailersysApiRequest("POST", `/viajes/${viajeActualId}/confirmar-entrega`, { observacion: observacion.trim() });
      await Promise.all([render(), cargarResumen()]);
      const actualizado = viajesCache.find((v) => String(v.id) === String(viajeActualId));
      if (actualizado) { renderResumen(actualizado); renderReporteEntrega(actualizado); }
    } catch (error) {
      alert(error.message || "No se pudo confirmar la llegada.");
    } finally {
      button.disabled = false;
    }
  });

  function destroyLeafletMap() {
    if (leafletMapInstance) { leafletMapInstance.remove(); leafletMapInstance = null; }
  }

  function showMapaPlaceholder(mensaje) {
    mapaContainer.innerHTML = `<div class="route-map-placeholder"><i class="bi bi-map"></i><p>${esc(mensaje)}</p></div>`;
  }

  async function renderMapa(viaje) {
    destroyLeafletMap();
    mapaContainer.innerHTML = "";
    if (typeof L === "undefined") {
      showMapaPlaceholder("No se pudo cargar el mapa. Verifica la conexión a internet y recarga la página.");
      return;
    }
    if (!viaje.ruta) {
      showMapaPlaceholder("Este viaje todavía no tiene una ruta calculada.");
      return;
    }
    let path = viaje.ruta.path;
    if (!path) {
      const ruta = await trailersysGetRoute(viaje.ruta.origenCoords, viaje.ruta.destinoCoords);
      if (ruta) path = ruta.path;
      viaje.ruta.path = path;
    }
    leafletMapInstance = L.map(mapaContainer, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", { attribution: "&copy; colaboradores de OpenStreetMap", maxZoom: 18 }).addTo(leafletMapInstance);
    const origenLatLng = [viaje.ruta.origenCoords.lat, viaje.ruta.origenCoords.lng];
    const destinoLatLng = [viaje.ruta.destinoCoords.lat, viaje.ruta.destinoCoords.lng];
    L.marker(origenLatLng).addTo(leafletMapInstance).bindPopup(`Origen: ${esc(viaje.origen)}`);
    L.marker(destinoLatLng).addTo(leafletMapInstance).bindPopup(`Destino: ${esc(viaje.destino)}`);
    if (path && path.length) {
      const polyline = L.polyline(path, { color: "#f2874b", weight: 4 }).addTo(leafletMapInstance);
      leafletMapInstance.fitBounds(polyline.getBounds(), { padding: [24, 24] });
    } else {
      leafletMapInstance.fitBounds(L.latLngBounds([origenLatLng, destinoLatLng]), { padding: [24, 24] });
    }
    setTimeout(() => { if (leafletMapInstance) leafletMapInstance.invalidateSize(); }, 200);
  }

  function formatPesoDoble(kg) {
    const kilos = Number(kg) || 0;
    return `${kilos.toLocaleString("es-EC")} kg / ${(kilos * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  }

  // Misma logica que showViajeGuide() en js/viajes.js: los datos de
  // vehiculo/carga/cliente ya vienen denormalizados en el propio viaje, asi
  // que la guia sale completa sin depender de otros endpoints restringidos.
  function mostrarGuia(viaje) {
    trailersysShowGuide({
      tipo: "Viaje", id: viaje.id, estado: viaje.estado,
      secciones: [
        { titulo: "Vehículo asignado", icono: "bi-truck", campos: [
          ["Placa", viaje.vehiculoPlaca], ["Marca", viaje.vehiculoMarca], ["Modelo", viaje.vehiculoModelo],
          ["Tipo", viaje.vehiculoTipo], ["Año", viaje.vehiculoAnio], ["Color", viaje.vehiculoColor],
          ["Capacidad", viaje.vehiculoCapacidad != null ? formatPesoDoble(viaje.vehiculoCapacidad) : "—"]
        ] },
        { titulo: "Carga transportada", icono: "bi-box-seam", campos: [
          ["Mercancía", viaje.cargaDescripcion || "Viaje sin carga asociada"],
          ["Tipo", viaje.cargaTipo], ["Peso", viaje.cargaPeso != null ? formatPesoDoble(viaje.cargaPeso) : "—"],
          ["Cliente", viaje.clienteNombre]
        ] },
        { titulo: "Ruta y despacho", icono: "bi-signpost-split", campos: [
          ["Origen", viaje.origen], ["Destino", viaje.destino],
          ["Fecha de salida", trailersysFormatDateTime(viaje.fechaSalida)],
          ["Distancia estimada", viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km` : "Sin ruta calculada"],
          ["Duración estimada", viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "Sin ruta calculada"],
          ["Observaciones", viaje.observaciones || "Sin observaciones"]
        ] }
      ]
    });
  }

  function abrirDetalle(viaje) {
    viajeActualId = viaje.id;
    detalleTitle.textContent = `${viaje.origen} → ${viaje.destino}`;
    // Cliente/carga ya no se repiten aqui: quedan en el detalle completo
    // (trailersysRenderViajeSecciones) justo debajo del resumen de ruta.
    detalleMeta.innerHTML = `<span class="badge ${ESTADO_BADGE[viaje.estado] || "badge-neutral"}">${esc(viaje.estado)}</span>`;
    renderResumen(viaje);
    trailersysRenderViajeSecciones(meta, viaje);
    renderReporteEntrega(viaje);
    trailersysOpenModal(detalleOverlay);
    renderMapa(viaje);
  }

  async function abrirDetallePorId(id) {
    try {
      const viaje = await trailersysApiRequest("GET", `/mis-viajes/${id}`);
      viaje.ruta = fromApiRuta(viaje.ruta);
      if (!viajesCache.some((v) => String(v.id) === String(id))) viajesCache.push(viaje);
      abrirDetalle(viaje);
    } catch (error) {
      alert(error.message || "No se pudo cargar el viaje.");
    }
  }

  function closeDetalle() {
    trailersysCloseModal(detalleOverlay);
    destroyLeafletMap();
    viajeActualId = null;
  }

  $("misViajesDetalleClose").addEventListener("click", closeDetalle);
  $("misViajesDetalleCerrarBtn").addEventListener("click", closeDetalle);
  detalleOverlay.addEventListener("click", (event) => { if (event.target === detalleOverlay) closeDetalle(); });
  $("misViajesVerGuia").addEventListener("click", () => {
    const viaje = viajesCache.find((v) => String(v.id) === String(viajeActualId));
    if (viaje) mostrarGuia(viaje);
  });

  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action='detalle']");
    if (!button) return;
    const viaje = viajesCache.find((v) => String(v.id) === button.dataset.id);
    if (viaje) abrirDetalle(viaje);
  });

  let buscarTimer;
  inputBuscar.addEventListener("input", () => {
    clearTimeout(buscarTimer);
    buscarTimer = setTimeout(() => { currentPage = 0; render(); }, 300);
  });
  filtroEstado.addEventListener("change", () => { currentPage = 0; render(); });

  // Expuesto para que la alerta de viaje asignado del Dashboard del
  // conductor (js/conductor-dashboard.js) pueda saltar directo al detalle,
  // sin duplicar la logica de mapa/guia/confirmar llegada aqui construida.
  window.trailersysAbrirMiViaje = (id) => {
    const viaje = viajesCache.find((v) => String(v.id) === String(id));
    if (viaje) abrirDetalle(viaje);
    else abrirDetallePorId(id);
  };

  render();
  cargarResumen();
})();
