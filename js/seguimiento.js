(function () {
  const MODULE_KEY = "seguimiento";
  const COLLECTION = "seguimientos";
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

  trailersysSeedIfEmpty(COLLECTION, [
    {
      id: "seguimiento-seed-1",
      viajeId: "viaje-seed-2",
      vehiculoId: "vehiculo-seed-2",
      fechaHora: "2026-08-09T06:05",
      ubicacion: "Terminal de Ambato",
      evento: "Salida",
      observacion: "Salida registrada a tiempo.",
    },
    {
      id: "seguimiento-seed-2",
      viajeId: "viaje-seed-2",
      vehiculoId: "vehiculo-seed-2",
      fechaHora: "2026-08-09T06:40",
      ubicacion: "Km 15 vía Ambato - Riobamba",
      evento: "Parada",
      observacion: "Parada breve por control de carga.",
    },
  ]);

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

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
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

  // --- Alertas operativas (calculadas en vivo a partir de los demas modulos) ---
  function computeAlerts() {
    const alerts = [];
    const hoy = new Date();
    const conductores = trailersysList("conductores");
    const vehiculos = trailersysList("vehiculos");
    const viajes = trailersysList("viajes");
    const activos = viajes.filter((v) => v.estado === "Programado" || v.estado === "En Curso");

    activos.forEach((viaje) => {
      const conductor = conductores.find((c) => c.id === viaje.conductorId);
      if (conductor && conductor.licenciaVencimiento && conductor.licenciaVencimiento < todayIso()) {
        alerts.push({
          level: "danger",
          icon: "bi-person-x",
          text: `La licencia de ${conductor.nombres} está vencida (venció el ${conductor.licenciaVencimiento}) y tiene un viaje ${viaje.estado.toLowerCase()} de ${viaje.origen} a ${viaje.destino}.`,
        });
      }

      const vehiculo = vehiculos.find((v) => v.id === viaje.vehiculoId);
      if (vehiculo && (vehiculo.estado === "Mantenimiento" || vehiculo.estado === "Fuera de Servicio")) {
        alerts.push({
          level: "danger",
          icon: "bi-tools",
          text: `El vehículo ${vehiculo.placa} está en estado "${vehiculo.estado}" pero tiene un viaje ${viaje.estado.toLowerCase()} asignado (${viaje.origen} → ${viaje.destino}).`,
        });
      }
    });

    viajes
      .filter((v) => v.estado === "Programado" && v.fechaSalida && new Date(v.fechaSalida) < hoy)
      .forEach((viaje) => {
        alerts.push({
          level: "warning",
          icon: "bi-alarm",
          text: `El viaje de ${viaje.origen} a ${viaje.destino} sigue "Programado" pero su salida (${trailersysFormatDateTime(viaje.fechaSalida)}) ya pasó.`,
        });
      });

    viajes
      .filter((v) => v.estado === "En Curso" && !v.ruta)
      .forEach((viaje) => {
        alerts.push({
          level: "warning",
          icon: "bi-map",
          text: `El viaje de ${viaje.origen} a ${viaje.destino} está "En Curso" pero no tiene una ruta calculada todavía.`,
        });
      });

    return alerts;
  }

  function renderAlertas() {
    const alerts = computeAlerts();
    if (!alerts.length) {
      alertasList.innerHTML = `<div class="alerts-empty"><i class="bi bi-check-circle"></i>No hay alertas activas. Todo está en orden.</div>`;
      return;
    }
    alertasList.innerHTML = alerts
      .map((a) => `<div class="alert-item level-${a.level}"><i class="bi ${a.icon}"></i><div class="alert-text">${escapeHtml(a.text)}</div></div>`)
      .join("");
  }

  // --- Tarjetas de viajes ---
  function renderCard(viaje) {
    const badgeClass = ESTADO_BADGE[viaje.estado] || "badge-neutral";
    const vehiculo = trailersysList("vehiculos").find((v) => v.id === viaje.vehiculoId);
    const conductor = trailersysList("conductores").find((c) => c.id === viaje.conductorId);
    const eventos = trailersysList(COLLECTION)
      .filter((e) => e.viajeId === viaje.id)
      .sort((a, b) => (a.fechaHora < b.fechaHora ? 1 : -1));
    const ultimo = eventos[0];

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-geo-alt"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(viaje.origen)} → ${escapeHtml(viaje.destino)}</div>
            <div class="item-subtitle">${escapeHtml(vehiculo ? vehiculo.placa : "Vehículo no encontrado")} · ${escapeHtml(conductor ? conductor.nombres : "Conductor no encontrado")}</div>
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

  function render() {
    renderAlertas();

    const viajes = trailersysList("viajes");
    const search = inputBuscar.value.trim().toLowerCase();
    const estado = filtroEstado.value;

    const filtrados = viajes.filter((viaje) => {
      const vehiculo = trailersysList("vehiculos").find((v) => v.id === viaje.vehiculoId);
      const conductor = trailersysList("conductores").find((c) => c.id === viaje.conductorId);
      const haystack = [viaje.origen, viaje.destino, vehiculo?.placa, conductor?.nombres]
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
    const viaje = trailersysList("viajes").find((v) => v.id === button.dataset.id);
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
    const eventos = trailersysList(COLLECTION)
      .filter((e) => e.viajeId === viajeId)
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
      onAccept: () => {
        trailersysRemove(COLLECTION, id);
        renderTimeline(viajeActualId);
        render();
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

  eventoForm.addEventListener("submit", (event) => {
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

    const viaje = trailersysList("viajes").find((v) => v.id === viajeActualId);

    trailersysUpsert(COLLECTION, {
      viajeId: viajeActualId,
      vehiculoId: viaje ? viaje.vehiculoId : "",
      fechaHora: inputFecha.value,
      evento: selectEvento.value,
      ubicacion,
      observacion: inputObservacion.value.trim(),
    });

    eventoForm.reset();
    inputFecha.value = nowForInput();
    renderTimeline(viajeActualId);
    render();
  });

  session = trailersysGetSession();
  render();
})();
