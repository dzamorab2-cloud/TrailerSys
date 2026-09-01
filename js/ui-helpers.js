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

/**
 * Mapa decorativo de Ecuador (Leaflet real, mismo mosaico de OpenStreetMap
 * que ya usan Viajes/Seguimiento/Mis viajes - una silueta dibujada a mano
 * no se veia como un mapa real), reutilizado en cada Dashboard
 * personalizado por rol que lo pida (Conductor, Supervisor, y el generico
 * de Administrador/Coordinador/Mantenimiento). Se guarda una instancia por
 * contenedor para no reinicializar Leaflet sobre el mismo div dos veces -
 * revienta con "Map container is already initialized" si se llama L.map()
 * de nuevo sobre el mismo elemento -, y la segunda vez en adelante (por
 * ejemplo al volver a activar el modulo Dashboard) solo se invalida el
 * tamaño.
 * @param {string} containerId - id del div donde va el mapa.
 */
const trailersysRenderEcuadorMap = (function () {
  const instancias = {};
  return function trailersysRenderEcuadorMap(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    if (typeof L === "undefined") {
      container.innerHTML = '<div class="route-map-placeholder"><i class="bi bi-wifi-off"></i><p>No se pudo cargar el mapa. Verifica la conexión a internet.</p></div>';
      return;
    }
    if (instancias[containerId]) {
      setTimeout(() => instancias[containerId].invalidateSize(), 100);
      return;
    }
    const mapa = L.map(container, { scrollWheelZoom: false }).setView([-1.55, -78.6], 6.3);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; colaboradores de OpenStreetMap",
      maxZoom: 18,
    }).addTo(mapa);

    // Un par de ciudades como referencia, nada mas - es decorativo, no una
    // ruta (esa vive en el mapa del detalle de cada viaje).
    [
      ["Quito", -0.1807, -78.4678, "var(--color-info)"],
      ["Guayaquil", -2.1894, -79.8891, "var(--color-warning)"],
      ["Cuenca", -2.9006, -79.0045, "var(--color-danger)"],
    ].forEach(([nombre, lat, lng, color]) => {
      L.circleMarker([lat, lng], { radius: 6, color, fillColor: color, fillOpacity: 1, weight: 2 })
        .addTo(mapa)
        .bindTooltip(nombre, { permanent: true, direction: "right", offset: [6, 0], className: "dashboard-map-tooltip" });
    });

    instancias[containerId] = mapa;
    setTimeout(() => mapa.invalidateSize(), 200);
  };
})();

/**
 * Anillo de progreso circular (SVG, sin libreria) para un solo porcentaje -
 * pensado para un indicador "de un vistazo" (ej. % de flota disponible, %
 * de licencias vigentes) en los Dashboards por rol. El valor siempre lo
 * calcula quien llama a partir de datos reales del backend, nunca se
 * inventa aqui.
 * @param {HTMLElement} container
 * @param {{valor:number, etiqueta:string, color?:string}} opts - valor 0-100.
 */
function trailersysRenderProgressRing(container, { valor, etiqueta, color = "var(--color-primary)" }) {
  const escape = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const pct = Number.isFinite(valor) ? Math.max(0, Math.min(100, valor)) : 0;
  const r = 42;
  const c = 2 * Math.PI * r;
  const dash = (pct / 100) * c;
  container.innerHTML = `
    <div class="progress-ring-wrap">
      <svg viewBox="0 0 100 100" class="progress-ring" role="img" aria-label="${escape(etiqueta)}: ${Math.round(pct)}%">
        <circle cx="50" cy="50" r="${r}" class="progress-ring-track"></circle>
        <circle cx="50" cy="50" r="${r}" class="progress-ring-fill" style="stroke:${color};stroke-dasharray:${dash.toFixed(1)} ${c.toFixed(1)}"></circle>
      </svg>
      <div class="progress-ring-center"><strong>${Math.round(pct)}%</strong></div>
    </div>
    <span class="progress-ring-label">${escape(etiqueta)}</span>`;
}

/**
 * Grafica de area con linea suavizada (SVG, sin libreria) para una
 * tendencia a lo largo del tiempo - mismo espiritu que el donut/barras en
 * CSS puro que ya usaba el Dashboard, pero con una curva suave (tecnica de
 * suavizado por punto medio: cada segmento es una curva cuadratica hacia
 * el punto medio del siguiente, en vez de una linea recta entre puntos).
 * @param {HTMLElement} container
 * @param {{label:string, value:number}[]} puntos
 * @param {{color?:string}} [opts]
 */
function trailersysRenderAreaChart(container, puntos, { color = "var(--color-primary)" } = {}) {
  const escape = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  if (!puntos.length) {
    container.innerHTML = '<p class="dashboard-empty">Sin datos todavía.</p>';
    return;
  }
  const width = 100;
  const height = 100;
  const max = Math.max(1, ...puntos.map((p) => p.value));
  const stepX = puntos.length > 1 ? width / (puntos.length - 1) : 0;
  const coords = puntos.map((p, i) => ({
    x: puntos.length > 1 ? i * stepX : width / 2,
    y: height - 8 - (p.value / max) * (height - 20),
  }));

  let linea = `M ${coords[0].x.toFixed(1)},${coords[0].y.toFixed(1)}`;
  for (let i = 1; i < coords.length; i++) {
    const prev = coords[i - 1];
    const cur = coords[i];
    const midX = (prev.x + cur.x) / 2;
    const midY = (prev.y + cur.y) / 2;
    linea += ` Q ${prev.x.toFixed(1)},${prev.y.toFixed(1)} ${midX.toFixed(1)},${midY.toFixed(1)}`;
  }
  linea += ` L ${coords[coords.length - 1].x.toFixed(1)},${coords[coords.length - 1].y.toFixed(1)}`;
  const area = `${linea} L ${coords[coords.length - 1].x.toFixed(1)},${height} L ${coords[0].x.toFixed(1)},${height} Z`;
  const gradId = `areaGrad${Math.random().toString(36).slice(2, 9)}`;

  container.innerHTML = `
    <svg viewBox="0 0 ${width} ${height}" preserveAspectRatio="none" class="area-chart-svg">
      <defs>
        <linearGradient id="${gradId}" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="${color}" stop-opacity="0.35"></stop>
          <stop offset="100%" stop-color="${color}" stop-opacity="0"></stop>
        </linearGradient>
      </defs>
      <path d="${area}" fill="url(#${gradId})" stroke="none"></path>
      <path d="${linea}" fill="none" stroke="${color}" stroke-width="2.2" vector-effect="non-scaling-stroke"></path>
      ${coords.map((p) => `<circle cx="${p.x.toFixed(1)}" cy="${p.y.toFixed(1)}" r="2" fill="${color}"></circle>`).join("")}
    </svg>
    <div class="area-chart-labels">${puntos.map((p) => `<span>${escape(p.label)}</span>`).join("")}</div>`;
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
