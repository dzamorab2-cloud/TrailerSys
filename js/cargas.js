(function () {
  // Ver el mismo comentario en js/vehiculos.js.
  if (!["administrador", "coordinador"].includes(trailersysGetSession()?.role)) return;
  const ESTADOS = ["Pendiente", "Asignada", "En Tránsito", "Entregada", "Cancelada"];
  const ESTADO_BADGE = {
    Pendiente: "badge-warning",
    Asignada: "badge-info",
    "En Tránsito": "badge-neutral",
    Entregada: "badge-success",
    Cancelada: "badge-danger",
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
  const inputCliente = document.getElementById("cargaCliente");
  const inputClienteBuscar = document.getElementById("cargaClienteBuscar");
  const resultadosCliente = document.getElementById("cargaClienteResultados");
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

  // --- Buscador de cliente con autocompletado ---
  // Con decenas de miles de clientes reales, precargar una lista fija (como
  // hacia el <select> anterior) es inutil: casi nunca contiene al cliente
  // que se busca. En su lugar, se consulta al backend a medida que se
  // escribe (debounced) y se muestran solo las coincidencias.
  let clienteBuscarTimer;
  let clienteResultadosActuales = [];

  function limpiarSeleccionCliente() {
    inputCliente.value = "";
  }

  function ocultarResultadosCliente() {
    resultadosCliente.hidden = true;
    resultadosCliente.innerHTML = "";
    clienteResultadosActuales = [];
  }

  function seleccionarCliente(cliente) {
    inputCliente.value = cliente.id;
    inputClienteBuscar.value = cliente.nombre;
    setFieldError("fieldCargaCliente", "");
    ocultarResultadosCliente();
  }

  function renderResultadosCliente(clientes) {
    clienteResultadosActuales = clientes;
    if (!clientes.length) {
      resultadosCliente.innerHTML = '<div class="autocomplete-empty">Ningún cliente coincide con esa búsqueda.</div>';
      resultadosCliente.hidden = false;
      return;
    }
    resultadosCliente.innerHTML = clientes
      .map(
        (c, index) => `
      <div class="autocomplete-item" data-index="${index}">
        <span class="autocomplete-item-name">${escapeHtml(c.nombre)}</span>
        <span class="autocomplete-item-meta">${escapeHtml(c.identificacion)}${c.telefono ? " · " + escapeHtml(c.telefono) : ""}</span>
      </div>`
      )
      .join("");
    resultadosCliente.hidden = false;
  }

  async function buscarClientes(query) {
    try {
      const pagina = await trailersysPagedRequest("clientes", 0, 8, { search: query });
      // Las respuestas no llegan garantizadas en el mismo orden en que
      // salieron las peticiones: si se siguio escribiendo mientras esta
      // viajaba, puede llegar despues de una mas reciente y pisar resultados
      // validos con los de una busqueda vieja. Se descarta si el input ya
      // no coincide con lo que se pidio.
      if (inputClienteBuscar.value.trim() !== query) return;
      renderResultadosCliente(pagina.content);
    } catch {
      if (inputClienteBuscar.value.trim() !== query) return;
      resultadosCliente.innerHTML = '<div class="autocomplete-empty">No se pudo buscar clientes.</div>';
      resultadosCliente.hidden = false;
    }
  }

  inputClienteBuscar.addEventListener("input", () => {
    limpiarSeleccionCliente();
    const query = inputClienteBuscar.value.trim();
    clearTimeout(clienteBuscarTimer);
    if (!query) {
      ocultarResultadosCliente();
      return;
    }
    clienteBuscarTimer = setTimeout(() => buscarClientes(query), 250);
  });

  inputClienteBuscar.addEventListener("focus", () => {
    if (inputClienteBuscar.value.trim() && !inputCliente.value) buscarClientes(inputClienteBuscar.value.trim());
  });

  // mousedown (no click) para que dispare antes que el blur del input.
  resultadosCliente.addEventListener("mousedown", (event) => {
    const item = event.target.closest(".autocomplete-item");
    if (!item) return;
    event.preventDefault();
    const cliente = clienteResultadosActuales[Number(item.dataset.index)];
    if (cliente) seleccionarCliente(cliente);
  });

  inputClienteBuscar.addEventListener("blur", () => {
    setTimeout(ocultarResultadosCliente, 150);
  });

  function renderCard(carga, canManage) {
    const badgeClass = ESTADO_BADGE[carga.estado] || "badge-neutral";
    // Una carga Pendiente todavia no tiene viaje asignado (ver
    // sincronizarEstadoCarga en el backend: pasa a Asignada recien cuando
    // un Coordinador le crea un viaje). Se resalta para que sea facil de
    // detectar entre el resto de estados.
    const sinViajeBadge = carga.estado === "Pendiente"
      ? `<span class="badge badge-warning"><i class="bi bi-exclamation-circle"></i> Sin viaje asignado</span>`
      : "";

    // Igual que en Mis pedidos (autoservicio del Cliente): el backend ya
    // rechaza con 409 editar/eliminar una carga que dejo de estar
    // "Pendiente" (en cuanto tiene un viaje asignado, sincronizarEstadoCarga
    // la saca de ese estado sola) - se ocultan los botones directamente en
    // vez de dejar que el Coordinador se tope con el error.
    const puedeEditar = canManage && carga.estado === "Pendiente";
    const actions = `<div class="item-actions">
          <button type="button" class="icon-btn" data-action="guia" data-id="${carga.id}" title="Ver e imprimir guía">
            <i class="bi bi-file-earmark-text"></i>
          </button>
          ${puedeEditar ? `
          <button type="button" class="icon-btn" data-action="editar" data-id="${carga.id}" title="Editar">
            <i class="bi bi-pencil"></i>
          </button>
          <button type="button" class="icon-btn danger" data-action="eliminar" data-id="${carga.id}" title="Eliminar">
            <i class="bi bi-trash3"></i>
          </button>` : ""}
        </div>`;

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
            ${sinViajeBadge}
            <span><i class="bi bi-box-seam"></i>${formatPesoDoble(carga.peso)}</span>
          </div>
          ${carga.observaciones ? `<p class="item-observations">${escapeHtml(carga.observaciones)}</p>` : ""}
          ${actions}
        </div>
      </article>`;
  }

  async function showCargaGuide(carga) {
    const viaje = await trailersysApiRequest("GET", `/viajes/por-carga/${carga.id}`).catch(() => null);
    const [conductor, vehiculo] = viaje ? await Promise.all([
      trailersysApiRequest("GET", `/conductores/${viaje.conductorId}`).catch(() => null),
      trailersysApiRequest("GET", `/vehiculos/${viaje.vehiculoId}`).catch(() => null)
    ]) : [null, null];
    trailersysShowGuide({
      tipo: "Carga", id: carga.id, estado: carga.estado,
      secciones: [
        { titulo: "Datos de la carga", icono: "bi-box-seam", campos: [
          ["Mercancía", carga.descripcion], ["Tipo", carga.tipo],
          ["Peso", formatPesoDoble(carga.peso)], ["Observaciones", carga.observaciones || "Sin observaciones"]
        ] },
        { titulo: "Cliente y recorrido", icono: "bi-building", campos: [
          ["Cliente", carga.clienteNombre], ["Origen", carga.origen], ["Destino", carga.destino]
        ] },
        { titulo: "Asignación de transporte", icono: "bi-truck", campos: [
          ["Conductor", conductor?.nombres || "Pendiente de asignar"],
          ["Identificación", conductor?.identificacion],
          ["Licencia", conductor ? `${conductor.licenciaNumero || "—"} · Categoría ${conductor.licenciaCategoria || "—"}` : "—"],
          ["Vehículo", vehiculo ? `${vehiculo.marca} ${vehiculo.modelo}` : "Pendiente de asignar"],
          ["Placa", vehiculo?.placa], ["Capacidad", vehiculo ? formatPesoDoble(vehiculo.capacidad) : "—"],
          ["Viaje asociado", viaje ? `GUIA-VIA-${String(viaje.id).padStart(6, "0")}` : "Sin viaje asociado"]
        ] }
      ]
    });
  }

  async function render() {
    const canManage = trailersysCanManage(session, "cargas");
    btnNuevo.hidden = !canManage;

    let cargas;
    try {
      pageMeta = await trailersysPagedRequest("cargas", currentPage, 24, {
        search: inputBuscar.value.trim(),
        estado: filtroEstado.value,
      });
      cargas = pageMeta.content;
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      trailersysRenderPager(resultsCount, null);
      emptyTitle.textContent = "No se pudo cargar las cargas";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    cargasCache = cargas;

    const filtrados = cargas;

    if (filtrados.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      trailersysRenderPager(resultsCount, null);
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
    resultsCount.textContent = `${Number(pageMeta.totalElements).toLocaleString("es-EC")} carga${pageMeta.totalElements === 1 ? "" : "s"}`;
    trailersysRenderPager(resultsCount, pageMeta, (page) => { currentPage = page; render(); });
    grid.innerHTML = filtrados.map((c) => renderCard(c, canManage)).join("");
  }

  // El select de Origen/Destino se llena una sola vez, al cargar la pagina
  // (ver ecuador-locations.js), con las 65 ciudades del catalogo - pero el
  // origen/destino real de una carga no siempre es una de esas 65 (los
  // datos sembrados con SQL usan libremente "Ciudad, Ecuador" u otro
  // texto, no necesariamente el nombre exacto de una opcion). Sin agregar
  // el valor real como opcion aparte, el select quedaba en blanco al
  // editar una carga asi - y guardar sin darse cuenta le cambiaba el
  // origen/destino real a "" (bloqueado por "obligatorio") o, peor, a
  // otra ciudad distinta de la que en realidad tenia.
  function asegurarOpcionLugar(select, valor) {
    if (!valor || [...select.options].some((o) => o.value === valor)) return;
    const opcion = document.createElement("option");
    opcion.value = valor;
    opcion.textContent = valor;
    // No usar insertBefore(opcion, select.options[1]): con las 65 ciudades
    // agrupadas en <optgroup> (ver trailersysPoblarLugaresEcuador), casi
    // ninguna option es hija directa de <select> - insertBefore exige que
    // la referencia si lo sea, o tira NotFoundError. appendChild al final
    // siempre es un hijo directo valido, y de todos modos queda
    // seleccionada enseguida.
    select.appendChild(opcion);
  }

  // --- Modal de alta / edicion ---
  async function openForm(carga) {
    clearFieldErrors();
    form.reset();
    selectEstado.value = "Pendiente";
    selectPesoUnidad.value = "kg";
    pesoUnidadAnterior = "kg";
    ocultarResultadosCliente();
    // Reinicia el select al catalogo completo: puede haber quedado con una
    // opcion "extra" (ver asegurarOpcionLugar) de una edicion anterior.
    trailersysPoblarLugaresEcuador(inputOrigen);
    trailersysPoblarLugaresEcuador(inputDestino);

    if (carga) {
      modalTitle.textContent = "Editar carga";
      inputId.value = carga.id;
      inputDescripcion.value = carga.descripcion;
      // El nombre del cliente ya viene denormalizado en la carga (evita
      // otra peticion solo para mostrar la seleccion actual al editar).
      inputCliente.value = carga.clienteId;
      inputClienteBuscar.value = carga.clienteNombre || "";
      inputTipo.value = carga.tipo;
      inputPeso.value = carga.peso;
      selectEstado.value = carga.estado;
      asegurarOpcionLugar(inputOrigen, carga.origen);
      asegurarOpcionLugar(inputDestino, carga.destino);
      inputOrigen.value = carga.origen;
      inputDestino.value = carga.destino;
      inputObservaciones.value = carga.observaciones || "";
    } else {
      modalTitle.textContent = "Nueva carga";
      inputId.value = "";
      inputCliente.value = "";
      inputClienteBuscar.value = "";
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
      clienteId: inputCliente.value ? Number(inputCliente.value) : null,
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
      let guardada;
      if (id) {
        guardada = await trailersysApiRequest("PUT", `/cargas/${id}`, data);
      } else {
        guardada = await trailersysApiRequest("POST", "/cargas", data);
      }
      closeForm();
      await render();
      if (!id && guardada) {
        await showCargaGuide(guardada);
      }
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

    if (action === "guia") {
      showCargaGuide(carga).catch((error) => alert(error.message || "No se pudo generar la guía."));
    } else if (action === "editar") {
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
  let buscarTimer;
  inputBuscar.addEventListener("input", () => {
    clearTimeout(buscarTimer);
    buscarTimer = setTimeout(() => { currentPage = 0; render(); }, 300);
  });
  filtroEstado.addEventListener("change", () => { currentPage = 0; render(); });

  session = trailersysGetSession();
  render();
})();
