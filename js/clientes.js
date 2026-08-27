(function () {
  const ESTADO_BADGE = {
    Activo: "badge-success",
    Inactivo: "badge-neutral",
  };
  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // Cache del ultimo listado cargado desde la API, para que los botones de
  // editar/eliminar de cada tarjeta no dependan de una segunda peticion.
  let clientesCache = [];
  let currentPage = 0;
  let pageMeta = null;

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoCliente");
  const grid = document.getElementById("clienteGrid");
  const emptyState = document.getElementById("clienteEmptyState");
  const emptyTitle = document.getElementById("clienteEmptyTitle");
  const emptyText = document.getElementById("clienteEmptyText");
  const resultsCount = document.getElementById("clienteResultsCount");

  const inputBuscar = document.getElementById("clienteBuscar");
  const filtroEstado = document.getElementById("clienteFiltroEstado");

  const modalOverlay = document.getElementById("clienteModalOverlay");
  const modalTitle = document.getElementById("clienteModalTitle");
  const form = document.getElementById("clienteForm");
  const btnCerrarModal = document.getElementById("clienteModalClose");
  const btnCancelar = document.getElementById("clienteCancelar");

  const inputId = document.getElementById("clienteId");
  const inputNombre = document.getElementById("clienteNombre");
  const inputIdentificacion = document.getElementById("clienteIdentificacion");
  const selectEstado = document.getElementById("clienteEstado");
  const inputTelefono = document.getElementById("clienteTelefono");
  const inputCorreo = document.getElementById("clienteCorreo");
  const inputDireccion = document.getElementById("clienteDireccion");
  const inputServicios = document.getElementById("clienteServicios");
  const inputObservaciones = document.getElementById("clienteObservaciones");

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

  function initials(name) {
    return name
      .trim()
      .split(/\s+/)
      .slice(0, 2)
      .map((part) => part[0])
      .join("")
      .toUpperCase();
  }

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldClienteNombre", "fieldClienteIdentificacion", "fieldClienteTelefono",
      "fieldClienteCorreo", "fieldClienteDireccion"]
      .forEach((id) => setFieldError(id, ""));
  }

  function serviciosTags(servicios) {
    if (!servicios) return "";
    const tags = servicios.split(",").map((s) => s.trim()).filter(Boolean);
    if (tags.length === 0) return "";
    return `<div class="tag-list">${tags.map((tag) => `<span class="tag-pill">${escapeHtml(tag)}</span>`).join("")}</div>`;
  }

  function renderCard(cliente, canManage) {
    const badgeClass = ESTADO_BADGE[cliente.estado] || "badge-neutral";

    const actions = canManage
      ? `<div class="person-actions">
          <button type="button" class="icon-btn" data-action="editar" data-id="${cliente.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${cliente.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>
        </div>`
      : "";

    return `
      <article class="card person-card">
        <div class="person-header">
          <div class="person-avatar">${initials(cliente.nombre)}</div>
          <div class="person-identity">
            <div class="person-name">${escapeHtml(cliente.nombre)}</div>
            <div class="person-id">${escapeHtml(cliente.identificacion)}</div>
          </div>
        </div>
        <div class="person-status-row">
          <span class="badge ${badgeClass}">${escapeHtml(cliente.estado)}</span>
        </div>
        <div class="person-meta">
          <span><i class="bi bi-telephone"></i>${escapeHtml(cliente.telefono)}</span>
          ${cliente.correo ? `<span><i class="bi bi-envelope"></i>${escapeHtml(cliente.correo)}</span>` : ""}
          <span><i class="bi bi-geo-alt"></i>${escapeHtml(cliente.direccion)}</span>
        </div>
        ${serviciosTags(cliente.servicios)}
        ${actions}
      </article>`;
  }

  async function render() {
    const canManage = trailersysCanManage(session, "clientes");
    btnNuevo.hidden = !canManage;

    let clientes;
    try {
      pageMeta = await trailersysPagedRequest("clientes", currentPage, 24, {
        search: inputBuscar.value.trim(),
        estado: filtroEstado.value,
      });
      clientes = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los clientes";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    clientesCache = clientes;

    const filtrados = clientes;

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (clientes.length === 0) {
        emptyTitle.textContent = "Todavía no hay clientes registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo cliente" para registrar el primero.'
          : "Cuando se registren clientes, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún cliente coincide con la búsqueda o el filtro aplicado.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${Number(pageMeta.totalElements).toLocaleString("es-EC")} cliente${pageMeta.totalElements === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((c) => renderCard(c, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  function openForm(cliente) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Activo";

    if (cliente) {
      modalTitle.textContent = "Editar cliente";
      inputId.value = cliente.id;
      inputNombre.value = cliente.nombre;
      inputIdentificacion.value = cliente.identificacion;
      selectEstado.value = cliente.estado;
      inputTelefono.value = cliente.telefono;
      inputCorreo.value = cliente.correo || "";
      inputDireccion.value = cliente.direccion;
      inputServicios.value = cliente.servicios || "";
      inputObservaciones.value = cliente.observaciones || "";
    } else {
      modalTitle.textContent = "Nuevo cliente";
      inputId.value = "";
    }

    trailersysOpenModal(modalOverlay);
    inputNombre.focus();
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

    if (!data.nombre) fail("fieldClienteNombre", "El nombre o razón social es obligatorio.");
    if (!data.identificacion) fail("fieldClienteIdentificacion", "La identificación es obligatoria.");
    if (!data.telefono) fail("fieldClienteTelefono", "El teléfono es obligatorio.");

    if (data.correo && !EMAIL_REGEX.test(data.correo)) {
      fail("fieldClienteCorreo", "Ingresa un correo válido.");
    }

    if (!data.direccion) fail("fieldClienteDireccion", "La dirección es obligatoria.");

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      nombre: inputNombre.value.trim(),
      identificacion: inputIdentificacion.value.trim(),
      estado: selectEstado.value === "Inactivo" ? "Inactivo" : "Activo",
      telefono: inputTelefono.value.trim(),
      correo: inputCorreo.value.trim(),
      direccion: inputDireccion.value.trim(),
      servicios: inputServicios.value.trim(),
      observaciones: inputObservaciones.value.trim(),
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/clientes/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/clientes", data);
      }
      closeForm();
      await render();
    } catch (error) {
      if (/identificaci/i.test(error.message || "")) {
        setFieldError("fieldClienteIdentificacion", error.message);
      } else {
        alert(error.message || "No se pudo guardar el cliente.");
      }
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Acciones sobre las tarjetas (editar / eliminar) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const cliente = clientesCache.find((c) => String(c.id) === id);
    if (!cliente) return;

    if (action === "editar") {
      openForm(cliente);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar cliente",
        text: `¿Seguro que deseas eliminar a ${cliente.nombre}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/clientes/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar el cliente.");
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

  session = trailersysGetSession();
  render();
})();
