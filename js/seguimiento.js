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
    alertasList.innerHTML = alerts
      .map((a) => `<div class="alert-item level-${a.nivel}"><i class="bi ${a.icono}"></i><div class="alert-text">${escapeHtml(a.texto)}</div></div>`)
      .join("");
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

  async function render() {
    renderAlertas();

    let viajes;
    try {
      viajes = await trailersysApiRequest("GET", "/viajes");
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los viajes";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    viajes.forEach((viaje) => {
      viaje.ruta = fromApiRuta(viaje.ruta);
    });
    viajesCache = viajes;

    try {
      eventosCache = await trailersysApiRequest("GET", "/seguimiento/eventos");
    } catch {
      eventosCache = [];
    }

    const search = inputBuscar.value.trim().toLowerCase();
    const estado = filtroEstado.value;

    const filtrados = viajes.filter((viaje) => {
      const haystack = [viaje.origen, viaje.destino, viaje.vehiculoPlaca, viaje.conductorNombres]
        .filter(Boolean)
        .join(" ")
        .toLowerCase();
      const matchesSearch = !search || haystack.includes(search);
      const matchesEstado = !estado || viaje.estado === estado;
      return matchesSearch && matchesEstado;
    });

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (viajes.length === 0) {
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
    resultsCount.textContent = `${filtrados.length} de ${viajes.length} viaje${viajes.length === 1 ? "" : "s"}`;
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

  function destroyLeafletMap() {
    if (leafletMapInstance) {
      leafletMapInstance.remove();
      leafletMapInstance = null;
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

    if (!viaje.ruta) {
      showMapaPlaceholder('Este viaje todavía no tiene una ruta calculada. Ve al módulo Viajes para calcularla.');
      return;
    }

    let path = viaje.ruta.path;
    if (!path) {
      const ruta = await trailersysGetRoute(viaje.ruta.origenCoords, viaje.ruta.destinoCoords);
      if (ruta) path = ruta.path;
    }

    leafletMapInstance = L.map(mapaContainer, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; colaboradores de OpenStreetMap",
      maxZoom: 18,
    }).addTo(leafletMapInstance);

    const origenLatLng = [viaje.ruta.origenCoords.lat, viaje.ruta.origenCoords.lng];
    const destinoLatLng = [viaje.ruta.destinoCoords.lat, viaje.ruta.destinoCoords.lng];

    L.marker(origenLatLng).addTo(leafletMapInstance).bindPopup(`Origen: ${viaje.origen}`);
    L.marker(destinoLatLng).addTo(leafletMapInstance).bindPopup(`Destino: ${viaje.destino}`);

    if (path && path.length) {
      const polyline = L.polyline(path, { color: "#f2874b", weight: 4 }).addTo(leafletMapInstance);
      leafletMapInstance.fitBounds(polyline.getBounds(), { padding: [24, 24] });
    } else {
      leafletMapInstance.fitBounds(L.latLngBounds([origenLatLng, destinoLatLng]), { padding: [24, 24] });
    }

    setTimeout(() => {
      if (leafletMapInstance) leafletMapInstance.invalidateSize();
    }, 200);
  }

  function renderTimeline(viajeId) {
    const canManage = trailersysCanManage(session, MODULE_KEY);
    const eventos = eventosCache
      .filter((e) => String(e.viajeId) === String(viajeId))
      .sort((a, b) => (a.fechaHora < b.fechaHora ? 1 : -1));

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
          renderTimeline(viajeActualId);
          render();
        } catch (error) {
          alert(error.message || "No se pudo eliminar el evento.");
        }
      },
    });
  });

  function openDetailModal(viaje) {
    viajeActualId = viaje.id;
    modalTitle.textContent = `${viaje.origen} → ${viaje.destino}`;

    const canManage = trailersysCanManage(session, MODULE_KEY);
    eventoForm.hidden = !canManage;
    eventoForm.reset();
    inputFecha.value = nowForInput();
    setFieldError("fieldSeguimientoFecha", "");
    setFieldError("fieldSeguimientoUbicacion", "");

    renderResumen(viaje);
    renderTimeline(viaje.id);
    trailersysOpenModal(modalOverlay);
    renderMapa(viaje);
  }

  function closeDetailModal() {
    trailersysCloseModal(modalOverlay);
    destroyLeafletMap();
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
      eventosCache = await trailersysApiRequest("GET", "/seguimiento/eventos");
      renderTimeline(viajeActualId);
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
