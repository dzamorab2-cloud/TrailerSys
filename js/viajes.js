(function () {
  const ESTADOS = ["Programado", "En Curso", "Finalizado", "Cancelado"];
  const ESTADO_BADGE = {
    Programado: "badge-info",
    "En Curso": "badge-warning",
    Finalizado: "badge-success",
    Cancelado: "badge-danger",
  };

  // Convierte la "ruta" del backend (origenLat/origenLng/... + path como
  // {lat,lng}) a la forma interna que ya usaba este modulo con Leaflet
  // (origenCoords/destinoCoords + path como [lat,lng]), y viceversa.
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

  function toApiRuta(ruta) {
    if (!ruta) return null;
    return {
      origenLat: ruta.origenCoords.lat,
      origenLng: ruta.origenCoords.lng,
      destinoLat: ruta.destinoCoords.lat,
      destinoLng: ruta.destinoCoords.lng,
      distanciaKm: ruta.distanciaKm,
      duracionMin: ruta.duracionMin,
      path: ruta.path ? ruta.path.map(([lat, lng]) => ({ lat, lng })) : null,
    };
  }

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
  const historialOverlay = document.getElementById("viajeHistorialModalOverlay");
  const historialTitle = document.getElementById("viajeHistorialTitle");
  const historialContent = document.getElementById("viajeHistorialContent");

  let session = null;
  let rutaCalculada = null;
  let leafletMapInstance = null;
  let mapaViajeActualId = null;

  // Cache del ultimo listado de viajes cargado desde la API.
  let viajesCache = [];
  let currentPage = 0;
  let pageMeta = null;

  // Caches de los catalogos relacionados, usados solo para poblar los
  // selectores del formulario de alta/edicion.
  let vehiculosCache = [];
  let conductoresCache = [];
  let clientesCache = [];
  let cargasCache = [];

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

  function formatPesoDoble(kg) {
    const kilos = Number(kg) || 0;
    const libras = kilos * 2.2046226218;
    return `${kilos.toLocaleString("es-EC")} kg / ${libras.toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
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
    if (items.some((item) => String(item.id) === current)) select.value = current;
  }

  async function refreshCaches() {
    [vehiculosCache, conductoresCache, clientesCache, cargasCache] = await Promise.all([
      trailersysApiRequest("GET", "/paginas/vehiculos?page=0&size=100&estado=Disponible").then((d) => d.content).catch(() => []),
      trailersysApiRequest("GET", "/paginas/conductores?page=0&size=100&estado=Disponible").then((d) => d.content).catch(() => []),
      trailersysPagedRequest("clientes", 0, 100).then((d) => d.content).catch(() => []),
      trailersysPagedRequest("cargas", 0, 100).then((d) => d.content).catch(() => []),
    ]);
  }

  // Deja pasar solo los recursos libres (estado === estadoLibre), mas el
  // que ya tiene asignado este viaje (si se esta editando), para no perder
  // la seleccion actual aunque su estado ya no sea "libre".
  function filtrarDisponibles(items, estadoLibre, currentId) {
    return items.filter((item) => item.estado === estadoLibre || String(item.id) === String(currentId));
  }

  async function refreshRelationOptions(viaje) {
    await refreshCaches();
    if (viaje?.vehiculoId && !vehiculosCache.some((v) => String(v.id) === String(viaje.vehiculoId))) {
      const actual = await trailersysApiRequest("GET", `/vehiculos/${viaje.vehiculoId}`).catch(() => null);
      if (actual) vehiculosCache.push(actual);
    }
    if (viaje?.conductorId && !conductoresCache.some((c) => String(c.id) === String(viaje.conductorId))) {
      const actual = await trailersysApiRequest("GET", `/conductores/${viaje.conductorId}`).catch(() => null);
      if (actual) conductoresCache.push(actual);
    }
    fillSelect(selectVehiculo, filtrarDisponibles(vehiculosCache, "Disponible", viaje?.vehiculoId),
      (v) => `${v.placa} · ${v.marca} ${v.modelo} · ${v.estado}`, "Selecciona un vehículo disponible");
    fillSelect(selectConductor, filtrarDisponibles(conductoresCache, "Disponible", viaje?.conductorId),
      (c) => `${c.nombres} · ${c.estado}`, "Selecciona un conductor disponible");
    fillSelect(selectCliente, clientesCache, (c) => c.nombre, "Selecciona un cliente");
    fillSelect(selectCarga, filtrarDisponibles(cargasCache, "Pendiente", viaje?.cargaId),
      (c) => c.descripcion, "Sin carga asociada");
  }

  // --- Tarjetas ---
  function renderCard(viaje, canManage) {
    const badgeClass = ESTADO_BADGE[viaje.estado] || "badge-neutral";

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
            <div class="item-subtitle">${escapeHtml(viaje.vehiculoPlaca)} · ${escapeHtml(viaje.conductorNombres)}</div>
          </div>
        </div>
        <div class="item-body">
          <div class="item-meta">
            <span class="badge ${badgeClass}">${escapeHtml(viaje.estado)}</span>
            ${viaje.entregaValidada
              ? `<span class="badge badge-info"><i class="bi bi-patch-check"></i> Validada</span>`
              : viaje.entregaConfirmada
                ? `<span class="badge badge-success"><i class="bi bi-check-circle"></i> Entrega confirmada</span>`
                : ""}
            <span><i class="bi bi-building"></i>${escapeHtml(viaje.clienteNombre)}</span>
            ${viaje.cargaDescripcion ? `<span><i class="bi bi-box-seam"></i>${escapeHtml(viaje.cargaDescripcion)}</span>` : ""}
          </div>
          <div class="item-meta">
            <span><i class="bi bi-signpost"></i>${distanciaText}</span>
            <span><i class="bi bi-clock-history"></i>${duracionText}</span>
            <span><i class="bi bi-flag"></i>ETA ${etaText}</span>
          </div>
          <div class="item-actions">
            <button type="button" class="icon-btn" data-action="historial" data-id="${viaje.id}" title="Ver historial del viaje">
              <i class="bi bi-clock-history"></i>
            </button>
            <button type="button" class="icon-btn" data-action="guia" data-id="${viaje.id}" title="Ver e imprimir guía">
              <i class="bi bi-file-earmark-text"></i>
            </button>
            <button type="button" class="icon-btn" data-action="mapa" data-id="${viaje.id}" title="Ver mapa de la ruta">
              <i class="bi bi-map"></i>
            </button>
            ${gestionActions}
          </div>
        </div>
      </article>`;
  }

  async function showViajeGuide(viaje) {
    const [conductor, vehiculo, carga] = await Promise.all([
      trailersysApiRequest("GET", `/conductores/${viaje.conductorId}`).catch(() => null),
      trailersysApiRequest("GET", `/vehiculos/${viaje.vehiculoId}`).catch(() => null),
      viaje.cargaId ? trailersysApiRequest("GET", `/cargas/${viaje.cargaId}`).catch(() => null) : Promise.resolve(null)
    ]);
    trailersysShowGuide({
      tipo: "Viaje", id: viaje.id, estado: viaje.estado,
      secciones: [
        { titulo: "Conductor", icono: "bi-person-badge", campos: [
          ["Nombre completo", conductor?.nombres || viaje.conductorNombres],
          ["Identificación", conductor?.identificacion], ["Teléfono", conductor?.telefono],
          ["Licencia", conductor?.licenciaNumero], ["Categoría", conductor?.licenciaCategoria],
          ["Vencimiento", conductor?.licenciaVencimiento]
        ] },
        { titulo: "Vehículo", icono: "bi-truck", campos: [
          ["Placa", vehiculo?.placa || viaje.vehiculoPlaca], ["Marca", vehiculo?.marca],
          ["Modelo", vehiculo?.modelo], ["Tipo", vehiculo?.tipo], ["Año", vehiculo?.anio],
          ["Color", vehiculo?.color], ["Capacidad", vehiculo ? formatPesoDoble(vehiculo.capacidad) : "—"]
        ] },
        { titulo: "Carga transportada", icono: "bi-box-seam", campos: [
          ["Mercancía", carga?.descripcion || viaje.cargaDescripcion || "Viaje sin carga asociada"],
          ["Tipo", carga?.tipo], ["Peso", carga ? formatPesoDoble(carga.peso) : "—"],
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

  async function openHistorial(viaje) {
    historialTitle.textContent = `Historial del viaje #${viaje.id}`;
    historialContent.innerHTML = '<div class="dashboard-empty">Cargando historial…</div>';
    trailersysOpenModal(historialOverlay);
    const items = await trailersysApiRequest("GET", `/viajes/${viaje.id}/historial`);
    historialContent.innerHTML = items.length ? items.map((item) => `
      <div class="timeline-item timeline-${String(item.tipo).toLowerCase()}">
        <div class="timeline-dot"><i class="bi bi-circle-fill"></i></div>
        <div class="timeline-body"><div class="timeline-date">${trailersysFormatDateTime(item.fecha)}</div><h4>${escapeHtml(item.titulo)}</h4><p>${escapeHtml(item.detalle || "Sin observaciones")}</p></div>
      </div>`).join("") : '<div class="dashboard-empty">Este viaje todavía no tiene eventos registrados.</div>';
  }

  const closeHistorial = () => trailersysCloseModal(historialOverlay);
  document.getElementById("viajeHistorialClose").addEventListener("click", closeHistorial);
  document.getElementById("viajeHistorialCerrarBtn").addEventListener("click", closeHistorial);
  historialOverlay.addEventListener("click", (event) => { if (event.target === historialOverlay) closeHistorial(); });

  async function render() {
    const canManage = trailersysCanManage(session, "viajes");
    btnNuevo.hidden = !canManage;

    let viajes;
    try {
      pageMeta = await trailersysPagedRequest("viajes", currentPage, 24, {
        search: inputBuscar.value.trim(),
        estado: filtroEstado.value,
      });
      viajes = pageMeta.content;
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

    const filtrados = viajes;

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
    resultsCount.textContent = `${Number(pageMeta.totalElements).toLocaleString("es-EC")} viaje${pageMeta.totalElements === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((v) => renderCard(v, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  async function openForm(viaje) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Programado";
    await refreshRelationOptions(viaje);

    if (viaje) {
      modalTitle.textContent = "Editar viaje";
      inputId.value = viaje.id;
      selectVehiculo.value = viaje.vehiculoId;
      selectConductor.value = viaje.conductorId;
      selectCliente.value = viaje.clienteId;
      selectCarga.value = viaje.cargaId || "";
      inputOrigen.value = viaje.origen;
      inputDestino.value = viaje.destino;
      inputFechaSalida.value = viaje.fechaSalida ? viaje.fechaSalida.slice(0, 16) : "";
      inputFechaSalida.removeAttribute("min");
      selectEstado.value = viaje.estado;
      inputObservaciones.value = viaje.observaciones || "";
      rutaCalculada = viaje.ruta || null;
    } else {
      modalTitle.textContent = "Nuevo viaje";
      inputId.value = "";
      // Solo para viajes nuevos: no tiene sentido bloquear la edicion de
      // uno ya existente (por ejemplo uno Finalizado con fecha pasada).
      inputFechaSalida.min = nowForInput();
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
    const carga = cargasCache.find((c) => String(c.id) === selectCarga.value);
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

    const origenCoords = await trailersysGeocode(trailersysLugarParaGeocodificar(origenTexto));
    if (!origenCoords) {
      setRutaStatus(`No se encontró "${origenTexto}". Intenta ser más específico (ej. "Quito, Ecuador").`, "error");
      btnCalcularRuta.disabled = false;
      return;
    }

    const destinoCoords = await trailersysGeocode(trailersysLugarParaGeocodificar(destinoTexto));
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
    if (!data.fechaSalida) {
      fail("fieldViajeFechaSalida", "La fecha de salida es obligatoria.");
    } else if (!inputId.value && new Date(data.fechaSalida) < new Date()) {
      fail("fieldViajeFechaSalida", "La fecha de salida no puede estar en el pasado.");
    }

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      vehiculoId: selectVehiculo.value ? Number(selectVehiculo.value) : null,
      conductorId: selectConductor.value ? Number(selectConductor.value) : null,
      clienteId: selectCliente.value ? Number(selectCliente.value) : null,
      cargaId: selectCarga.value ? Number(selectCarga.value) : null,
      origen: inputOrigen.value.trim(),
      destino: inputDestino.value.trim(),
      fechaSalida: inputFechaSalida.value,
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      observaciones: inputObservaciones.value.trim(),
      ruta: toApiRuta(rutaCalculada),
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      let guardado;
      if (id) {
        guardado = await trailersysApiRequest("PUT", `/viajes/${id}`, data);
      } else {
        guardado = await trailersysApiRequest("POST", "/viajes", data);
      }
      closeForm();
      await render();
      if (!id && guardado) {
        guardado.ruta = fromApiRuta(guardado.ruta);
        await showViajeGuide(guardado);
      }
    } catch (error) {
      alert(error.message || "No se pudo guardar el viaje.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Acciones sobre las tarjetas (editar / eliminar / mapa) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const viaje = viajesCache.find((v) => String(v.id) === id);
    if (!viaje) return;

    if (action === "guia") {
      showViajeGuide(viaje).catch((error) => alert(error.message || "No se pudo generar la guía."));
    } else if (action === "historial") {
      openHistorial(viaje).catch((error) => { historialContent.innerHTML = `<div class="dashboard-empty">${escapeHtml(error.message)}</div>`; });
    } else if (action === "editar") {
      openForm(viaje);
    } else if (action === "mapa") {
      openMapModal(viaje);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar viaje",
        text: `¿Seguro que deseas eliminar el viaje de ${viaje.origen} a ${viaje.destino}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/viajes/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar el viaje.");
          }
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  let buscarTimer;
  inputBuscar.addEventListener("input", () => {
    clearTimeout(buscarTimer);
    buscarTimer = setTimeout(() => { currentPage = 0; render(); }, 300);
  });
  filtroEstado.addEventListener("change", () => { currentPage = 0; render(); });

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

    if (typeof L === "undefined") {
      mapaContainer.innerHTML = '<div class="route-map-placeholder"><i class="bi bi-wifi-off"></i><p>No se pudo cargar el mapa. Verifica la conexión a internet y recarga la página.</p></div>';
      return;
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
        const viaje = viajesCache.find((v) => String(v.id) === String(mapaViajeActualId));
        if (!viaje) return;
        await calcularYGuardarRuta(viaje);
      });
    }
  }

  async function persistirRuta(viaje) {
    await trailersysApiRequest("PUT", `/viajes/${viaje.id}`, {
      vehiculoId: viaje.vehiculoId,
      conductorId: viaje.conductorId,
      clienteId: viaje.clienteId,
      cargaId: viaje.cargaId || null,
      origen: viaje.origen,
      destino: viaje.destino,
      fechaSalida: viaje.fechaSalida,
      estado: viaje.estado,
      observaciones: viaje.observaciones,
      ruta: toApiRuta(viaje.ruta),
    });
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

    try {
      await persistirRuta(viaje);
    } catch (error) {
      showMapaPlaceholder(error.message || "No se pudo guardar la ruta calculada.", false);
      return;
    }

    renderMapaResumen(viaje);
    drawLeafletRoute(viaje);
    await render();
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
        try {
          await persistirRuta(viaje);
        } catch {
          // La ruta se sigue mostrando en el mapa aunque no se haya podido persistir.
        }
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
    const viaje = viajesCache.find((v) => String(v.id) === String(mapaViajeActualId));
    if (!viaje) return;
    await calcularYGuardarRuta(viaje);
  });

  session = trailersysGetSession();
  render();
})();
