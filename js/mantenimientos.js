(function () {
  const TIPOS = ["Preventivo", "Correctivo"];

  // Caches del ultimo listado cargado desde la API.
  let mantenimientosCache = [];
  let vehiculosCache = [];

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoMantenimiento");
  const grid = document.getElementById("mantenimientoGrid");
  const emptyState = document.getElementById("mantenimientoEmptyState");
  const emptyTitle = document.getElementById("mantenimientoEmptyTitle");
  const emptyText = document.getElementById("mantenimientoEmptyText");
  const resultsCount = document.getElementById("mantenimientoResultsCount");

  const inputBuscar = document.getElementById("mantenimientoBuscar");
  const filtroVehiculo = document.getElementById("mantenimientoFiltroVehiculo");
  const filtroTipo = document.getElementById("mantenimientoFiltroTipo");

  const modalOverlay = document.getElementById("mantenimientoModalOverlay");
  const modalTitle = document.getElementById("mantenimientoModalTitle");
  const form = document.getElementById("mantenimientoForm");
  const btnCerrarModal = document.getElementById("mantenimientoModalClose");
  const btnCancelar = document.getElementById("mantenimientoCancelar");

  const inputId = document.getElementById("mantenimientoId");
  const selectVehiculo = document.getElementById("mantenimientoVehiculo");
  const selectTipo = document.getElementById("mantenimientoTipo");
  const inputFecha = document.getElementById("mantenimientoFecha");
  const inputKilometraje = document.getElementById("mantenimientoKilometraje");
  const inputCosto = document.getElementById("mantenimientoCosto");
  const inputProximoServicio = document.getElementById("mantenimientoProximoServicio");
  const inputDescripcion = document.getElementById("mantenimientoDescripcion");

  let session = null;

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

  function formatCosto(value) {
    return `$ ${Number(value).toLocaleString("es-EC", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  }

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldMantenimientoVehiculo", "fieldMantenimientoFecha", "fieldMantenimientoKilometraje",
      "fieldMantenimientoCosto", "fieldMantenimientoDescripcion"]
      .forEach((id) => setFieldError(id, ""));
  }

  // --- Selects relacionados ---
  async function refreshVehiculosCache() {
    try {
      vehiculosCache = await trailersysApiRequest("GET", "/vehiculos");
    } catch {
      vehiculosCache = [];
    }
  }

  function fillVehiculoSelect(select, placeholder) {
    const current = select.value;
    select.innerHTML = `<option value="">${placeholder}</option>`;
    vehiculosCache.forEach((v) => {
      const option = document.createElement("option");
      option.value = v.id;
      option.textContent = `${v.placa} · ${v.marca} ${v.modelo}`;
      select.appendChild(option);
    });
    if (vehiculosCache.some((v) => String(v.id) === current)) select.value = current;
  }

  function renderCard(mantenimiento, canManage) {
    const vencido = mantenimiento.proximoServicioVencido;

    const actions = canManage
      ? `<div class="item-actions">
          <button type="button" class="icon-btn" data-action="editar" data-id="${mantenimiento.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${mantenimiento.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>
        </div>`
      : "";

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-tools"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(mantenimiento.tipo)} · ${escapeHtml(mantenimiento.vehiculoPlaca)}</div>
            <div class="item-subtitle">${escapeHtml(mantenimiento.fecha)}</div>
          </div>
        </div>
        <div class="item-body">
          <p class="item-observations">${escapeHtml(mantenimiento.descripcion)}</p>
          <div class="item-meta">
            <span><i class="bi bi-speedometer2"></i>${Number(mantenimiento.kilometraje).toLocaleString("es-EC")} km</span>
            <span><i class="bi bi-cash-coin"></i>${formatCosto(mantenimiento.costo)}</span>
          </div>
          <div class="item-meta">
            ${mantenimiento.proximoServicio
              ? `<span class="badge ${vencido ? "badge-danger" : "badge-info"}"><i class="bi bi-calendar-check"></i> Próximo servicio: ${escapeHtml(mantenimiento.proximoServicio)}${vencido ? " (vencido)" : ""}</span>`
              : `<span class="badge badge-neutral">Sin próximo servicio definido</span>`}
          </div>
          ${actions}
        </div>
      </article>`;
  }

  async function render() {
    const canManage = trailersysCanManage(session, "mantenimientos");
    btnNuevo.hidden = !canManage;

    await refreshVehiculosCache();
    fillVehiculoSelect(filtroVehiculo, "Todos los vehículos");

    let mantenimientos;
    try {
      mantenimientos = await trailersysApiRequest("GET", "/mantenimientos");
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los mantenimientos";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    mantenimientosCache = mantenimientos;

    const search = inputBuscar.value.trim().toLowerCase();
    const vehiculoId = filtroVehiculo.value;
    const tipo = filtroTipo.value;

    const filtrados = mantenimientos.filter((m) => {
      const haystack = [m.descripcion, m.vehiculoPlaca].filter(Boolean).join(" ").toLowerCase();
      const matchesSearch = !search || haystack.includes(search);
      const matchesVehiculo = !vehiculoId || String(m.vehiculoId) === vehiculoId;
      const matchesTipo = !tipo || m.tipo === tipo;
      return matchesSearch && matchesVehiculo && matchesTipo;
    });

    filtrados.sort((a, b) => (a.fecha < b.fecha ? 1 : -1));

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (mantenimientos.length === 0) {
        emptyTitle.textContent = "Todavía no hay mantenimientos registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo mantenimiento" para registrar el primero.'
          : "Cuando se registren mantenimientos, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún mantenimiento coincide con la búsqueda o los filtros aplicados.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${filtrados.length} de ${mantenimientos.length} mantenimiento${mantenimientos.length === 1 ? "" : "s"}`;
    grid.innerHTML = filtrados.map((m) => renderCard(m, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  async function openForm(mantenimiento) {
    clearFieldErrors();
    form.reset();
    selectTipo.value = "Preventivo";
    await refreshVehiculosCache();
    fillVehiculoSelect(selectVehiculo, "Selecciona un vehículo");

    if (mantenimiento) {
      modalTitle.textContent = "Editar mantenimiento";
      inputId.value = mantenimiento.id;
      selectVehiculo.value = mantenimiento.vehiculoId;
      selectTipo.value = mantenimiento.tipo;
      inputFecha.value = mantenimiento.fecha;
      inputKilometraje.value = mantenimiento.kilometraje;
      inputCosto.value = mantenimiento.costo;
      inputProximoServicio.value = mantenimiento.proximoServicio || "";
      inputDescripcion.value = mantenimiento.descripcion;
    } else {
      modalTitle.textContent = "Nuevo mantenimiento";
      inputId.value = "";
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

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.vehiculoId) fail("fieldMantenimientoVehiculo", "Selecciona un vehículo existente.");
    if (!data.fecha) fail("fieldMantenimientoFecha", "La fecha es obligatoria.");

    if (data.kilometraje === "" || Number.isNaN(data.kilometraje) || data.kilometraje < 0) {
      fail("fieldMantenimientoKilometraje", "Ingresa un kilometraje válido.");
    }

    if (data.costo === "" || Number.isNaN(data.costo) || data.costo < 0) {
      fail("fieldMantenimientoCosto", "Ingresa un costo válido.");
    }

    if (!data.descripcion) fail("fieldMantenimientoDescripcion", "La descripción es obligatoria.");

    if (data.fecha && data.proximoServicio && data.proximoServicio < data.fecha) {
      fail("fieldMantenimientoFecha", "El próximo servicio debe ser posterior a la fecha del mantenimiento.");
    }

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      vehiculoId: selectVehiculo.value ? Number(selectVehiculo.value) : null,
      tipo: TIPOS.includes(selectTipo.value) ? selectTipo.value : TIPOS[0],
      fecha: inputFecha.value,
      kilometraje: inputKilometraje.value === "" ? "" : Number(inputKilometraje.value),
      costo: inputCosto.value === "" ? "" : Number(inputCosto.value),
      proximoServicio: inputProximoServicio.value || null,
      descripcion: inputDescripcion.value.trim(),
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/mantenimientos/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/mantenimientos", data);
      }
      closeForm();
      await render();
    } catch (error) {
      alert(error.message || "No se pudo guardar el mantenimiento.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Acciones sobre las tarjetas (editar / eliminar) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const mantenimiento = mantenimientosCache.find((m) => String(m.id) === id);
    if (!mantenimiento) return;

    if (action === "editar") {
      openForm(mantenimiento);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar mantenimiento",
        text: "¿Seguro que deseas eliminar este registro de mantenimiento? Esta acción no se puede deshacer.",
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/mantenimientos/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar el mantenimiento.");
          }
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  [inputBuscar, filtroVehiculo, filtroTipo].forEach((el) => {
    el.addEventListener("input", render);
    el.addEventListener("change", render);
  });

  session = trailersysGetSession();
  render();
})();
