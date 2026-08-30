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
  // Celular ecuatoriano: 10 digitos que empiezan en 09.
  const TELEFONO_REGEX = /^09\d{8}$/;

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
  const inputVehiculo = document.getElementById("conductorVehiculo");
  const inputVehiculoBuscar = document.getElementById("conductorVehiculoBuscar");
  const resultadosVehiculo = document.getElementById("conductorVehiculoResultados");
  const inputObservaciones = document.getElementById("conductorObservaciones");

  // Solo digitos: evita que se puedan escribir letras o simbolos en estos
  // dos campos (ver trailersysSoloDigitos en ui-helpers.js).
  trailersysSoloDigitos(inputIdentificacion);
  trailersysSoloDigitos(inputTelefono, 10);

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
      fotoPreview.innerHTML = `<img src="${escapeHtml(fotoActual)}" alt="Vista previa del conductor" />`;
      btnQuitarFoto.hidden = false;
    } else {
      fotoPreview.innerHTML = `<i class="bi bi-person"></i>`;
      btnQuitarFoto.hidden = true;
    }
  }

  // Con decenas de miles de vehiculos reales, un <select> con una lista fija
  // es inutil. Se busca en el backend a medida que se escribe (ver
  // trailersysAutocomplete en ui-helpers.js).
  const vehiculoAutocomplete = trailersysAutocomplete({
    input: inputVehiculoBuscar,
    hidden: inputVehiculo,
    resultados: resultadosVehiculo,
    recurso: "vehiculos",
    etiqueta: (v) => `${v.placa} · ${v.marca} ${v.modelo}`,
    detalle: (v) => v.estado,
  });

  function renderCard(conductor, canManage) {
    const badgeClass = ESTADO_BADGE[conductor.estado] || "badge-neutral";
    const avatarContent = conductor.foto
      ? `<img src="${escapeHtml(conductor.foto)}" alt="Foto de ${escapeHtml(conductor.nombres)}" />`
      : initials(conductor.nombres);

    const licenciaBadge = conductor.licenciaVencida
      ? `<span class="badge badge-danger"><i class="bi bi-exclamation-triangle"></i> Licencia vencida</span>`
      : "";

    const actions = `<div class="person-actions">
          <button type="button" class="icon-btn" data-action="guia" data-id="${conductor.id}" title="Ver e imprimir guía del conductor"><i class="bi bi-file-earmark-person"></i></button>
          ${canManage ? `
          <button type="button" class="icon-btn" data-action="editar" data-id="${conductor.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${conductor.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>` : ""}
        </div>`;

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

  async function showConductorGuide(conductor) {
    const vehiculo = conductor.vehiculoId ? await trailersysApiRequest("GET", `/vehiculos/${conductor.vehiculoId}`).catch(() => null) : null;
    trailersysShowGuide({ tipo: "Conductor", codigo: "CON", id: conductor.id, estado: conductor.estado, secciones: [
      { titulo: "Datos personales", icono: "bi-person-vcard", campos: [["Nombre completo", conductor.nombres], ["Identificación", conductor.identificacion], ["Teléfono", conductor.telefono], ["Correo", conductor.correo || "No registrado"]] },
      { titulo: "Licencia profesional", icono: "bi-card-checklist", campos: [["Número", conductor.licenciaNumero], ["Categoría", conductor.licenciaCategoria], ["Vencimiento", conductor.licenciaVencimiento], ["Vigencia", conductor.licenciaVencida ? "Vencida" : "Vigente"]] },
      { titulo: "Asignación operativa", icono: "bi-truck", campos: [["Estado", conductor.estado], ["Vehículo", vehiculo ? `${vehiculo.marca} ${vehiculo.modelo}` : "Sin vehículo asignado"], ["Placa", vehiculo?.placa || conductor.vehiculoPlaca], ["Capacidad", vehiculo ? `${Number(vehiculo.capacidad).toLocaleString("es-EC")} kg` : "—"], ["Observaciones", conductor.observaciones || "Sin observaciones"]] }
    ] });
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
    vehiculoAutocomplete.ocultar();

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
      // El vehiculo ya viene denormalizado en el conductor (vehiculoPlaca),
      // asi que no hace falta otra peticion para mostrar la seleccion actual.
      inputVehiculo.value = conductor.vehiculoId || "";
      inputVehiculoBuscar.value = conductor.vehiculoPlaca || "";
      inputObservaciones.value = conductor.observaciones || "";
      setFotoPreview(conductor.foto);
    } else {
      modalTitle.textContent = "Nuevo conductor";
      inputId.value = "";
      inputVehiculo.value = "";
      inputVehiculoBuscar.value = "";
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
    if (!data.telefono) {
      fail("fieldConductorTelefono", "El teléfono es obligatorio.");
    } else if (!TELEFONO_REGEX.test(data.telefono)) {
      fail("fieldConductorTelefono", "El teléfono debe tener 10 dígitos y empezar con 09.");
    }

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
      vehiculoId: inputVehiculo.value ? Number(inputVehiculo.value) : null,
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

    if (action === "guia") {
      showConductorGuide(conductor).catch((error) => alert(error.message || "No se pudo generar la guía."));
    } else if (action === "editar") {
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
