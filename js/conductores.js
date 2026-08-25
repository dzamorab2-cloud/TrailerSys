(function () {
  const ESTADOS = ["Disponible", "En Ruta", "Descanso", "Inactivo"];
  const ESTADO_BADGE = {
    Disponible: "badge-success",
    "En Ruta": "badge-info",
    Descanso: "badge-warning",
    Inactivo: "badge-neutral",
  };
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  // Cache del ultimo listado cargado desde la API, para que los botones de
  // editar/eliminar de cada tarjeta no dependan de una segunda peticion.
  let conductoresCache = [];
  let currentPage = 0;
  let pageMeta = null;

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoConductor");
  const grid = document.getElementById("conductorGrid");
  const emptyState = document.getElementById("conductorEmptyState");
  const emptyTitle = document.getElementById("conductorEmptyTitle");
  const emptyText = document.getElementById("conductorEmptyText");
  const resultsCount = document.getElementById("conductorResultsCount");

  const inputBuscar = document.getElementById("conductorBuscar");
  const filtroEstado = document.getElementById("conductorFiltroEstado");

  const modalOverlay = document.getElementById("conductorModalOverlay");
  const modalTitle = document.getElementById("conductorModalTitle");
  const form = document.getElementById("conductorForm");
  const btnCerrarModal = document.getElementById("conductorModalClose");
  const btnCancelar = document.getElementById("conductorCancelar");

  const inputId = document.getElementById("conductorId");
  const inputNombres = document.getElementById("conductorNombres");
  const inputIdentificacion = document.getElementById("conductorIdentificacion");
  const inputTelefono = document.getElementById("conductorTelefono");
  const inputCorreo = document.getElementById("conductorCorreo");
  const inputLicenciaNumero = document.getElementById("conductorLicenciaNumero");
  const inputLicenciaCategoria = document.getElementById("conductorLicenciaCategoria");
  const inputLicenciaVencimiento = document.getElementById("conductorLicenciaVencimiento");
  const selectEstado = document.getElementById("conductorEstado");
  const selectVehiculo = document.getElementById("conductorVehiculo");
  const inputObservaciones = document.getElementById("conductorObservaciones");

  const inputFoto = document.getElementById("conductorFoto");
  const fotoPreview = document.getElementById("conductorFotoPreview");
  const btnQuitarFoto = document.getElementById("conductorFotoQuitar");

  let fotoActual = "";
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
    ["fieldConductorNombres", "fieldConductorIdentificacion", "fieldConductorTelefono",
      "fieldConductorCorreo", "fieldConductorLicenciaNumero", "fieldConductorLicenciaCategoria",
      "fieldConductorLicenciaVencimiento"]
      .forEach((id) => setFieldError(id, ""));
  }

  function setFotoPreview(dataUrl) {
    fotoActual = dataUrl || "";
    if (fotoActual) {
      fotoPreview.innerHTML = `<img src="${fotoActual}" alt="Vista previa del conductor" />`;
      btnQuitarFoto.hidden = false;
    } else {
      fotoPreview.innerHTML = `<i class="bi bi-person"></i>`;
      btnQuitarFoto.hidden = true;
    }
  }

  async function refreshVehiculoOptions() {
    let vehiculos;
    try {
      vehiculos = (await trailersysPagedRequest("vehiculos", 0, 100)).content;
    } catch {
      vehiculos = [];
    }
    const current = selectVehiculo.value;
    selectVehiculo.innerHTML = '<option value="">Sin asignar</option>';
    vehiculos.forEach((v) => {
      const option = document.createElement("option");
      option.value = v.id;
      option.textContent = `${v.placa} · ${v.marca} ${v.modelo}`;
      selectVehiculo.appendChild(option);
    });
    if (vehiculos.some((v) => String(v.id) === current)) selectVehiculo.value = current;
  }

  function renderCard(conductor, canManage) {
    const badgeClass = ESTADO_BADGE[conductor.estado] || "badge-neutral";
    const avatarContent = conductor.foto
      ? `<img src="${conductor.foto}" alt="Foto de ${escapeHtml(conductor.nombres)}" />`
      : initials(conductor.nombres);

    const licenciaBadge = conductor.licenciaVencida
      ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Licencia vencida</span>`
      : "";

    const actions = canManage
      ? `<div class="person-actions">
          <button type="button" class="icon-btn" data-action="editar" data-id="${conductor.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${conductor.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>
        </div>`
      : "";

    return `
      <article class="card person-card">
        <div class="person-header">
          <div class="person-avatar">${avatarContent}</div>
          <div class="person-identity">
            <div class="person-name">${escapeHtml(conductor.nombres)}</div>
            <div class="person-id">${escapeHtml(conductor.identificacion)}</div>
          </div>
        </div>
        <div class="person-status-row">
          <span class="badge ${badgeClass}">${escapeHtml(conductor.estado)}</span>
          ${licenciaBadge}
        </div>
        <div class="person-meta">
          <span><i class="bi bi-telephone"></i>${escapeHtml(conductor.telefono)}</span>
          ${conductor.correo ? `<span><i class="bi bi-envelope"></i>${escapeHtml(conductor.correo)}</span>` : ""}
          <span><i class="bi bi-card-checklist"></i>${escapeHtml(conductor.licenciaNumero)} · ${escapeHtml(conductor.licenciaCategoria)}</span>
          <span><i class="bi bi-calendar-x"></i>Vence ${escapeHtml(conductor.licenciaVencimiento)}</span>
          ${conductor.vehiculoPlaca ? `<span><i class="bi bi-truck"></i>${escapeHtml(conductor.vehiculoPlaca)}</span>` : ""}
        </div>
        ${actions}
      </article>`;
  }

  async function render() {
    const canManage = trailersysCanManage(session, "conductores");
    btnNuevo.hidden = !canManage;

    let conductores;
    try {
      const params = new URLSearchParams({
        page: currentPage,
        size: 24,
        search: inputBuscar.value.trim(),
      });
      if (filtroEstado.value) params.set("estado", filtroEstado.value);
      pageMeta = await trailersysApiRequest("GET", `/paginas/conductores?${params}`);
      conductores = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar los conductores";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    conductoresCache = conductores;

    const filtrados = conductores;

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (conductores.length === 0) {
        emptyTitle.textContent = "Todavía no hay conductores registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo conductor" para registrar el primero.'
          : "Cuando se registren conductores, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún conductor coincide con la búsqueda o el filtro aplicado.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${Number(pageMeta.totalElements).toLocaleString("es-EC")} conductor${pageMeta.totalElements === 1 ? "" : "es"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((c) => renderCard(c, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  async function openForm(conductor) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Disponible";
    await refreshVehiculoOptions();

    if (conductor) {
      modalTitle.textContent = "Editar conductor";
      inputId.value = conductor.id;
      inputNombres.value = conductor.nombres;
      inputIdentificacion.value = conductor.identificacion;
      inputTelefono.value = conductor.telefono;
      inputCorreo.value = conductor.correo || "";
      inputLicenciaNumero.value = conductor.licenciaNumero;
      inputLicenciaCategoria.value = conductor.licenciaCategoria;
      inputLicenciaVencimiento.value = conductor.licenciaVencimiento;
      selectEstado.value = conductor.estado;
      selectVehiculo.value = conductor.vehiculoId || "";
      inputObservaciones.value = conductor.observaciones || "";
      setFotoPreview(conductor.foto);
    } else {
      modalTitle.textContent = "Nuevo conductor";
      inputId.value = "";
      setFotoPreview("");
    }

    trailersysOpenModal(modalOverlay);
    inputNombres.focus();
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

  inputFoto.addEventListener("change", () => {
    const file = inputFoto.files[0];
    if (!file) return;
    if (file.size > MAX_PHOTO_BYTES) {
      alert("La imagen es muy grande. El tamaño máximo permitido es 3 MB.");
      inputFoto.value = "";
      return;
    }
    const reader = new FileReader();
    reader.onload = () => setFotoPreview(reader.result);
    reader.readAsDataURL(file);
  });

  btnQuitarFoto.addEventListener("click", () => {
    inputFoto.value = "";
    setFotoPreview("");
  });

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.nombres) fail("fieldConductorNombres", "El nombre es obligatorio.");
    if (!data.identificacion) fail("fieldConductorIdentificacion", "La identificación es obligatoria.");
    if (!data.telefono) fail("fieldConductorTelefono", "El teléfono es obligatorio.");

    if (data.correo && !EMAIL_REGEX.test(data.correo)) {
      fail("fieldConductorCorreo", "Ingresa un correo válido.");
    }

    if (!data.licenciaNumero) fail("fieldConductorLicenciaNumero", "El número de licencia es obligatorio.");
    if (!data.licenciaCategoria) fail("fieldConductorLicenciaCategoria", "La categoría es obligatoria.");
    if (!data.licenciaVencimiento) fail("fieldConductorLicenciaVencimiento", "La fecha de vencimiento es obligatoria.");

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      nombres: inputNombres.value.trim(),
      identificacion: inputIdentificacion.value.trim(),
      telefono: inputTelefono.value.trim(),
      correo: inputCorreo.value.trim(),
      licenciaNumero: inputLicenciaNumero.value.trim(),
      licenciaCategoria: inputLicenciaCategoria.value.trim(),
      licenciaVencimiento: inputLicenciaVencimiento.value,
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      vehiculoId: selectVehiculo.value ? Number(selectVehiculo.value) : null,
      observaciones: inputObservaciones.value.trim(),
      foto: fotoActual,
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/conductores/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/conductores", data);
      }
      closeForm();
      await render();
    } catch (error) {
      if (/identificaci/i.test(error.message || "")) {
        setFieldError("fieldConductorIdentificacion", error.message);
      } else {
        alert(error.message || "No se pudo guardar el conductor.");
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
    const conductor = conductoresCache.find((c) => String(c.id) === id);
    if (!conductor) return;

    if (action === "editar") {
      openForm(conductor);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar conductor",
        text: `¿Seguro que deseas eliminar a ${conductor.nombres}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/conductores/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar el conductor.");
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
