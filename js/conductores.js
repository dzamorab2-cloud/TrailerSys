(function () {
  const COLLECTION = "conductores";
  const ESTADOS = ["Disponible", "En Ruta", "Descanso", "Inactivo"];
  const ESTADO_BADGE = {
    Disponible: "badge-success",
    "En Ruta": "badge-info",
    Descanso: "badge-warning",
    Inactivo: "badge-neutral",
  };
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
  const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

  function todayIso() {
    return new Date().toISOString().slice(0, 10);
  }

  trailersysSeedIfEmpty(COLLECTION, [
    {
      id: "conductor-seed-1",
      nombres: "Luis Herrera",
      identificacion: "0912345678",
      telefono: "0991234567",
      correo: "luis.herrera@trailersys.test",
      licenciaNumero: "LIC-88213",
      licenciaCategoria: "Tipo E",
      licenciaVencimiento: "2027-03-15",
      estado: "En Ruta",
      vehiculoId: "vehiculo-seed-1",
      observaciones: "",
      foto: "",
    },
    {
      id: "conductor-seed-2",
      nombres: "Marcia Torres",
      identificacion: "0923456789",
      telefono: "0987654321",
      correo: "",
      licenciaNumero: "LIC-40071",
      licenciaCategoria: "Tipo C",
      licenciaVencimiento: "2024-01-10",
      estado: "Disponible",
      vehiculoId: "",
      observaciones: "Disponible para rutas cortas.",
      foto: "",
    },
  ]);

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

  function refreshVehiculoOptions() {
    const vehiculos = trailersysList("vehiculos");
    const current = selectVehiculo.value;
    selectVehiculo.innerHTML = '<option value="">Sin asignar</option>';
    vehiculos.forEach((v) => {
      const option = document.createElement("option");
      option.value = v.id;
      option.textContent = `${v.placa} · ${v.marca} ${v.modelo}`;
      selectVehiculo.appendChild(option);
    });
    if (vehiculos.some((v) => v.id === current)) selectVehiculo.value = current;
  }

  function renderCard(conductor, canManage) {
    const badgeClass = ESTADO_BADGE[conductor.estado] || "badge-neutral";
    const avatarContent = conductor.foto
      ? `<img src="${conductor.foto}" alt="Foto de ${escapeHtml(conductor.nombres)}" />`
      : initials(conductor.nombres);

    const vencida = conductor.licenciaVencimiento && conductor.licenciaVencimiento < todayIso();
    const licenciaBadge = vencida
      ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Licencia vencida</span>`
      : "";

    const vehiculo = conductor.vehiculoId
      ? trailersysList("vehiculos").find((v) => v.id === conductor.vehiculoId)
      : null;

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
          ${vehiculo ? `<span><i class="bi bi-truck"></i>${escapeHtml(vehiculo.placa)} · ${escapeHtml(vehiculo.marca)} ${escapeHtml(vehiculo.modelo)}</span>` : ""}
        </div>
        ${actions}
      </article>`;
  }

  function render() {
    const conductores = trailersysList(COLLECTION);

    const canManage = trailersysCanManage(session, COLLECTION);
    btnNuevo.hidden = !canManage;

    const search = inputBuscar.value.trim().toLowerCase();
    const estado = filtroEstado.value;

    const filtrados = conductores.filter((c) => {
      const matchesSearch = !search
        || c.nombres.toLowerCase().includes(search)
        || c.identificacion.toLowerCase().includes(search)
        || c.telefono.toLowerCase().includes(search);
      const matchesEstado = !estado || c.estado === estado;
      return matchesSearch && matchesEstado;
    });

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
    resultsCount.textContent = `${filtrados.length} de ${conductores.length} conductor${conductores.length === 1 ? "" : "es"}`;
    grid.innerHTML = filtrados.map((c) => renderCard(c, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  function openForm(conductor) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Disponible";
    refreshVehiculoOptions();

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
  function validate(data, currentId) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.nombres) fail("fieldConductorNombres", "El nombre es obligatorio.");

    if (!data.identificacion) {
      fail("fieldConductorIdentificacion", "La identificación es obligatoria.");
    } else {
      const duplicada = trailersysList(COLLECTION).some(
        (c) => c.id !== currentId && c.identificacion.toLowerCase() === data.identificacion.toLowerCase()
      );
      if (duplicada) fail("fieldConductorIdentificacion", "Ya existe un conductor con esta identificación.");
    }

    if (!data.telefono) fail("fieldConductorTelefono", "El teléfono es obligatorio.");

    if (data.correo && !EMAIL_REGEX.test(data.correo)) {
      fail("fieldConductorCorreo", "Ingresa un correo válido.");
    }

    if (!data.licenciaNumero) fail("fieldConductorLicenciaNumero", "El número de licencia es obligatorio.");
    if (!data.licenciaCategoria) fail("fieldConductorLicenciaCategoria", "La categoría es obligatoria.");
    if (!data.licenciaVencimiento) fail("fieldConductorLicenciaVencimiento", "La fecha de vencimiento es obligatoria.");

    return valid;
  }

  form.addEventListener("submit", (event) => {
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
      vehiculoId: selectVehiculo.value,
      observaciones: inputObservaciones.value.trim(),
      foto: fotoActual,
    };

    if (!validate(data, inputId.value || null)) return;

    data.id = inputId.value || undefined;
    trailersysUpsert(COLLECTION, data);
    closeForm();
    render();
  });

  // --- Acciones sobre las tarjetas (editar / eliminar) ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest("button[data-action]");
    if (!button) return;
    const { action, id } = button.dataset;
    const conductor = trailersysList(COLLECTION).find((c) => c.id === id);
    if (!conductor) return;

    if (action === "editar") {
      openForm(conductor);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar conductor",
        text: `¿Seguro que deseas eliminar a ${conductor.nombres}? Esta acción no se puede deshacer.`,
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

  session = trailersysGetSession();
  render();
})();
