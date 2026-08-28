(function () {
  const ESTADO_BADGE = {
    Pendiente: "badge-warning",
    Asignada: "badge-info",
    "En Tránsito": "badge-neutral",
    Entregada: "badge-success",
  };

  // Cache del ultimo listado de pedidos (Cargas propias) cargado desde la
  // API, y del viaje asociado a cada uno (solo se resuelve para los que ya
  // estan Entregados, que es cuando puede haber algo que confirmar).
  let pedidosCache = [];
  let viajesPorCarga = {};
  let pesoUnidadAnterior = "kg";

  // --- Referencias del DOM ---
  const btnNuevo = document.getElementById("btnNuevoPedido");
  const grid = document.getElementById("pedidoGrid");
  const emptyState = document.getElementById("pedidoEmptyState");
  const emptyTitle = document.getElementById("pedidoEmptyTitle");
  const emptyText = document.getElementById("pedidoEmptyText");
  const resultsCount = document.getElementById("pedidoResultsCount");

  const modalOverlay = document.getElementById("pedidoModalOverlay");
  const form = document.getElementById("pedidoForm");
  const btnCerrarModal = document.getElementById("pedidoModalClose");
  const btnCancelar = document.getElementById("pedidoCancelar");

  const inputDescripcion = document.getElementById("pedidoDescripcion");
  const inputTipo = document.getElementById("pedidoTipo");
  const inputPeso = document.getElementById("pedidoPeso");
  const selectPesoUnidad = document.getElementById("pedidoPesoUnidad");
  const inputOrigen = document.getElementById("pedidoOrigen");
  const inputDestino = document.getElementById("pedidoDestino");
  const inputObservaciones = document.getElementById("pedidoObservaciones");

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, (char) => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;",
    }[char]));
  }

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

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function clearFieldErrors() {
    ["fieldPedidoDescripcion", "fieldPedidoTipo", "fieldPedidoPeso", "fieldPedidoOrigen", "fieldPedidoDestino"]
      .forEach((id) => setFieldError(id, ""));
  }

  function renderCard(carga) {
    const badgeClass = ESTADO_BADGE[carga.estado] || "badge-neutral";
    const viaje = viajesPorCarga[carga.id];

    let accion = "";
    if (carga.estado === "Entregada" && viaje) {
      accion = viaje.entregaConfirmadaCliente
        ? `<span class="badge badge-success"><i class="bi bi-patch-check"></i> Recepción confirmada${viaje.fechaConfirmacionCliente ? ` el ${trailersysFormatDateTime(viaje.fechaConfirmacionCliente)}` : ""}</span>`
        : `<div class="item-actions"><button type="button" class="btn btn-primary" data-action="confirmar" data-id="${carga.id}"><i class="bi bi-check-circle"></i> Confirmar recepción</button></div>`;
    } else if (carga.estado === "Pendiente") {
      accion = `<span class="badge badge-warning"><i class="bi bi-hourglass-split"></i> Esperando asignación de viaje</span>`;
    }

    return `
      <article class="card item-card">
        <div class="item-banner">
          <i class="bi bi-box-seam"></i>
          <div class="item-banner-title">
            <div class="item-title">${escapeHtml(carga.descripcion)}</div>
            <div class="item-subtitle">${escapeHtml(carga.tipo)}</div>
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
          ${accion}
        </div>
      </article>`;
  }

  async function render() {
    let cargas;
    try {
      cargas = await trailersysApiRequest("GET", "/mis-cargas");
    } catch (error) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "No se pudieron cargar tus pedidos";
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }
    pedidosCache = cargas;

    // Solo hace falta saber el viaje de los pedidos ya Entregados, que es
    // cuando puede haber algo pendiente de confirmar.
    const entregadas = cargas.filter((c) => c.estado === "Entregada");
    viajesPorCarga = {};
    await Promise.all(entregadas.map(async (c) => {
      try {
        viajesPorCarga[c.id] = await trailersysApiRequest("GET", `/mis-cargas/${c.id}/viaje`);
      } catch {
        viajesPorCarga[c.id] = null;
      }
    }));

    if (cargas.length === 0) {
      grid.hidden = true;
      emptyState.hidden = false;
      resultsCount.textContent = "";
      emptyTitle.textContent = "Todavía no tienes pedidos";
      emptyText.textContent = 'Usa "Hacer un pedido" para solicitar tu primer transporte.';
      return;
    }

    grid.hidden = false;
    emptyState.hidden = true;
    resultsCount.textContent = `${cargas.length} pedido${cargas.length === 1 ? "" : "s"}`;
    grid.innerHTML = cargas.map(renderCard).join("");
  }

  // --- Modal de "Hacer un pedido" ---
  function openForm() {
    clearFieldErrors();
    form.reset();
    selectPesoUnidad.value = "kg";
    pesoUnidadAnterior = "kg";
    trailersysOpenModal(modalOverlay);
    inputDescripcion.focus();
  }

  function closeForm() {
    trailersysCloseModal(modalOverlay);
  }

  btnNuevo.addEventListener("click", openForm);
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

  // --- Validacion y envio ---
  function validate(data) {
    clearFieldErrors();
    let valid = true;

    function fail(fieldId, message) {
      setFieldError(fieldId, message);
      valid = false;
    }

    if (!data.descripcion) fail("fieldPedidoDescripcion", "La descripción es obligatoria.");
    if (!data.tipo) fail("fieldPedidoTipo", "El tipo de mercancía es obligatorio.");

    if (data.peso === "" || Number.isNaN(data.peso) || data.peso < 0) {
      fail("fieldPedidoPeso", "Ingresa un peso válido.");
    }

    if (!data.origen) fail("fieldPedidoOrigen", "Selecciona un origen.");
    if (!data.destino) fail("fieldPedidoDestino", "Selecciona un destino.");

    return valid;
  }

  const submitBtn = form.querySelector('button[type="submit"]');

  form.addEventListener("submit", async (event) => {
    event.preventDefault();

    const data = {
      descripcion: inputDescripcion.value.trim(),
      tipo: inputTipo.value.trim(),
      peso: inputPeso.value === "" ? "" : Math.round(valorEnKg(inputPeso.value, selectPesoUnidad.value)),
      origen: inputOrigen.value,
      destino: inputDestino.value,
      observaciones: inputObservaciones.value.trim(),
    };

    if (!validate(data)) return;

    submitBtn.disabled = true;
    try {
      await trailersysApiRequest("POST", "/mis-cargas", data);
      closeForm();
      await render();
    } catch (error) {
      alert(error.message || "No se pudo enviar el pedido.");
    } finally {
      submitBtn.disabled = false;
    }
  });

  // --- Confirmar recepcion ---
  grid.addEventListener("click", (event) => {
    const button = event.target.closest('button[data-action="confirmar"]');
    if (!button) return;
    const { id } = button.dataset;
    const carga = pedidosCache.find((c) => String(c.id) === id);
    if (!carga) return;

    trailersysConfirm({
      title: "Confirmar recepción",
      text: `¿Confirmas que recibiste "${carga.descripcion}" en buen estado?`,
      acceptLabel: "Confirmar",
      variant: "primary",
      onAccept: async () => {
        try {
          await trailersysApiRequest("POST", `/mis-cargas/${id}/confirmar-recepcion`, {});
          await render();
        } catch (error) {
          alert(error.message || "No se pudo confirmar la recepción.");
        }
      },
    });
  });

  render();
})();
