(function () {
  const ESTADOS = ["Disponible", "En Ruta", "Mantenimiento", "Fuera de Servicio"];
  const ESTADO_BADGE = {
    Disponible: "badge-success",
    "En Ruta": "badge-info",
    Mantenimiento: "badge-warning",
    "Fuera de Servicio": "badge-danger",
  };
  const MAX_PHOTO_BYTES = 3 * 1024 * 1024;
  const MODELOS_POR_MARCA = {
    Freightliner: ["Cascadia 126", "Cascadia 116", "M2 106"],
    Kenworth: ["T680 Next Gen", "W900L", "T880"],
    Peterbilt: ["Model 579", "Model 389X", "Model 567"],
    "Volvo Trucks": ["VNL 860", "VNR 660", "VHD 300"],
    Mack: ["Anthem 70-inch", "Pinnacle 64T", "Granite 64FR"],
    International: ["LT625", "RH613", "MV607"],
    Scania: ["R 500", "S 650", "G 410"],
    "Mercedes-Benz": ["Actros 2645", "Arocs 3345", "Atego 1726"],
    MAN: ["TGX 26.510", "TGS 33.480", "TGM 18.290"],
    DAF: ["XF 480", "XG 530", "CF 450"],
    Iveco: ["S-Way AS440S", "Stralis 480", "Eurocargo ML180"],
    "Renault Trucks": ["T High 520", "T 480", "C 440"],
    "Western Star": ["49X 600", "57X 600", "47X 500"],
    Isuzu: ["FVR 34K", "NPR 75L", "Giga CYZ"],
    Hino: ["Dutro 616", "500 FC", "700 SS"],
    Fuso: ["Canter 815", "Fighter 1627", "Super Great 6R20"],
    "UD Trucks": ["Quon GW", "Croner PKE", "Quester GWE"],
    Sinotruk: ["HOWO T7H 540", "HOWO TX 440", "HOWO A7 420"],
    Shacman: ["X6000 550", "X3000 430", "F3000 385"],
    JAC: ["Gallop K7 540", "Gallop K5 420", "N90"],
  };

  // Cache del ultimo listado cargado desde la API, para que los botones de
  // editar/eliminar de cada tarjeta no dependan de una segunda peticion.
  let vehiculosCache = [];
  let currentPage = 0;
  let pageMeta = null;

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
  const inputColorPersonalizado = document.getElementById("vehiculoColorPersonalizado");
  const selectEstado = document.getElementById("vehiculoEstado");
  const inputKilometraje = document.getElementById("vehiculoKilometraje");
  const inputCapacidad = document.getElementById("vehiculoCapacidad");
  const selectCapacidadUnidad = document.getElementById("vehiculoCapacidadUnidad");
  const inputObservaciones = document.getElementById("vehiculoObservaciones");

  const inputFoto = document.getElementById("vehiculoFoto");
  const fotoPreview = document.getElementById("vehiculoFotoPreview");
  const btnQuitarFoto = document.getElementById("vehiculoFotoQuitar");

  let fotoActual = "";
  let session = null;
  let capacidadUnidadAnterior = "kg";

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

  function actualizarModelos(modeloSeleccionado = "") {
    const modelos = MODELOS_POR_MARCA[inputMarca.value] || [];
    inputModelo.innerHTML = `<option value="">${modelos.length ? "Selecciona un modelo" : "Primero selecciona una marca"}</option>`;
    modelos.forEach((modelo) => {
      const option = document.createElement("option");
      option.value = modelo;
      option.textContent = modelo;
      inputModelo.appendChild(option);
    });
    inputModelo.disabled = modelos.length === 0;
    if (modelos.includes(modeloSeleccionado)) inputModelo.value = modeloSeleccionado;
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

    const actions = `<div class="vehicle-actions">
          <button type="button" class="icon-btn" data-action="guia" data-id="${vehiculo.id}" title="Ver e imprimir guía del vehículo"><i class="bi bi-file-earmark-text"></i></button>
          ${canManage ? `
          <button type="button" class="icon-btn" data-action="editar" data-id="${vehiculo.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${vehiculo.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>` : ""}
        </div>`;

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
            <span><i class="bi bi-box-seam"></i>${formatPesoDoble(vehiculo.capacidad)}</span>
          </div>
          ${vehiculo.observaciones ? `<p class="vehicle-observations">${escapeHtml(vehiculo.observaciones)}</p>` : ""}
          ${actions}
        </div>
      </article>`;
  }

  async function showVehiculoGuide(vehiculo) {
    const conductor = await trailersysApiRequest("GET", `/conductores/por-vehiculo/${vehiculo.id}`).catch(() => null);
    trailersysShowGuide({ tipo: "Vehículo", codigo: "VEH", id: vehiculo.id, estado: vehiculo.estado, secciones: [
      { titulo: "Ficha técnica", icono: "bi-truck", campos: [["Placa", vehiculo.placa], ["Marca", vehiculo.marca], ["Modelo", vehiculo.modelo], ["Tipo", vehiculo.tipo], ["Año", vehiculo.anio], ["Color", vehiculo.color]] },
      { titulo: "Operación", icono: "bi-speedometer2", campos: [["Kilometraje", `${Number(vehiculo.kilometraje).toLocaleString("es-EC")} km`], ["Capacidad", formatPesoDoble(vehiculo.capacidad)], ["Estado", vehiculo.estado], ["Observaciones", vehiculo.observaciones || "Sin observaciones"]] },
      { titulo: "Conductor asignado", icono: "bi-person-badge", campos: [["Nombre", conductor?.nombres || "Sin conductor asignado"], ["Identificación", conductor?.identificacion], ["Licencia", conductor?.licenciaNumero], ["Categoría", conductor?.licenciaCategoria], ["Vencimiento", conductor?.licenciaVencimiento]] }
    ] });
  }

  async function render() {
    const canManage = trailersysCanManage(session, "vehiculos");
    btnNuevo.hidden = !canManage;

    let vehiculos;
    try {
      pageMeta = await trailersysPagedRequest("vehiculos", currentPage, 24);
      vehiculos = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudo cargar la flota";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    vehiculosCache = vehiculos;
    refreshFilterOptions(vehiculos);

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
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((v) => renderCard(v, canManage)).join("");
  }

  // --- Modal de alta / edicion ---
  function openForm(vehiculo) {
    clearFieldErrors();
    form.reset();
    actualizarModelos();
    selectEstado.value = "Disponible";
    selectCapacidadUnidad.value = "kg";
    capacidadUnidadAnterior = "kg";
    inputColorPersonalizado.hidden = true;

    if (vehiculo) {
      modalTitle.textContent = "Editar vehículo";
      inputId.value = vehiculo.id;
      inputPlaca.value = vehiculo.placa;
      inputMarca.value = vehiculo.marca;
      actualizarModelos(vehiculo.modelo);
      inputTipo.value = vehiculo.tipo;
      inputAnio.value = vehiculo.anio;
      const opcionColor = [...inputColor.options].some((opcion) => opcion.value === vehiculo.color);
      inputColor.value = opcionColor ? vehiculo.color : "__personalizado__";
      if (!opcionColor) {
        inputColorPersonalizado.value = /^#[0-9a-f]{6}$/i.test(vehiculo.color) ? vehiculo.color : "#ffffff";
        inputColorPersonalizado.hidden = false;
      }
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

  inputMarca.addEventListener("change", () => actualizarModelos());

  inputColor.addEventListener("change", () => {
    inputColorPersonalizado.hidden = inputColor.value !== "__personalizado__";
  });

  selectCapacidadUnidad.addEventListener("change", () => {
    const valor = Number(inputCapacidad.value);
    if (!Number.isNaN(valor) && inputCapacidad.value !== "") {
      const kg = valorEnKg(valor, capacidadUnidadAnterior);
      inputCapacidad.value = (selectCapacidadUnidad.value === "lb" ? kgToLb(kg) : kg).toFixed(2).replace(/\.00$/, "");
    }
    capacidadUnidadAnterior = selectCapacidadUnidad.value;
  });

  // --- Validacion y guardado ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.placa) fail("fieldVehiculoPlaca", "La placa es obligatoria.");
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

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      placa: inputPlaca.value.trim(),
      marca: inputMarca.value.trim(),
      modelo: inputModelo.value.trim(),
      tipo: inputTipo.value.trim(),
      anio: Number(inputAnio.value),
      color: inputColor.value === "__personalizado__" ? inputColorPersonalizado.value : inputColor.value,
      estado: ESTADOS.includes(selectEstado.value) ? selectEstado.value : ESTADOS[0],
      kilometraje: inputKilometraje.value === "" ? "" : Number(inputKilometraje.value),
      capacidad: inputCapacidad.value === "" ? "" : Math.round(valorEnKg(inputCapacidad.value, selectCapacidadUnidad.value)),
      observaciones: inputObservaciones.value.trim(),
      foto: fotoActual,
    };

    if (!validate(data)) return;

    const id = inputId.value || null;
    submitBtn.disabled = true;
    try {
      if (id) {
        await trailersysApiRequest("PUT", `/vehiculos/${id}`, data);
      } else {
        await trailersysApiRequest("POST", "/vehiculos", data);
      }
      closeForm();
      await render();
    } catch (error) {
      if (/placa/i.test(error.message || "")) {
        setFieldError("fieldVehiculoPlaca", error.message);
      } else {
        alert(error.message || "No se pudo guardar el vehículo.");
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
    const vehiculo = vehiculosCache.find((v) => String(v.id) === id);
    if (!vehiculo) return;

    if (action === "guia") {
      showVehiculoGuide(vehiculo).catch((error) => alert(error.message || "No se pudo generar la guía."));
    } else if (action === "editar") {
      openForm(vehiculo);
    } else if (action === "eliminar") {
      trailersysConfirm({
        title: "Eliminar vehículo",
        text: `¿Seguro que deseas eliminar el vehículo con placa ${vehiculo.placa}? Esta acción no se puede deshacer.`,
        acceptLabel: "Eliminar",
        onAccept: async () => {
          try {
            await trailersysApiRequest("DELETE", `/vehiculos/${id}`);
            await render();
          } catch (error) {
            alert(error.message || "No se pudo eliminar el vehículo.");
          }
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
  window.addEventListener("trailersys:data-changed", (event) => {
    if (event.detail?.resource === "mantenimientos") render();
  });
  window.addEventListener("trailersys:module-activated", (event) => {
    if (event.detail?.module === "vehiculos") render();
  });
  render();
})();
