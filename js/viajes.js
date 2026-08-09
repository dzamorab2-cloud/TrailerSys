(function () {
  const COLLECTION = "viajes";
  const ESTADOS = ["Programado", "En Curso", "Finalizado", "Cancelado"];
  const ESTADO_BADGE = {
    Programado: "badge-info",
    "En Curso": "badge-warning",
    Finalizado: "badge-success",
    Cancelado: "badge-danger",
  };

  trailersysSeedIfEmpty(COLLECTION, [
    {
      id: "viaje-seed-1",
      vehiculoId: "vehiculo-seed-1",
      conductorId: "conductor-seed-1",
      clienteId: "cliente-seed-1",
      cargaId: "carga-seed-1",
      origen: "Guayaquil, Ecuador",
      destino: "Quito, Ecuador",
      fechaSalida: "2026-08-10T07:00",
      estado: "Programado",
      observaciones: "",
      ruta: {
        origenCoords: { lat: -2.1894, lng: -79.8891 },
        destinoCoords: { lat: -0.2201641, lng: -78.5123274 },
        distanciaKm: 424.5,
        duracionMin: 372.6,
        path: null,
      },
    },
    {
      id: "viaje-seed-2",
      vehiculoId: "vehiculo-seed-2",
      conductorId: "conductor-seed-2",
      clienteId: "cliente-seed-2",
      cargaId: "carga-seed-2",
      origen: "Ambato, Ecuador",
      destino: "Riobamba, Ecuador",
      fechaSalida: "2026-08-09T06:00",
      estado: "En Curso",
      observaciones: "",
      ruta: null,
    },
  ]);

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoViaje");
  const grid = document.getElementById("viajeGrid");
  const emptyState = document.getElementById("viajeEmptyState");
  const emptyTitle = document.getElementById("viajeEmptyTitle");
  const emptyText = document.getElementById("viajeEmptyText");
  const resultsCount = document.getElementById("viajeResultsCount");

  const inputBuscar = document.getElementById("viajeBuscar");
  const filtroEstado = document.getElementById("viajeFiltroEstado");

  const modalOverlay = document.getElementById("viajeModalOverlay");
  const modalTitle = document.getElementById("viajeModalTitle");
  const form = document.getElementById("viajeForm");
  const btnCerrarModal = document.getElementById("viajeModalClose");
  const btnCancelar = document.getElementById("viajeCancelar");

  const inputId = document.getElementById("viajeId");
  const selectVehiculo = document.getElementById("viajeVehiculo");
  const selectConductor = document.getElementById("viajeConductor");
  const selectCliente = document.getElementById("viajeCliente");
  const selectCarga = document.getElementById("viajeCarga");
  const inputOrigen = document.getElementById("viajeOrigen");
  const inputDestino = document.getElementById("viajeDestino");
  const inputFechaSalida = document.getElementById("viajeFechaSalida");
  const selectEstado = document.getElementById("viajeEstado");
  const inputObservaciones = document.getElementById("viajeObservaciones");

  const btnCalcularRuta = document.getElementById("viajeCalcularRuta");
  const rutaStatus = document.getElementById("viajeRutaStatus");

  const mapaModalOverlay = document.getElementById("viajeMapaModalOverlay");
  const mapaTitle = document.getElementById("viajeMapaTitle");
  const mapaContainer = document.getElementById("viajeMapaContainer");
  const mapaResumen = document.getElementById("viajeMapaResumen");
  const btnMapaClose = document.getElementById("viajeMapaClose");
  const btnMapaCerrarBtn = document.getElementById("viajeMapaCerrarBtn");
  const btnMapaRecalcular = document.getElementById("viajeMapaRecalcular");

  let session = null;
  let rutaCalculada = null;
  let leafletMapInstance = null;
  let mapaViajeActualId = null;

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldViajeVehiculo", "fieldViajeConductor", "fieldViajeCliente",
      "fieldViajeOrigen", "fieldViajeDestino", "fieldViajeFechaSalida"]
      .forEach((id) => setFieldError(id, ""));
  }

  function setRutaStatus(text, kind) {
    rutaStatus.textContent = text;
    rutaStatus.classList.remove("status-success", "status-error", "status-loading");
    if (kind) rutaStatus.classList.add(`status-${kind}`);
  }

  function eta(viaje) {
    if (!viaje.ruta || !viaje.fechaSalida) return null;
    const salida = new Date(viaje.fechaSalida);
    if (Number.isNaN(salida.getTime())) return null;
    return new Date(salida.getTime() + viaje.ruta.duracionMin * 60000);
  }

  // --- Selects relacionados ---
  function fillSelect(select, items, labelFn, placeholder) {
    const current = select.value;
    select.innerHTML = `<option value="">${placeholder}</option>`;
    items.forEach((item) => {
      const option = document.createElement("option");
      option.value = item.id;
      option.textContent = labelFn(item);
      select.appendChild(option);
    });
    if (items.some((item) => item.id === current)) select.value = current;
  }

  function refreshRelationOptions() {
    fillSelect(selectVehiculo, trailersysList("vehiculos"), (v) => `${v.placa} · ${v.marca} ${v.modelo}`, "Selecciona un vehículo");
    fillSelect(selectConductor, trailersysList("conductores"), (c) => c.nombres, "Selecciona un conductor");
    fillSelect(selectCliente, trailersysList("clientes"), (c) => c.nombre, "Selecciona un cliente");
    fillSelect(selectCarga, trailersysList("cargas"), (c) => c.descripcion, "Sin carga asociada");
  }

  // --- Tarjetas ---
  function renderCard(viaje, canManage) {
    const badgeClass = ESTADO_BADGE[viaje.estado] || "badge-neutral";
    const vehiculo = trailersysList("vehiculos").find((v) => v.id === viaje.vehiculoId);
    const conductor = trailersysList("conductores").find((c) => c.id === viaje.conductorId);
    const cliente = trailersysList("clientes").find((c) => c.id === viaje.clienteId);
    const carga = viaje.cargaId ? trailersysList("cargas").find((c) => c.id === viaje.cargaId) : null;

    const distanciaLabel = viaje.estado === "Finalizado" ? "recorridos" : "por recorrer";
    const distanciaText = viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km ${distanciaLabel}` : "Ruta no calculada";
    const duracionText = viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "—";
    const etaDate = eta(viaje);
    const etaText = etaDate ? trailersysFormatDateTime(etaDate) : "—";

    const gestionActions = canManage
      ? `<button type="button" class="icon-btn" data-action="editar" data-id="${viaje.id}" title="Editar">
          <i class="bi bi-pencil"></i>
        </button>
        <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${viaje.id}" title="Eliminar">
          <i class="bi bi-trash3"></i>
        </button>`
      : "";

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-signpost-split"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(viaje.origen)} → ${escapeHtml(viaje.destino)}</div>
            <div class="item-subtitle">${escapeHtml(vehiculo ? vehiculo.placa : "Vehículo no encontrado")} · ${escapeHtml(conductor ? conductor.nombres : "Conductor no encontrado")}</div>
          </div>
        </div>
        <div class="item-body">
          <div class="item-meta">
            <span class="badge ${badgeClass}">${escapeHtml(viaje.estado)}</span>
            <span><i class="bi bi-building"></i>${escapeHtml(cliente ? cliente.nombre : "Cliente no encontrado")}</span>
            ${carga ? `<span><i class="bi bi-box-seam"></i>${escapeHtml(carga.descripcion)}</span>` : ""}
          </div>
          <div class="item-meta">
            <span><i class="bi bi-signpost"></i>${distanciaText}</span>
            <span><i class="bi bi-clock-history"></i>${duracionText}</span>
            <span><i class="bi bi-flag"></i>ETA ${etaText}</span>
          </div>
          <div class="item-actions">
            <button type="button" class="icon-btn" data-action="mapa" data-id="${viaje.id}" title="Ver mapa de la ruta">
              <i class="bi bi-map"></i>
            </button>
            ${gestionActions}
          </div>
        </div>
      </article>`;
  }

  function render() {
    const viajes = trailersysList(COLLECTION);

    const canManage = trailersysCanManage(session, COLLECTION);
    btnNuevo.hidden = !canManage;

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
        emptyTitle.textContent = "Todavía no hay viajes registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo viaje" para registrar el primero.'
          : "Cuando se registren viajes, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún viaje coincide con la búsqueda o el filtro aplicado.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${filtrados.length} de ${viajes.length} viaje${viajes.length === 1 ? "" : "s"}`;
    grid.innerHTML = filtrados.map((v) => renderCard(v, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  function openForm(viaje) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Programado";
    refreshRelationOptions();

    if (viaje) {
      modalTitle.textContent = "Editar viaje";
      inputId.value = viaje.id;
      selectVehiculo.value = viaje.vehiculoId;
      selectConductor.value = viaje.conductorId;
      selectCliente.value = viaje.clienteId;
      selectCarga.value = viaje.cargaId || "";
      inputOrigen.value = viaje.origen;
      inputDestino.value = viaje.destino;
      inputFechaSalida.value = viaje.fechaSalida;
      selectEstado.value = viaje.estado;
      inputObservaciones.value = viaje.observaciones || "";
      rutaCalculada = viaje.ruta || null;
    } else {
      modalTitle.textContent = "Nuevo viaje";
      inputId.value = "";
      rutaCalculada = null;
    }

    if (rutaCalculada) {
      setRutaStatus(`${rutaCalculada.distanciaKm.toFixed(1)} km · ${trailersysFormatDuration(rutaCalculada.duracionMin)} estimado.`, "success");
    } else {
      setRutaStatus("Sin ruta calculada todavía.", null);
    }

    trailersysOpenModal(modalOverlay);
    selectVehiculo.focus();
  }

  function closeForm() {
    trailersysCloseModal(modalOverlay);
  }

  btnNuevo.addEventListener("click", () => openForm(null));
  btnCerrarModal.addEventListener("click", closeForm);
  btnCancelar.addEventListener("click", closeForm);
  modalOverlay.addEventListener("click", (event) => {
    if (event.target === modalOverlay) closeForm();
  });

  selectCarga.addEventListener("change", () => {
    if (!selectCarga.value) return;
    const carga = trailersysList("cargas").find((c) => c.id === selectCarga.value);
    if (!carga) return;
    inputOrigen.value = carga.origen;
    inputDestino.value = carga.destino;
    if (carga.clienteId) selectCliente.value = carga.clienteId;
    rutaCalculada = null;
    setRutaStatus("Origen/destino actualizados desde la carga. Calcula la ruta nuevamente.", null);
  });

  [inputOrigen, inputDestino].forEach((input) => {
    input.addEventListener("input", () => {
      if (rutaCalculada) {
        rutaCalculada = null;
        setRutaStatus("Origen/destino modificados. Calcula la ruta nuevamente.", null);
      }
    });
  });

  btnCalcularRuta.addEventListener("click", async () => {
    const origenTexto = inputOrigen.value.trim();
    const destinoTexto = inputDestino.value.trim();

    if (!origenTexto || !destinoTexto) {
      setRutaStatus("Ingresa origen y destino antes de calcular.", "error");
      return;
    }

    btnCalcularRuta.disabled = true;
    setRutaStatus("Calculando ruta con el mapa gratuito (OpenStreetMap)...", "loading");

    const origenCoords = await trailersysGeocode(origenTexto);
    if (!origenCoords) {
      setRutaStatus(`No se encontró "${origenTexto}". Intenta ser más específico (ej. "Quito, Ecuador").`, "error");
      btnCalcularRuta.disabled = false;
      return;
    }

    const destinoCoords = await trailersysGeocode(destinoTexto);
    if (!destinoCoords) {
      setRutaStatus(`No se encontró "${destinoTexto}". Intenta ser más específico (ej. "Guayaquil, Ecuador").`, "error");
      btnCalcularRuta.disabled = false;
      return;
    }

    const ruta = await trailersysGetRoute(origenCoords, destinoCoords);
    btnCalcularRuta.disabled = false;

    if (!ruta) {
      setRutaStatus("No se pudo calcular la ruta por carretera entre esos puntos.", "error");
      return;
    }

    rutaCalculada = {
      origenCoords: { lat: origenCoords.lat, lng: origenCoords.lng },
      destinoCoords: { lat: destinoCoords.lat, lng: destinoCoords.lng },
      distanciaKm: ruta.distanceKm,
      duracionMin: ruta.durationMin,
      path: ruta.path,
    };
    setRutaStatus(`${ruta.distanceKm.toFixed(1)} km · ${trailersysFormatDuration(ruta.durationMin)} estimado.`, "success");
  });

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.vehiculoId) fail("fieldViajeVehiculo", "Selecciona un vehículo.");
    if (!data.conductorId) fail("fieldViajeConductor", "Selecciona un conductor.");
    if (!data.clienteId) fail("fieldViajeCliente", "Selecciona un cliente.");
    if (!data.origen) fail("fieldViajeOrigen", "El origen es obligatorio.");
    if (!data.destino) fail("fieldViajeDestino", "El destino es obligatorio.");
    if (!data.fechaSalida) fail("fieldViajeFechaSalida", "La fecha de salida es obligatoria.");

    return valid;
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();

    const data = {
      vehiculoId: selectVehiculo.value,
      conductorId: selectConductor.value,
      clienteId: selectCliente.value,
      cargaId: selectCarga.value,
      origen: inputOrigen.value.trim(),
      destino: inputDestino.value.trim(),
      fechaSalida: inputFechaSalida.value,
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      observaciones: inputObservaciones.value.trim(),
      ruta: rutaCalculada,
    };

    if (!validate(data)) return;

    data.id = inputId.value || undefined;
    trailersysUpsert(COLLECTION, data);
    closeForm();
    render();
  });

  // --- Acciones sobre las tarjetas (editar / eliminar / mapa) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const viaje = trailersysList(COLLECTION).find((v) => v.id === id);
    if (!viaje) return;

    if (action === "editar") {
      openForm(viaje);
    } else if (action === "mapa") {
      openMapModal(viaje);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar viaje",
        text: `¿Seguro que deseas eliminar el viaje de ${viaje.origen} a ${viaje.destino}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: () => {
          trailersysRemove(COLLECTION, id);
          render();
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  [inputBuscar, filtroEstado].forEach((el) => {
    el.addEventListener("input", render);
    el.addEventListener("change", render);
  });

  // --- Visor de mapa ---
  function renderMapaResumen(viaje) {
    const distanciaLabel = viaje.estado === "Finalizado" ? "Distancia recorrida" : "Distancia por recorrer";
    const distanciaText = viaje.ruta ? `${viaje.ruta.distanciaKm.toFixed(1)} km` : "—";
    const duracionText = viaje.ruta ? trailersysFormatDuration(viaje.ruta.duracionMin) : "—";
    const salidaText = viaje.fechaSalida ? trailersysFormatDateTime(viaje.fechaSalida) : "—";
    const etaDate = eta(viaje);
    const etaText = etaDate ? trailersysFormatDateTime(etaDate) : "—";

    mapaResumen.innerHTML = `
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

  function drawLeafletRoute(viaje) {
    destroyLeafletMap();
    mapaContainer.innerHTML = "";

    leafletMapInstance = L.map(mapaContainer, { scrollWheelZoom: true });
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; colaboradores de OpenStreetMap",
      maxZoom: 18,
    }).addTo(leafletMapInstance);

    const origenLatLng = [viaje.ruta.origenCoords.lat, viaje.ruta.origenCoords.lng];
    const destinoLatLng = [viaje.ruta.destinoCoords.lat, viaje.ruta.destinoCoords.lng];

    L.marker(origenLatLng).addTo(leafletMapInstance).bindPopup(`Origen: ${viaje.origen}`);
    L.marker(destinoLatLng).addTo(leafletMapInstance).bindPopup(`Destino: ${viaje.destino}`);

    if (viaje.ruta.path && viaje.ruta.path.length) {
      const polyline = L.polyline(viaje.ruta.path, { color: "#f2874b", weight: 4 }).addTo(leafletMapInstance);
      leafletMapInstance.fitBounds(polyline.getBounds(), { padding: [24, 24] });
    } else {
      leafletMapInstance.fitBounds(L.latLngBounds([origenLatLng, destinoLatLng]), { padding: [24, 24] });
    }

    setTimeout(() => {
      if (leafletMapInstance) leafletMapInstance.invalidateSize();
    }, 200);
  }

  function showMapaPlaceholder(mensaje, mostrarBoton) {
    mapaContainer.innerHTML = `
      <div class="route-map-placeholder">
        <i class="bi bi-map"></i>
        <p>${escapeHtml(mensaje)}</p>
        ${mostrarBoton ? `<button type="button" class="btn btn-primary" id="viajeMapaCalcularInline">Calcular ruta ahora</button>` : ""}
      </div>`;

    if (mostrarBoton) {
      document.getElementById("viajeMapaCalcularInline").addEventListener("click", async () => {
        const viaje = trailersysList(COLLECTION).find((v) => v.id === mapaViajeActualId);
        if (!viaje) return;
        await calcularYGuardarRuta(viaje);
      });
    }
  }

  async function calcularYGuardarRuta(viaje) {
    showMapaPlaceholder("Calculando ruta con el mapa gratuito (OpenStreetMap)...", false);

    const origenCoords = await trailersysGeocode(viaje.origen);
    const destinoCoords = origenCoords ? await trailersysGeocode(viaje.destino) : null;
    const ruta = destinoCoords ? await trailersysGetRoute(origenCoords, destinoCoords) : null;

    if (!origenCoords || !destinoCoords || !ruta) {
      showMapaPlaceholder("No se pudo calcular la ruta automáticamente para este origen/destino.", false);
      return;
    }

    viaje.ruta = {
      origenCoords: { lat: origenCoords.lat, lng: origenCoords.lng },
      destinoCoords: { lat: destinoCoords.lat, lng: destinoCoords.lng },
      distanciaKm: ruta.distanceKm,
      duracionMin: ruta.durationMin,
      path: ruta.path,
    };
    trailersysUpsert(COLLECTION, viaje);
    renderMapaResumen(viaje);
    drawLeafletRoute(viaje);
    render();
  }

  async function ensureMapaRendered(viaje) {
    if (!viaje.ruta) {
      showMapaPlaceholder("Esta ruta todavía no se ha calculado.", true);
      return;
    }

    if (!viaje.ruta.path) {
      const ruta = await trailersysGetRoute(viaje.ruta.origenCoords, viaje.ruta.destinoCoords);
      if (ruta) {
        viaje.ruta.path = ruta.path;
        trailersysUpsert(COLLECTION, viaje);
      }
    }

    drawLeafletRoute(viaje);
  }

  function openMapModal(viaje) {
    mapaViajeActualId = viaje.id;
    mapaTitle.textContent = `${viaje.origen} → ${viaje.destino}`;
    renderMapaResumen(viaje);
    trailersysOpenModal(mapaModalOverlay);
    ensureMapaRendered(viaje);
  }

  function closeMapModal() {
    trailersysCloseModal(mapaModalOverlay);
    destroyLeafletMap();
    mapaViajeActualId = null;
  }

  btnMapaClose.addEventListener("click", closeMapModal);
  btnMapaCerrarBtn.addEventListener("click", closeMapModal);
  mapaModalOverlay.addEventListener("click", (event) => {
    if (event.target === mapaModalOverlay) closeMapModal();
  });

  btnMapaRecalcular.addEventListener("click", async () => {
    const viaje = trailersysList(COLLECTION).find((v) => v.id === mapaViajeActualId);
    if (!viaje) return;
    await calcularYGuardarRuta(viaje);
  });

  session = trailersysGetSession();
  render();
})();
