(function () {
  const MODULE_KEY = "seguimiento";
  const ESTADO_BADGE = {
    Programado: "badge-info",
    "En Curso": "badge-warning",
    Finalizado: "badge-success",
    Cancelado: "badge-danger",
  };
  const TIPO_ICON = {
    Salida: "bi-box-arrow-right",
    Parada: "bi-pause-circle",
    Retraso: "bi-alarm",
    Incidente: "bi-exclamation-triangle",
    Llegada: "bi-flag-fill",
    Otro: "bi-info-circle",
  };

  // Convierte la "ruta" que devuelve /api/viajes (origenLat/origenLng/... +
  // path como {lat,lng}) a la forma que espera Leaflet aquí mismo
  // (origenCoords/destinoCoords + path como [lat,lng]).
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

  // Caches del ultimo listado cargado desde la API.
  let viajesCache = [];
  let eventosCache = [];
  // Total de viajes SIN busqueda ni filtro de estado aplicados - se
  // actualiza solo cuando se refresca sin ninguno de los dos, para poder
  // distinguir en render() "no hay ningun viaje todavia" (formulario vacio,
  // 0 en total) de "ninguno coincide con lo que buscaste" (0 filtrados,
  // pero el sistema si tiene viajes).
  let totalSinFiltro = 0;

  // --- Referencias del DOM ---
  const alertasList = document.getElementById("alertasList");

  const inputBuscar = document.getElementById("seguimientoBuscar");
  const filtroEstado = document.getElementById("seguimientoFiltroEstado");
  const grid = document.getElementById("seguimientoGrid");
  const emptyState = document.getElementById("seguimientoEmptyState");
  const emptyTitle = document.getElementById("seguimientoEmptyTitle");
  const emptyText = document.getElementById("seguimientoEmptyText");
  const resultsCount = document.getElementById("seguimientoResultsCount");

  const modalOverlay = document.getElementById("seguimientoModalOverlay");
  const modalTitle = document.getElementById("seguimientoModalTitle");
  const resumen = document.getElementById("seguimientoResumen");
  const mapaContainer = document.getElementById("seguimientoMapaContainer");
  const reporteEntrega = document.getElementById("seguimientoReporteEntrega");
  const btnCerrarModal = document.getElementById("seguimientoModalClose");
  const btnCerrarBtn = document.getElementById("seguimientoCerrarBtn");

  const eventoForm = document.getElementById("seguimientoEventoForm");
  const inputFecha = document.getElementById("seguimientoFecha");
  const selectEvento = document.getElementById("seguimientoEvento");
  const inputUbicacion = document.getElementById("seguimientoUbicacion");
  const inputObservacion = document.getElementById("seguimientoObservacion");
  const timelineContainer = document.getElementById("seguimientoTimeline");

  let session = null;
  let leafletMapInstance = null;
  let viajeActualId = null;
  let vehiculoMarker = null;
  let posicionIntervalId = null;

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

  function nowForInput() {
    const d = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
  }

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  // --- Alertas operativas (calculadas en el backend) ---
  async function renderAlertas() {
    let alerts;
    try {
      alerts = await trailersysApiRequest("GET", "/seguimiento/alertas");
    } catch {
      alertasList.innerHTML = `<div class="alerts-empty"><i class="bi bi-exclamation-circle"></i>No se pudieron cargar las alertas.</div>`;
      return;
    }
    if (!alerts.length) {
      alertasList.innerHTML = `<div class="alerts-empty"><i class="bi bi-check-circle"></i>No hay alertas activas. Todo está en orden.</div>`;
      return;
    }
    const renderAlerta = (a) => `<div class="alert-item level-${a.nivel}"><i class="bi ${a.icono}"></i><div class="alert-text">${escapeHtml(a.texto)}</div></div>`;
    const visibles = alerts.slice(0, 5);
    const adicionales = alerts.slice(5);
    alertasList.innerHTML = visibles.map(renderAlerta).join("") + (adicionales.length
      ? `<details class="alerts-more"><summary>Ver ${adicionales.length} alertas adicionales</summary>${adicionales.map(renderAlerta).join("")}</details>`
      : "");
  }

  // --- Tarjetas de viajes ---
  function renderCard(viaje) {
    const badgeClass = ESTADO_BADGE[viaje.estado] || "badge-neutral";
    const eventos = eventosCache
      .filter((e) => String(e.viajeId) === String(viaje.id))
      .sort((a, b) => (a.fechaHora < b.fechaHora ? 1 : -1));
    const ultimo = eventos[0];

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-geo-alt"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(viaje.origen)} → ${escapeHtml(viaje.destino)}</div>
            <div class="item-subtitle">${escapeHtml(viaje.vehiculoPlaca)} · ${escapeHtml(viaje.conductorNombres)}</div>
          </div>
        </div>
        <div class="item-body">
          <div class="item-meta">
            <span class="badge ${badgeClass}">${escapeHtml(viaje.estado)}</span>
            <span><i class="bi bi-list-check"></i>${eventos.length} evento${eventos.length === 1 ? "" : "s"}</span>
          </div>
          ${ultimo
            ? `<div class="item-route"><i class="bi ${TIPO_ICON[ultimo.evento] || "bi-flag"}"></i><span>${escapeHtml(ultimo.evento)} · ${escapeHtml(ultimo.ubicacion)}</span></div>`
            : `<p class="item-observations">Sin eventos registrados todavía.</p>`}
          <div class="item-actions">
            <button type="button" class="btn btn-ghost btn-block" data-action="detalle" data-id="${viaje.id}">
              <i class="bi bi-geo-alt"></i>
              Ver seguimiento
            </button>
          </div>
        </div>
      </article>`;
  }

  // Trae viajes + eventos y actualiza los caches; separado de render() para
  // poder refrescar solo el modal de detalle (actualizacion periodica del
  // vehiculo simulado) sin repintar toda la grilla de tarjetas.
  async function refrescarDatos() {
    // search/estado se mandan al backend (igual que Vehiculos, Cargas,
    // etc.): antes esto SIEMPRE pedia la pagina 0 sin filtro alguno y
    // la busqueda/el filtro de estado se aplicaban despues en el
    // navegador solo sobre esos 24 viajes - con 250.000+ viajes en la
    // base, buscar "SYN-049869" (que existe pero no esta entre los 24
    // mas recientes) daba "Sin resultados" aunque el viaje si existiera.
    let pagina;
    try {
      pagina = await trailersysPagedRequest("viajes", 0, 24, {
        search: inputBuscar.value.trim(),
        estado: filtroEstado.value,
      });
    } catch (error) {
      return { ok: false, error };
    }
    const viajes = pagina.content;
    viajes.forEach((viaje) => {
      viaje.ruta = fromApiRuta(viaje.ruta);
    });
    viajesCache = viajes;
    totalSinFiltro = inputBuscar.value.trim() || filtroEstado.value ? totalSinFiltro : pagina.totalElements;

    // OJO: antes esto traia "los ultimos 100 eventos de TODO el sistema"
    // (/paginas/eventos, sin filtro por viaje) y de ahi se filtraba en el
    // navegador por viajeId - con miles de viajes y cientos de miles de
    // eventos en la base, casi ningun viaje individual tenia alguno de sus
    // propios eventos entre esos 100 globales, asi que la tarjeta y el
    // detalle mostraban "sin eventos" para viajes que si tenian historial.
    // Se pide, en paralelo, SOLO los eventos de los viajes que estan
    // visibles en esta pagina (maximo 24), usando el endpoint que ya existe
    // filtrado por viaje (/seguimiento/eventos?viajeId=), asi el conteo y el
    // ultimo evento de cada tarjeta son siempre los reales de ESE viaje.
    try {
      const porViaje = await Promise.all(
        viajes.map((v) => trailersysApiRequest("GET", `/seguimiento/eventos?viajeId=${v.id}`).catch(() => []))
      );
      eventosCache = porViaje.flat();
    } catch {
      eventosCache = [];
    }
    return { ok: true };
  }

  async function render() {
    renderAlertas();

    const resultado = await refrescarDatos();
    if (!resultado.ok) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los viajes";
      emptyText.textContent = resultado.error?.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    // viajesCache ya viene filtrado por el backend (refrescarDatos manda
    // search/estado en la propia peticion), asi que aqui ya no hace falta
    // volver a filtrar en el navegador.
    const filtrados = viajesCache;
    const hayFiltroActivo = Boolean(inputBuscar.value.trim() || filtroEstado.value);

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (!hayFiltroActivo && totalSinFiltro === 0) {
        emptyTitle.textContent = "Todavía no hay viajes para seguir";
        emptyText.textContent = "Los viajes registrados en el módulo Viajes aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún viaje coincide con la búsqueda o el filtro aplicado.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = hayFiltroActivo
      ? `${filtrados.length} viaje${filtrados.length === 1 ? "" : "s"} encontrado${filtrados.length === 1 ? "" : "s"}`
      : `${filtrados.length} de ${totalSinFiltro.toLocaleString("es-EC")} viajes`;
    grid.innerHTML = filtrados.map((v) => renderCard(v)).join("");
  }

  [inputBuscar, filtroEstado].forEach((el) => {
    el.addEventListener("input", render);
    el.addEventListener("change", render);
  });

  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action='detalle']");
    if (!button) return;
    const viaje = viajesCache.find((v) => String(v.id) === button.dataset.id);
    if (viaje) openDetailModal(viaje);
  });

  // --- Modal de detalle: resumen + mapa + eventos ---
  function renderResumen(viaje) {
    const distanciaLabel = viaje.estado === "Finalizado" ? "Distancia recorrida" : "Distancia por recorrer";
    const distanciaText = viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km` : "—";
    const duracionText = viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "—";
    const salidaText = viaje.fechaSalida ? trailersysFormatDateTime(viaje.fechaSalida) : "—";
    let etaText = "—";
    if (viaje.ruta && viaje.fechaSalida) {
      const salida = new Date(viaje.fechaSalida);
      if (!Number.isNaN(salida.getTime())) {
        etaText = trailersysFormatDateTime(new Date(salida.getTime() + viaje.ruta.duracionMin * 60000));
      }
    }

    resumen.innerHTML = `
      <div class="route-stat"><div class="route-stat-label">${distanciaLabel}</div><div class="route-stat-value">${distanciaText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Duración estimada</div><div class="route-stat-value">${duracionText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Salida</div><div class="route-stat-value">${salidaText}</div></div>
      <div class="route-stat"><div class="route-stat-label">Llegada estimada (ETA)</div><div class="route-stat-value">${etaText}</div></div>
    `;
  }

  function renderReporteEntrega(viaje) {
    const bloques = [];

    if (viaje.entregaConfirmada) {
      bloques.push(`
        <div class="delivery-report-block">
          <span class="badge badge-success"><i class="bi bi-check-circle"></i> Llegada confirmada</span>
          <div class="delivery-report-meta">${trailersysFormatDateTime(viaje.fechaEntregaConfirmada)} · por ${escapeHtml(viaje.confirmadoPor || "—")}</div>
          ${viaje.observacionEntrega ? `<div>${escapeHtml(viaje.observacionEntrega)}</div>` : ""}
        </div>`);
    }

    if (viaje.entregaValidada) {
      bloques.push(`
        <div class="delivery-report-block">
          <span class="badge badge-info"><i class="bi bi-patch-check"></i> Validada por supervisor</span>
          <div class="delivery-report-meta">${trailersysFormatDateTime(viaje.fechaValidacionEntrega)} · por ${escapeHtml(viaje.validadoPor || "—")}</div>
          ${viaje.observacionValidacion ? `<div>${escapeHtml(viaje.observacionValidacion)}</div>` : ""}
        </div>`);
    }

    const acciones = [];
    if (session?.role === "conductor" && viaje.estado === "En Curso" && !viaje.entregaConfirmada) {
      acciones.push(`<button type="button" class="btn btn-primary" data-action="confirmar-entrega"><i class="bi bi-flag-fill"></i> Confirmar llegada</button>`);
    }
    if (session?.role === "supervisor" && viaje.entregaConfirmada && !viaje.entregaValidada) {
      acciones.push(`<button type="button" class="btn btn-primary" data-action="validar-entrega"><i class="bi bi-patch-check"></i> Validar entrega</button>`);
    }
    if (acciones.length) {
      bloques.push(`<div class="delivery-report-actions">${acciones.join("")}</div>`);
    }

    if (!bloques.length) {
      reporteEntrega.hidden = true;
      reporteEntrega.innerHTML = "";
      return;
    }

    reporteEntrega.hidden = false;
    reporteEntrega.innerHTML = bloques.join("");
  }

  reporteEntrega.addEventListener("click", async (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button || !viajeActualId) return;

    const accion = button.dataset.action;
    const observacion = window.prompt(
      accion === "confirmar-entrega"
        ? "Observación de la llegada (opcional):"
        : "Observación de la validación (opcional):",
      ""
    );
    if (observacion === null) return;

    button.disabled = true;
    try {
      await trailersysApiRequest(
        "POST",
        `/viajes/${viajeActualId}/${accion}`,
        { observacion: observacion.trim() }
      );
      await render();
      const viajeActualizado = viajesCache.find((v) => String(v.id) === String(viajeActualId));
      if (viajeActualizado) {
        renderResumen(viajeActualizado);
        renderReporteEntrega(viajeActualizado);
      }
    } catch (error) {
      alert(error.message || "No se pudo completar la acción.");
    } finally {
      button.disabled = false;
    }
  });

  function destroyLeafletMap() {
    if (leafletMapInstance) {
      leafletMapInstance.remove();
      leafletMapInstance = null;
    }
    vehiculoMarker = null;
  }

  const VEHICULO_ICON = L.divIcon({
    className: "vehicle-marker",
    html: '<i class="bi bi-truck"></i>',
    iconSize: [30, 30],
    iconAnchor: [15, 15],
  });

  // Posicion simulada del vehiculo sobre viaje.ruta.path, interpolando
  // segun cuanto tiempo real transcurrio desde fechaSalida frente a la
  // duracion estimada de la ruta. No hay telemetria real: es la misma idea
  // que ya aplica ViajeSimulacionService en el backend para las paradas
  // automaticas, pero calculada aca para animar el marcador.
  function calcularPosicionSimulada(viaje) {
    const path = viaje.ruta?.path;
    if (!path || path.length < 2 || !viaje.fechaSalida || !viaje.ruta.duracionMin) {
      return null;
    }
    const salida = new Date(viaje.fechaSalida).getTime();
    if (Number.isNaN(salida)) return null;
    const duracionMs = viaje.ruta.duracionMin * 60000;
    if (duracionMs <= 0) return null;

    const progreso = Math.min(1, Math.max(0, (Date.now() - salida) / duracionMs));
    const posicionExacta = progreso * (path.length - 1);
    const indiceBase = Math.floor(posicionExacta);
    const indiceSiguiente = Math.min(path.length - 1, indiceBase + 1);
    const fraccionLocal = posicionExacta - indiceBase;

    const p1 = path[indiceBase];
    const p2 = path[indiceSiguiente];
    return [
      p1[0] + (p2[0] - p1[0]) * fraccionLocal,
      p1[1] + (p2[1] - p1[1]) * fraccionLocal,
    ];
  }

  function actualizarMarcadorVehiculo(viaje) {
    if (!leafletMapInstance) return;

    if (viaje.estado !== "En Curso") {
      if (vehiculoMarker) {
        leafletMapInstance.removeLayer(vehiculoMarker);
        vehiculoMarker = null;
      }
      return;
    }

    const posicion = calcularPosicionSimulada(viaje);
    if (!posicion) return;

    if (vehiculoMarker) {
      vehiculoMarker.setLatLng(posicion);
    } else {
      vehiculoMarker = L.marker(posicion, { icon: VEHICULO_ICON })
        .addTo(leafletMapInstance)
        .bindPopup("Posición estimada del vehículo (simulada)");
    }
  }

  function showMapaPlaceholder(mensaje) {
    mapaContainer.innerHTML = `
      <div class="route-map-placeholder">
        <i class="bi bi-map"></i>
        <p>${escapeHtml(mensaje)}</p>
      </div>`;
  }

  async function renderMapa(viaje) {
    destroyLeafletMap();
    mapaContainer.innerHTML = "";

    if (typeof L === "undefined") {
      showMapaPlaceholder("No se pudo cargar el mapa. Verifica la conexión a internet y recarga la página.");
      return;
    }

    if (!viaje.ruta) {
      showMapaPlaceholder('Este viaje todavía no tiene una ruta calculada. Ve al módulo Viajes para calcularla.');
      return;
    }

    let path = viaje.ruta.path;
    if (!path) {
      const ruta = await trailersysGetRoute(viaje.ruta.origenCoords, viaje.ruta.destinoCoords);
      if (ruta) path = ruta.path;
      viaje.ruta.path = path;
    }

    leafletMapInstance = L.map(mapaContainer, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; colaboradores de OpenStreetMap",
      maxZoom: 18,
    }).addTo(leafletMapInstance);

    const origenLatLng = [viaje.ruta.origenCoords.lat, viaje.ruta.origenCoords.lng];
    const destinoLatLng = [viaje.ruta.destinoCoords.lat, viaje.ruta.destinoCoords.lng];

    L.marker(origenLatLng).addTo(leafletMapInstance).bindPopup(`Origen: ${escapeHtml(viaje.origen)}`);
    L.marker(destinoLatLng).addTo(leafletMapInstance).bindPopup(`Destino: ${escapeHtml(viaje.destino)}`);

    if (path && path.length) {
      const polyline = L.polyline(path, { color: "#f2874b", weight: 4 }).addTo(leafletMapInstance);
      leafletMapInstance.fitBounds(polyline.getBounds(), { padding: [24, 24] });
    } else {
      leafletMapInstance.fitBounds(L.latLngBounds([origenLatLng, destinoLatLng]), { padding: [24, 24] });
    }

    actualizarMarcadorVehiculo(viaje);

    setTimeout(() => {
      if (leafletMapInstance) leafletMapInstance.invalidateSize();
    }, 200);
  }

  async function renderTimeline(viajeId) {
    const canManage = trailersysCanManage(session, MODULE_KEY);
    // Se pide directo al endpoint filtrado por viaje (no eventosCache, que
    // solo trae los viajes de la pagina actual) para que el detalle de UN
    // viaje muestre siempre TODO su historial, sin importar cuantos eventos
    // tenga ni si ese viaje sigue visible en la lista de fondo.
    let eventos;
    try {
      eventos = (await trailersysApiRequest("GET", `/seguimiento/eventos?viajeId=${viajeId}`))
        .sort((a, b) => (a.fechaHora < b.fechaHora ? 1 : -1));
    } catch {
      timelineContainer.innerHTML = `<div class="events-empty">No se pudo cargar el historial de este viaje.</div>`;
      return;
    }

    if (!eventos.length) {
      timelineContainer.innerHTML = `<div class="events-empty">Todavía no hay eventos registrados para este viaje.</div>`;
      return;
    }

    timelineContainer.innerHTML = eventos
      .map(
        (e) => `
      <div class="event-item">
        <div class="event-icon"><i class="bi ${TIPO_ICON[e.evento] || "bi-flag"}"></i></div>
        <div class="event-body">
          <div class="event-top-row">
            <span class="event-type">${escapeHtml(e.evento)}</span>
            <span class="event-date">${trailersysFormatDateTime(e.fechaHora)}</span>
          </div>
          <div class="event-location"><i class="bi bi-geo-alt"></i> ${escapeHtml(e.ubicacion)}</div>
          ${e.observacion ? `<div class="event-observation">${escapeHtml(e.observacion)}</div>` : ""}
        </div>
        ${canManage ? `<button type="button" class="event-delete" data-id="${e.id}" title="Eliminar evento"><i class="bi bi-trash3"></i></button>` : ""}
      </div>`
      )
      .join("");
  }

  timelineContainer.addEventListener("click", (event) => {
    const button = event.target.closest(".event-delete");
    if (!button) return;
    const id = button.dataset.id;
    trailersysConfirm({
      title: "Eliminar evento",
      text: "¿Seguro que deseas eliminar este evento de seguimiento?",
      acceptLabel: "Eliminar",
      onAccept: async () => {
        try {
          await trailersysApiRequest("DELETE", `/seguimiento/eventos/${id}`);
          eventosCache = eventosCache.filter((e) => String(e.id) !== id);
          await renderTimeline(viajeActualId);
          render();
        } catch (error) {
          alert(error.message || "No se pudo eliminar el evento.");
        }
      },
    });
  });

  // Mientras el modal esta abierto sobre un viaje En Curso, refresca datos
  // cada 10s: mueve el marcador simulado y muestra sin recargar la pagina
  // las paradas que ViajeSimulacionService vaya registrando en el backend.
  function detenerActualizacionPeriodica() {
    if (posicionIntervalId) {
      clearInterval(posicionIntervalId);
      posicionIntervalId = null;
    }
  }

  function iniciarActualizacionPeriodica() {
    detenerActualizacionPeriodica();
    posicionIntervalId = setInterval(async () => {
      if (!viajeActualId) return;
      const resultado = await refrescarDatos();
      if (!resultado.ok || !viajeActualId) return;

      const viaje = viajesCache.find((v) => String(v.id) === String(viajeActualId));
      if (!viaje) return;
      renderResumen(viaje);
      renderReporteEntrega(viaje);
      await renderTimeline(viaje.id);
      actualizarMarcadorVehiculo(viaje);
    }, 10000);
  }

  function openDetailModal(viaje) {
    viajeActualId = viaje.id;
    modalTitle.textContent = `${viaje.origen} → ${viaje.destino}`;

    const canManage = trailersysCanManage(session, MODULE_KEY);
    eventoForm.hidden = !canManage;
    eventoForm.reset();
    inputFecha.value = nowForInput();
    // Un evento de seguimiento (Salida, Llegada, Incidente...) describe algo
    // que ya esta pasando o ya paso; no tiene sentido registrar uno con
    // fecha futura, asi que se bloquea tambien a nivel de input nativo.
    inputFecha.max = nowForInput();
    setFieldError("fieldSeguimientoFecha", "");
    setFieldError("fieldSeguimientoUbicacion", "");

    renderResumen(viaje);
    renderReporteEntrega(viaje);
    renderTimeline(viaje.id);
    trailersysOpenModal(modalOverlay);
    renderMapa(viaje);
    iniciarActualizacionPeriodica();
  }

  function closeDetailModal() {
    trailersysCloseModal(modalOverlay);
    destroyLeafletMap();
    detenerActualizacionPeriodica();
    viajeActualId = null;
  }

  btnCerrarModal.addEventListener("click", closeDetailModal);
  btnCerrarBtn.addEventListener("click", closeDetailModal);
  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) closeDetailModal();
  });

  const submitEventoBtn = eventoForm.querySelector('button[type="submit"]');

  eventoForm.addEventListener("submit", async (event) => {
    event.preventDefault();

    let valid = true;
    if (!inputFecha.value) {
      setFieldError("fieldSeguimientoFecha", "La fecha y hora son obligatorias.");
      valid = false;
    } else if (new Date(inputFecha.value) > new Date()) {
      setFieldError("fieldSeguimientoFecha", "La fecha y hora no pueden ser futuras.");
      valid = false;
    } else {
      setFieldError("fieldSeguimientoFecha", "");
    }

    const ubicacion = inputUbicacion.value.trim();
    if (!ubicacion) {
      setFieldError("fieldSeguimientoUbicacion", "La ubicación es obligatoria.");
      valid = false;
    } else {
      setFieldError("fieldSeguimientoUbicacion", "");
    }

    if (!valid) return;

    submitEventoBtn.disabled = true;
    try {
      await trailersysApiRequest("POST", "/seguimiento/eventos", {
        viajeId: viajeActualId,
        fechaHora: inputFecha.value,
        evento: selectEvento.value,
        ubicacion,
        observacion: inputObservacion.value.trim(),
      });
      eventoForm.reset();
      inputFecha.value = nowForInput();
      inputFecha.max = nowForInput();
      // render() ya refresca eventosCache (para las tarjetas de la lista);
      // el timeline del modal abierto se trae aparte porque renderTimeline
      // pide directo el historial de ESTE viaje, no depende de esa cache.
      await renderTimeline(viajeActualId);
      await render();
    } catch (error) {
      alert(error.message || "No se pudo registrar el evento.");
    } finally {
      submitEventoBtn.disabled = false;
    }
  });

  session = trailersysGetSession();
  render();
})();
