(function () {
  const ESTADOS = ["Pendiente", "Asignada", "En Tránsito", "Entregada"];
  const ESTADO_BADGE = {
    Pendiente: "badge-warning",
    Asignada: "badge-info",
    "En Tránsito": "badge-neutral",
    Entregada: "badge-success",
  };

  // Cache del ultimo listado de cargas cargado desde la API, para que los
  // botones de editar/eliminar de cada tarjeta no dependan de otra peticion.
  let cargasCache = [];
  let currentPage = 0;
  let pageMeta = null;

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevaCarga");
  const grid = document.getElementById("cargaGrid");
  const emptyState = document.getElementById("cargaEmptyState");
  const emptyTitle = document.getElementById("cargaEmptyTitle");
  const emptyText = document.getElementById("cargaEmptyText");
  const resultsCount = document.getElementById("cargaResultsCount");

  const inputBuscar = document.getElementById("cargaBuscar");
  const filtroEstado = document.getElementById("cargaFiltroEstado");

  const modalOverlay = document.getElementById("cargaModalOverlay");
  const modalTitle = document.getElementById("cargaModalTitle");
  const form = document.getElementById("cargaForm");
  const btnCerrarModal = document.getElementById("cargaModalClose");
  const btnCancelar = document.getElementById("cargaCancelar");

  const inputId = document.getElementById("cargaId");
  const inputDescripcion = document.getElementById("cargaDescripcion");
  const selectCliente = document.getElementById("cargaCliente");
  const inputTipo = document.getElementById("cargaTipo");
  const inputPeso = document.getElementById("cargaPeso");
  const selectPesoUnidad = document.getElementById("cargaPesoUnidad");
  const selectEstado = document.getElementById("cargaEstado");
  const inputOrigen = document.getElementById("cargaOrigen");
  const inputDestino = document.getElementById("cargaDestino");
  const inputObservaciones = document.getElementById("cargaObservaciones");

  let session = null;
  let pesoUnidadAnterior = "kg";

  function kgToLb(kg) {
    return Number(kg) * 2.2046226218;
  }

  function valorEnKg(valor, unidad) {
    return unidad === "lb" ? Number(valor) / 2.2046226218 : Number(valor);
  }

  function formatPesoDoble(kg) {
    const kilos = Number(kg) || 0;
    return `${kilos.toLocaleString("es-EC")} kg / ${kgToLb(kilos).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  }
  // Cache del listado de clientes usado solo para poblar el selector del
  // formulario; la tarjeta y la búsqueda usan carga.clienteNombre, que ya
  // viene denormalizado desde el backend.
  let clientesCache = [];

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
    ["fieldCargaDescripcion", "fieldCargaCliente", "fieldCargaTipo",
      "fieldCargaPeso", "fieldCargaOrigen", "fieldCargaDestino"]
      .forEach((id) => setFieldError(id, ""));
  }

  async function refreshClienteOptions() {
    try {
      clientesCache = (await trailersysPagedRequest("clientes", 0, 100)).content;
    } catch {
      clientesCache = [];
    }
    const current = selectCliente.value;
    selectCliente.innerHTML = '<option value="">Selecciona un cliente</option>';
    clientesCache.forEach((c) => {
      const option = document.createElement("option");
      option.value = c.id;
      option.textContent = c.nombre;
      selectCliente.appendChild(option);
    });
    if (clientesCache.some((c) => String(c.id) === current)) selectCliente.value = current;
  }

  function renderCard(carga, canManage) {
    const badgeClass = ESTADO_BADGE[carga.estado] || "badge-neutral";

    const actions = canManage
      ? `<div class="item-actions">
          <button type="button" class="icon-btn" data-action="editar" data-id="${carga.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${carga.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>
        </div>`
      : "";

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-box-seam"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(carga.descripcion)}</div>
            <div class="item-subtitle">${escapeHtml(carga.tipo)} · ${escapeHtml(carga.clienteNombre || "Cliente no encontrado")}</div>
          </div>
        </div>
        <div class="item-body">
          <div class="item-route">
            <i class="bi bi-geo-alt"></i>
            <span>${escapeHtml(carga.origen)}</span>
            <span class="item-route-sep"><i class="bi bi-arrow-right"></i></span>
            <span>${escapeHtml(carga.destino)}</span>
          </div>
          <div class="item-meta">
            <span class="badge ${badgeClass}">${escapeHtml(carga.estado)}</span>
            <span><i class="bi bi-box-seam"></i>${formatPesoDoble(carga.peso)}</span>
          </div>
          ${carga.observaciones ? `<p class="item-observations">${escapeHtml(carga.observaciones)}</p>` : ""}
          ${actions}
        </div>
      </article>`;
  }

  async function render() {
    const canManage = trailersysCanManage(session, "cargas");
    btnNuevo.hidden = !canManage;

    let cargas;
    try {
      pageMeta = await trailersysPagedRequest("cargas", currentPage, 24);
      cargas = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar las cargas";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    cargasCache = cargas;

    const search = inputBuscar.value.trim().toLowerCase();
    const estado = filtroEstado.value;

    const filtrados = cargas.filter((carga) => {
      const clienteNombre = (carga.clienteNombre || "").toLowerCase();
      const matchesSearch = !search
        || carga.descripcion.toLowerCase().includes(search)
        || carga.origen.toLowerCase().includes(search)
        || carga.destino.toLowerCase().includes(search)
        || clienteNombre.includes(search);
      const matchesEstado = !estado || carga.estado === estado;
      return matchesSearch && matchesEstado;
    });

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (cargas.length === 0) {
        emptyTitle.textContent = "Todavía no hay cargas registradas";
        emptyText.textContent = canManage
          ? 'Usa "Nueva carga" para registrar la primera.'
          : "Cuando se registren cargas, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ninguna carga coincide con la búsqueda o el filtro aplicado.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${filtrados.length} de ${cargas.length} carga${cargas.length === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((c) => renderCard(c, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  async function openForm(carga) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Pendiente";
    selectPesoUnidad.value = "kg";
    pesoUnidadAnterior = "kg";
    await refreshClienteOptions();

    if (carga) {
      modalTitle.textContent = "Editar carga";
      inputId.value = carga.id;
      inputDescripcion.value = carga.descripcion;
      selectCliente.value = carga.clienteId;
      inputTipo.value = carga.tipo;
      inputPeso.value = carga.peso;
      selectEstado.value = carga.estado;
      inputOrigen.value = carga.origen;
      inputDestino.value = carga.destino;
      inputObservaciones.value = carga.observaciones || "";
    } else {
      modalTitle.textContent = "Nueva carga";
      inputId.value = "";
    }

    trailersysOpenModal(modalOverlay);
    inputDescripcion.focus();
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

  selectPesoUnidad.addEventListener("change", () => {
    const valor = Number(inputPeso.value);
    if (!Number.isNaN(valor) && inputPeso.value !== "") {
      const kg = valorEnKg(valor, pesoUnidadAnterior);
      inputPeso.value = (selectPesoUnidad.value === "lb" ? kgToLb(kg) : kg).toFixed(2).replace(/\.00$/, "");
    }
    pesoUnidadAnterior = selectPesoUnidad.value;
  });

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.descripcion) fail("fieldCargaDescripcion", "La descripción es obligatoria.");
    if (!data.clienteId) fail("fieldCargaCliente", "Selecciona un cliente.");
    if (!data.tipo) fail("fieldCargaTipo", "El tipo de mercancía es obligatorio.");

    if (data.peso === "" || Number.isNaN(data.peso) || data.peso < 0) {
      fail("fieldCargaPeso", "Ingresa un peso válido.");
    }

    if (!data.origen) fail("fieldCargaOrigen", "El origen es obligatorio.");
    if (!data.destino) fail("fieldCargaDestino", "El destino es obligatorio.");

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      descripcion: inputDescripcion.value.trim(),
      clienteId: selectCliente.value ? Number(selectCliente.value) : null,
      tipo: inputTipo.value.trim(),
      peso: inputPeso.value === "" ? "" : Math.round(valorEnKg(inputPeso.value, selectPesoUnidad.value)),
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      origen: inputOrigen.value.trim(),
      destino: inputDestino.value.trim(),
      observaciones: inputObservaciones.value.trim(),
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/cargas/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/cargas", data);
      }
      closeForm();
      await render();
    } catch (error) {
      alert(error.message || "No se pudo guardar la carga.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Acciones sobre las tarjetas (editar / eliminar) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const carga = cargasCache.find((c) => String(c.id) === id);
    if (!carga) return;

    if (action === "editar") {
      openForm(carga);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar carga",
        text: `¿Seguro que deseas eliminar la carga "${carga.descripcion}"? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/cargas/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar la carga.");
          }
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  [inputBuscar, filtroEstado].forEach((el) => {
    el.addEventListener("input", render);
    el.addEventListener("change", render);
  });

  session = trailersysGetSession();
  render();
})();
