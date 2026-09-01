/**
 * Utilidades de UI compartidas entre modulos: apertura/cierre de modales
 * y un dialogo de confirmacion generico (reemplaza al confirm() nativo
 * para mantener la identidad visual del sistema).
 */
function trailersysOpenModal(overlayEl) {
  overlayEl.classList.add("open");
}

function trailersysCloseModal(overlayEl) {
  overlayEl.classList.remove("open");
}

const trailersysConfirm = (function () {
  const overlay = document.getElementById("confirmModalOverlay");
  const titleEl = document.getElementById("confirmModalTitle");
  const textEl = document.getElementById("confirmModalText");
  const acceptBtn = document.getElementById("confirmModalAccept");
  const cancelBtn = document.getElementById("confirmModalCancel");
  const closeBtn = document.getElementById("confirmModalClose");
  let pendingAccept = null;

  function close() {
    trailersysCloseModal(overlay);
    pendingAccept = null;
  }

  acceptBtn.addEventListener("click", () => {
    const accept = pendingAccept;
    close();
    if (accept) accept();
  });
  cancelBtn.addEventListener("click", close);
  closeBtn.addEventListener("click", close);
  overlay.addEventListener("click", (event) => {
    if (event.target === overlay) close();
  });

  return function trailersysConfirm({ title, text, acceptLabel = "Eliminar", variant = "danger", onAccept }) {
    titleEl.textContent = title;
    textEl.textContent = text;
    acceptBtn.textContent = acceptLabel;
    acceptBtn.classList.toggle("btn-danger", variant === "danger");
    acceptBtn.classList.toggle("btn-primary", variant !== "danger");
    pendingAccept = onAccept;
    trailersysOpenModal(overlay);
  };
})();

/**
 * Convierte un input de texto en un buscador con autocompletado contra un
 * endpoint paginado del backend (trailersysPagedRequest de api-client.js).
 * Pensado para catalogos demasiado grandes para precargar en un <select>
 * (decenas de miles de vehiculos/conductores/clientes reales): en vez de
 * una lista fija de las primeras N filas, busca en el backend a medida
 * que se escribe y solo muestra las coincidencias.
 *
 * Requiere en el HTML: el input visible, un input[type=hidden] junto a el
 * (donde queda el id elegido) y un contenedor .autocomplete-results (ver
 * css/cargas.css) posicionado dentro de un .autocomplete-wrap.
 *
 * @param {Object} opts
 * @param {HTMLInputElement} opts.input - campo de texto visible.
 * @param {HTMLInputElement} opts.hidden - campo oculto con el id elegido.
 * @param {HTMLElement} opts.resultados - contenedor de la lista de coincidencias.
 * @param {string} opts.recurso - nombre para trailersysPagedRequest (ej. "vehiculos").
 * @param {(item:any) => string} opts.etiqueta - texto principal de cada resultado
 *   (y valor que queda en el input al elegirlo).
 * @param {(item:any) => string} [opts.detalle] - texto secundario opcional.
 * @param {Object|() => Object} [opts.extraParams] - parametros fijos de busqueda
 *   (ej. {estado: "Disponible"}); puede ser una funcion si dependen de otro campo.
 * @param {(item:any) => void} [opts.onSeleccionar] - callback extra al elegir.
 * @returns {{ seleccionar(item), limpiar(), ocultar() }}
 */
function trailersysAutocomplete({ input, hidden, resultados, recurso, etiqueta, detalle, extraParams = {}, onSeleccionar }) {
  const escape = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;",
  }[char]));

  let timer;
  let actuales = [];

  function ocultar() {
    resultados.hidden = true;
    resultados.innerHTML = "";
    actuales = [];
  }

  function limpiar() {
    hidden.value = "";
  }

  function seleccionar(item) {
    hidden.value = item.id;
    input.value = etiqueta(item);
    ocultar();
    if (onSeleccionar) onSeleccionar(item);
  }

  function render(items) {
    actuales = items;
    resultados.innerHTML = items.length
      ? items.map((item, index) => `
        <div class="autocomplete-item" data-index="${index}">
          <span class="autocomplete-item-name">${escape(etiqueta(item))}</span>
          ${detalle ? `<span class="autocomplete-item-meta">${escape(detalle(item))}</span>` : ""}
        </div>`).join("")
      : '<div class="autocomplete-empty">Ninguna coincidencia.</div>';
    resultados.hidden = false;
  }

  async function buscar(query) {
    const params = typeof extraParams === "function" ? extraParams() : extraParams;
    try {
      const pagina = await trailersysPagedRequest(recurso, 0, 12, { ...params, search: query });
      // Las respuestas no llegan garantizadas en el mismo orden en que
      // salieron las peticiones: si la persona siguio escribiendo mientras
      // esta viajaba, puede llegar despues de una mas reciente y pisar
      // resultados validos con los de una busqueda vieja (ej. escribir
      // rapido "Comercial Andina" podia terminar mostrando "Ninguna
      // coincidencia" de una letra a medio escribir). Se descarta si el
      // input ya no coincide con lo que se pidio.
      if (input.value.trim() !== query) return;
      render(pagina.content);
    } catch {
      if (input.value.trim() !== query) return;
      resultados.innerHTML = '<div class="autocomplete-empty">No se pudo buscar.</div>';
      resultados.hidden = false;
    }
  }

  input.addEventListener("input", () => {
    limpiar();
    const query = input.value.trim();
    clearTimeout(timer);
    if (!query) {
      ocultar();
      return;
    }
    timer = setTimeout(() => buscar(query), 250);
  });

  input.addEventListener("focus", () => {
    if (input.value.trim() && !hidden.value) buscar(input.value.trim());
  });

  // mousedown (no click) para que dispare antes que el blur del input.
  resultados.addEventListener("mousedown", (event) => {
    const item = event.target.closest(".autocomplete-item");
    if (!item) return;
    event.preventDefault();
    const elegido = actuales[Number(item.dataset.index)];
    if (elegido) seleccionar(elegido);
  });

  input.addEventListener("blur", () => setTimeout(ocultar, 150));

  return { seleccionar, limpiar, ocultar };
}

/**
 * Restringe un input de texto a solo digitos mientras se escribe o se pega
 * (telefono, cedula/RUC): borra cualquier caracter que no sea 0-9 en cuanto
 * aparece, en vez de dejar que se guarde con letras o simbolos y recien
 * avisar al enviar el formulario. No toca el valor si se precarga por
 * codigo (ej. al abrir un formulario de edicion), porque asignar
 * input.value = ... no dispara "input": solo actua sobre lo que la persona
 * realmente escribe o pega desde ese momento.
 *
 * @param {HTMLInputElement} input
 * @param {number} [maxLength] - longitud maxima opcional (ej. 10 para un
 *   celular ecuatoriano).
 */
function trailersysSoloDigitos(input, maxLength) {
  input.addEventListener("input", () => {
    const cursor = input.selectionStart;
    const limpio = input.value.replace(/\D/g, "");
    const recortado = maxLength ? limpio.slice(0, maxLength) : limpio;
    if (recortado !== input.value) {
      const diferencia = input.value.length - recortado.length;
      input.value = recortado;
      // Mantiene el cursor donde estaba en vez de mandarlo al final, para
      // no molestar si se está corrigiendo un caracter en medio del texto.
      if (cursor !== null) {
        const nuevaPos = Math.max(0, cursor - diferencia);
        input.setSelectionRange(nuevaPos, nuevaPos);
      }
    }
  });
}

/**
 * Secciones estandar de vehiculo/conductor/cliente y carga de un viaje
 * (recibe el shape ya denormalizado de ViajeResponse). Compartidas entre
 * la guia (trailersysShowGuide, que ademas agrega su propia seccion de
 * "Ruta y despacho") y el detalle "denso" que se muestra dentro de los
 * modales de Viajes, Seguimiento y Mis viajes (trailersysRenderViajeSecciones)
 * - antes esos modales solo mostraban 4 tarjetas de distancia/duracion/
 * salida/ETA y para ver que vehiculo o conductor era habia que cerrar el
 * modal y abrir la guia aparte.
 */
function trailersysViajeSecciones(viaje) {
  const peso = (kg) => kg == null ? null
    : `${Number(kg).toLocaleString("es-EC")} kg / ${(Number(kg) * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  return [
    { titulo: "Vehículo", icono: "bi-truck", campos: [
      ["Placa", viaje.vehiculoPlaca], ["Marca", viaje.vehiculoMarca], ["Modelo", viaje.vehiculoModelo],
      ["Tipo", viaje.vehiculoTipo], ["Año", viaje.vehiculoAnio], ["Color", viaje.vehiculoColor],
      ["Capacidad", peso(viaje.vehiculoCapacidad)],
    ] },
    { titulo: "Conductor", icono: "bi-person-badge", campos: [
      ["Nombre completo", viaje.conductorNombres], ["Identificación", viaje.conductorIdentificacion],
      ["Teléfono", viaje.conductorTelefono], ["Licencia", viaje.conductorLicenciaNumero],
      ["Categoría", viaje.conductorLicenciaCategoria], ["Vencimiento", viaje.conductorLicenciaVencimiento],
    ] },
    { titulo: "Cliente y carga", icono: "bi-box-seam", campos: [
      ["Cliente", viaje.clienteNombre],
      ["Mercancía", viaje.cargaDescripcion || (viaje.cargaId ? null : "Viaje sin carga asociada")],
      ["Tipo de carga", viaje.cargaTipo], ["Peso", peso(viaje.cargaPeso)],
      ["Observaciones", viaje.observaciones],
    ] },
  ];
}

/**
 * Pinta trailersysViajeSecciones() dentro de un contenedor, reusando la
 * misma marca/estilo .guia-section/.guia-fields que ya usa la guia (ver
 * css/modal.css) para que un vistazo rapido del detalle se vea igual de
 * prolijo sin tener que abrir la guia completa.
 */
function trailersysRenderViajeSecciones(container, viaje) {
  const escape = (value) => String(value ?? "").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[char]));
  const html = trailersysViajeSecciones(viaje)
    .map((seccion) => {
      const campos = seccion.campos.filter(([, valor]) => valor != null && valor !== "");
      if (!campos.length) return "";
      return `<section class="guia-section">
        <h4><i class="bi ${seccion.icono}"></i>${escape(seccion.titulo)}</h4>
        <div class="guia-fields">
          ${campos.map(([etiqueta, valor]) => `<div class="guia-field"><span>${escape(etiqueta)}</span><strong>${escape(valor)}</strong></div>`).join("")}
        </div>
      </section>`;
    })
    .filter(Boolean)
    .join("");
  container.innerHTML = html || '<p class="dashboard-empty">Sin información adicional.</p>';
}

const trailersysShowGuide = (function () {
  const overlay = document.getElementById("guiaModalOverlay");
  const titleEl = document.getElementById("guiaModalTitle");
  const contentEl = document.getElementById("guiaModalContent");
  const closeBtn = document.getElementById("guiaModalClose");
  const closeFooterBtn = document.getElementById("guiaModalCerrarBtn");
  const printBtn = document.getElementById("guiaModalImprimir");

  const escapeHtml = (value) => String(value ?? "—").replace(/[&<>"']/g, (char) => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  }[char]));

  function close() { trailersysCloseModal(overlay); }
  closeBtn.addEventListener("click", close);
  closeFooterBtn.addEventListener("click", close);
  printBtn.addEventListener("click", () => window.print());
  overlay.addEventListener("click", (event) => { if (event.target === overlay) close(); });

  return function trailersysShowGuide({ tipo, codigo, id, estado, secciones }) {
    const prefijo = codigo || (tipo === "Viaje" ? "VIA" : "CAR");
    const numero = `GUIA-${prefijo}-${String(id).padStart(6, "0")}`;
    titleEl.textContent = `Guía de ${tipo.toLowerCase()} ${numero}`;
    contentEl.innerHTML = `
      <div class="guia-summary">
        <div><span>Número de guía</span><strong>${escapeHtml(numero)}</strong></div>
        <div><span>Fecha de emisión</span><strong>${escapeHtml(new Date().toLocaleString("es-EC"))}</strong></div>
        <div><span>Estado</span><strong>${escapeHtml(estado)}</strong></div>
      </div>
      ${secciones.map((seccion) => `
        <section class="guia-section">
          <h4><i class="bi ${escapeHtml(seccion.icono || "bi-list-check")}"></i>${escapeHtml(seccion.titulo)}</h4>
          <div class="guia-fields">
            ${seccion.campos.map(([etiqueta, valor]) => `
              <div class="guia-field"><span>${escapeHtml(etiqueta)}</span><strong>${escapeHtml(valor || "—")}</strong></div>
            `).join("")}
          </div>
        </section>
      `).join("")}
      <div class="guia-signatures">
        <div><span>Firma del responsable de despacho</span></div>
        <div><span>Firma del conductor</span></div>
        <div><span>Firma de recepción</span></div>
      </div>`;
    trailersysOpenModal(overlay);
  };
})();
