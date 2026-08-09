(function () {
  const COLLECTION = "vehiculos";
  const ESTADOS = ["Disponible", "En Ruta", "Mantenimiento", "Fuera de Servicio"];
  const ESTADO_BADGE = {
    Disponible: "badge-success",
    "En Ruta": "badge-info",
    Mantenimiento: "badge-warning",
    "Fuera de Servicio": "badge-danger",
  };
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;

  trailersysSeedIfEmpty(COLLECTION, [
    {
      id: "vehiculo-seed-1",
      placa: "PBA-1234",
      marca: "Freightliner",
      modelo: "Cascadia",
      tipo: "Tráiler",
      anio: 2021,
      color: "Blanco",
      estado: "Disponible",
      kilometraje: 82000,
      capacidad: 28000,
      observaciones: "Unidad principal para rutas de larga distancia.",
      foto: "",
    },
    {
      id: "vehiculo-seed-2",
      placa: "PCD-5678",
      marca: "Hino",
      modelo: "300",
      tipo: "Camión",
      anio: 2019,
      color: "Azul",
      estado: "En Ruta",
      kilometraje: 145000,
      capacidad: 6500,
      observaciones: "",
      foto: "",
    },
    {
      id: "vehiculo-seed-3",
      placa: "PEF-9012",
      marca: "Chevrolet",
      modelo: "NPR",
      tipo: "Furgón",
      anio: 2017,
      color: "Gris",
      estado: "Mantenimiento",
      kilometraje: 201500,
      capacidad: 4000,
      observaciones: "Revisión de frenos programada.",
      foto: "",
    },
  ]);

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoVehiculo");
  const grid = document.getElementById("vehiculoGrid");
  const emptyState = document.getElementById("vehiculoEmptyState");
  const emptyTitle = document.getElementById("vehiculoEmptyTitle");
  const emptyText = document.getElementById("vehiculoEmptyText");
  const resultsCount = document.getElementById("vehiculoResultsCount");

  const inputBuscar = document.getElementById("vehiculoBuscar");
  const filtroEstado = document.getElementById("vehiculoFiltroEstado");
  const filtroTipo = document.getElementById("vehiculoFiltroTipo");
  const filtroMarca = document.getElementById("vehiculoFiltroMarca");

  const modalOverlay = document.getElementById("vehiculoModalOverlay");
  const modalTitle = document.getElementById("vehiculoModalTitle");
  const form = document.getElementById("vehiculoForm");
  const btnCerrarModal = document.getElementById("vehiculoModalClose");
  const btnCancelar = document.getElementById("vehiculoCancelar");

  const inputId = document.getElementById("vehiculoId");
  const inputPlaca = document.getElementById("vehiculoPlaca");
  const inputMarca = document.getElementById("vehiculoMarca");
  const inputModelo = document.getElementById("vehiculoModelo");
  const inputTipo = document.getElementById("vehiculoTipo");
  const inputAnio = document.getElementById("vehiculoAnio");
  const inputColor = document.getElementById("vehiculoColor");
  const selectEstado = document.getElementById("vehiculoEstado");
  const inputKilometraje = document.getElementById("vehiculoKilometraje");
  const inputCapacidad = document.getElementById("vehiculoCapacidad");
  const inputObservaciones = document.getElementById("vehiculoObservaciones");

  const inputFoto = document.getElementById("vehiculoFoto");
  const fotoPreview = document.getElementById("vehiculoFotoPreview");
  const btnQuitarFoto = document.getElementById("vehiculoFotoQuitar");

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

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldVehiculoPlaca", "fieldVehiculoMarca", "fieldVehiculoModelo", "fieldVehiculoTipo",
      "fieldVehiculoAnio", "fieldVehiculoColor", "fieldVehiculoKilometraje", "fieldVehiculoCapacidad"]
      .forEach((id) => setFieldError(id, ""));
  }

  function setFotoPreview(dataUrl) {
    fotoActual = dataUrl || "";
    if (fotoActual) {
      fotoPreview.innerHTML = `<img src="${fotoActual}" alt="Vista previa del vehículo" />`;
      btnQuitarFoto.hidden = false;
    } else {
      fotoPreview.innerHTML = `<i class="bi bi-truck"></i>`;
      btnQuitarFoto.hidden = true;
    }
  }

  // --- Filtros dinamicos segun los datos existentes ---
  function refreshFilterOptions(vehiculos) {
    function fillSelect(select, values) {
      const current = select.value;
      const firstOption = select.querySelector("option");
      select.innerHTML = "";
      select.appendChild(firstOption);
      values.forEach((value) => {
        const option = document.createElement("option");
        option.value = value;
        option.textContent = value;
        select.appendChild(option);
      });
      if (values.includes(current)) select.value = current;
    }

    const tipos = [...new Set(vehiculos.map((v) => v.tipo).filter(Boolean))].sort();
    const marcas = [...new Set(vehiculos.map((v) => v.marca).filter(Boolean))].sort();
    fillSelect(filtroTipo, tipos);
    fillSelect(filtroMarca, marcas);
  }

  function renderCard(vehiculo, canManage) {
    const badgeClass = ESTADO_BADGE[vehiculo.estado] || "badge-neutral";
    const photoContent = vehiculo.foto
      ? `<img src="${vehiculo.foto}" alt="Foto de ${escapeHtml(vehiculo.placa)}" />`
      : `<i class="bi bi-truck"></i>`;

    const actions = canManage
      ? `<div class="vehicle-actions">
          <button type="button" class="icon-btn" data-action="editar" data-id="${vehiculo.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${vehiculo.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>
        </div>`
      : "";

    return `
      <article class="card vehicle-card">
        <div class="vehicle-photo">${photoContent}</div>
        <div class="vehicle-body">
          <div class="vehicle-plate-row">
            <span class="vehicle-plate">${escapeHtml(vehiculo.placa)}</span>
            <span class="badge ${badgeClass}">${escapeHtml(vehiculo.estado)}</span>
          </div>
          <span class="vehicle-model">${escapeHtml(vehiculo.marca)} ${escapeHtml(vehiculo.modelo)} · ${escapeHtml(vehiculo.anio)}</span>
          <div class="vehicle-meta">
            <span><i class="bi bi-truck-front"></i>${escapeHtml(vehiculo.tipo)}</span>
            <span><i class="bi bi-palette"></i>${escapeHtml(vehiculo.color)}</span>
            <span><i class="bi bi-speedometer2"></i>${Number(vehiculo.kilometraje).toLocaleString("es-EC")} km</span>
            <span><i class="bi bi-box-seam"></i>${Number(vehiculo.capacidad).toLocaleString("es-EC")} kg</span>
          </div>
          ${vehiculo.observaciones ? `<p class="vehicle-observations">${escapeHtml(vehiculo.observaciones)}</p>` : ""}
          ${actions}
        </div>
      </article>`;
  }

  function render() {
    const vehiculos = trailersysList(COLLECTION);
    refreshFilterOptions(vehiculos);

    const canManage = trailersysCanManage(session, COLLECTION);
    btnNuevo.hidden = !canManage;

    const search = inputBuscar.value.trim().toLowerCase();
    const estado = filtroEstado.value;
    const tipo = filtroTipo.value;
    const marca = filtroMarca.value;

    const filtrados = vehiculos.filter((v) => {
      const matchesSearch = !search
        || v.placa.toLowerCase().includes(search)
        || v.marca.toLowerCase().includes(search)
        || v.modelo.toLowerCase().includes(search);
      const matchesEstado = !estado || v.estado === estado;
      const matchesTipo = !tipo || v.tipo === tipo;
      const matchesMarca = !marca || v.marca === marca;
      return matchesSearch && matchesEstado && matchesTipo && matchesMarca;
    });

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      if (vehiculos.length === 0) {
        emptyTitle.textContent = "Todavía no hay vehículos registrados";
        emptyText.textContent = canManage
          ? 'Usa "Nuevo vehículo" para registrar el primero de la flota.'
          : "Cuando se registren vehículos, aparecerán aquí.";
      } else {
        emptyTitle.textContent = "Sin resultados";
        emptyText.textContent = "Ningún vehículo coincide con la búsqueda o los filtros aplicados.";
      }
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${filtrados.length} de ${vehiculos.length} vehículo${vehiculos.length === 1 ? "" : "s"}`;
    grid.innerHTML = filtrados.map((v) => renderCard(v, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  function openForm(vehiculo) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Disponible";

    if (vehiculo) {
      modalTitle.textContent = "Editar vehículo";
      inputId.value = vehiculo.id;
      inputPlaca.value = vehiculo.placa;
      inputMarca.value = vehiculo.marca;
      inputModelo.value = vehiculo.modelo;
      inputTipo.value = vehiculo.tipo;
      inputAnio.value = vehiculo.anio;
      inputColor.value = vehiculo.color;
      selectEstado.value = vehiculo.estado;
      inputKilometraje.value = vehiculo.kilometraje;
      inputCapacidad.value = vehiculo.capacidad;
      inputObservaciones.value = vehiculo.observaciones || "";
      setFotoPreview(vehiculo.foto);
    } else {
      modalTitle.textContent = "Nuevo vehículo";
      inputId.value = "";
      setFotoPreview("");
    }

    trailersysOpenModal(modalOverlay);
    inputPlaca.focus();
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

    if (!data.placa) {
      fail("fieldVehiculoPlaca", "La placa es obligatoria.");
    } else {
      const duplicada = trailersysList(COLLECTION).some(
        (v) => v.id !== currentId && v.placa.toLowerCase() === data.placa.toLowerCase()
      );
      if (duplicada) fail("fieldVehiculoPlaca", "Ya existe un vehículo con esta placa.");
    }

    if (!data.marca) fail("fieldVehiculoMarca", "La marca es obligatoria.");
    if (!data.modelo) fail("fieldVehiculoModelo", "El modelo es obligatorio.");
    if (!data.tipo) fail("fieldVehiculoTipo", "El tipo es obligatorio.");
    if (!data.color) fail("fieldVehiculoColor", "El color es obligatorio.");

    const currentYear = new Date().getFullYear();
    if (!data.anio || Number.isNaN(data.anio) || data.anio < 1980 || data.anio > currentYear + 1) {
      fail("fieldVehiculoAnio", `Ingresa un año entre 1980 y ${currentYear + 1}.`);
    }

    if (data.kilometraje === "" || Number.isNaN(data.kilometraje) || data.kilometraje < 0) {
      fail("fieldVehiculoKilometraje", "Ingresa un kilometraje válido.");
    }

    if (data.capacidad === "" || Number.isNaN(data.capacidad) || data.capacidad < 0) {
      fail("fieldVehiculoCapacidad", "Ingresa una capacidad válida.");
    }

    return valid;
  }

  form.addEventListener("submit", (event) => {
    event.preventDefault();

    const data = {
      placa: inputPlaca.value.trim(),
      marca: inputMarca.value.trim(),
      modelo: inputModelo.value.trim(),
      tipo: inputTipo.value.trim(),
      anio: Number(inputAnio.value),
      color: inputColor.value.trim(),
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      kilometraje: inputKilometraje.value === "" ? "" : Number(inputKilometraje.value),
      capacidad: inputCapacidad.value === "" ? "" : Number(inputCapacidad.value),
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
    const vehiculo = trailersysList(COLLECTION).find((v) => v.id === id);
    if (!vehiculo) return;

    if (action === "editar") {
      openForm(vehiculo);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar vehículo",
        text: `¿Seguro que deseas eliminar el vehículo con placa ${vehiculo.placa}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: () => {
          trailersysRemove(COLLECTION, id);
          render();
        },
      });
    }
  });

  // --- Busqueda y filtros ---
  [inputBuscar, filtroEstado, filtroTipo, filtroMarca].forEach((el) => {
    el.addEventListener("input", render);
    el.addEventListener("change", render);
  });

  session = trailersysGetSession();
  render();
})();
