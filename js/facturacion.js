/**
 * Facturacion y cobros (cuentas por cobrar internas, NO comprobante del
 * SRI). Administrador y Coordinador emiten facturas a partir de viajes
 * finalizados y registran abonos; Supervisor consulta; el Cliente ve sus
 * propias facturas en "Mis facturas". API: /api/facturas y /api/mis-facturas.
 */
(function () {
  const session = trailersysGetSession();
  const rol = session?.role;
  const puedeVer = TRAILERSYS_ROLES[rol]?.modules.includes("facturacion");
  const esCliente = TRAILERSYS_ROLES[rol]?.modules.includes("mis-facturas");
  if (!puedeVer && !esCliente) return;

  const puedeGestionar = trailersysCanManage(session, "facturacion");
  const puedeAnular = rol === "administrador";

  const $ = (id) => document.getElementById(id);
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const dinero = (n) => Number(n || 0).toLocaleString("es-EC", { style: "currency", currency: "USD" });
  const fecha = (iso) => {
    if (!iso) return "—";
    const [y, m, d] = String(iso).slice(0, 10).split("-").map(Number);
    return new Date(y, m - 1, d).toLocaleDateString("es-EC", { day: "2-digit", month: "short", year: "numeric" });
  };
  const BADGE = { Pendiente: "badge-info", Parcial: "badge-warning", Pagada: "badge-success", Vencida: "badge-danger", Anulada: "badge-neutral" };
  const badge = (estado) => `<span class="badge ${BADGE[estado] || "badge-neutral"}">${esc(estado)}</span>`;
  const statCard = (icon, valor, label, tone = "") =>
    `<div class="stat-card stat-card-action ${tone}"><div class="stat-card-icon"><i class="bi ${icon}"></i></div><div><div class="stat-card-value">${valor}</div><div class="stat-card-label">${esc(label)}</div></div></div>`;

  let facturasCache = [];
  let configuracion = { ivaPorcentaje: 15, diasCredito: 30 };

  // ======================================================================
  // Detalle (compartido entre el modulo interno y "Mis facturas")
  // ======================================================================
  const detalleOverlay = $("facturaDetalleOverlay");
  const detalleContenido = $("facturaDetalleContenido");
  let facturaAbierta = null;

  function cerrarDetalle() {
    trailersysCloseModal(detalleOverlay);
    facturaAbierta = null;
  }
  $("facturaDetalleClose").addEventListener("click", cerrarDetalle);
  detalleOverlay.addEventListener("click", (e) => { if (e.target === detalleOverlay) cerrarDetalle(); });

  function renderDetalle(f, { soloLectura }) {
    facturaAbierta = f;
    $("facturaDetalleTitulo").textContent = `Factura ${f.numero}`;
    const abierta = f.estado !== "Pagada" && f.estado !== "Anulada";
    const progreso = Number(f.total) > 0 ? Math.min(100, (Number(f.pagado) / Number(f.total)) * 100) : 0;
    const vencimientoTxt = f.estado === "Vencida"
      ? `<span class="factura-vencida">Vencida hace ${f.diasVencida} día${f.diasVencida === 1 ? "" : "s"}</span>`
      : fecha(f.fechaVencimiento);

    detalleContenido.innerHTML = `
      <div class="factura-cabecera">
        <div>
          <div class="table-secondary">Cliente</div>
          <div class="factura-cliente">${esc(f.clienteNombre)}</div>
          <div class="table-secondary">RUC/CI ${esc(f.clienteIdentificacion || "—")}</div>
        </div>
        <div class="factura-cabecera-estado">${badge(f.estado)}</div>
      </div>
      <div class="route-summary-grid factura-datos">
        <div class="route-stat"><div class="route-stat-label">Emisión</div><div class="route-stat-value">${fecha(f.fechaEmision)}</div></div>
        <div class="route-stat"><div class="route-stat-label">Vencimiento</div><div class="route-stat-value">${vencimientoTxt}</div></div>
        <div class="route-stat"><div class="route-stat-label">Total</div><div class="route-stat-value">${dinero(f.total)}</div></div>
        <div class="route-stat"><div class="route-stat-label">Saldo</div><div class="route-stat-value ${Number(f.saldo) > 0 ? "factura-saldo" : ""}">${dinero(f.saldo)}</div></div>
      </div>
      <div class="factura-progreso" title="Pagado ${dinero(f.pagado)} de ${dinero(f.total)}">
        <div class="factura-progreso-barra" style="width:${progreso.toFixed(1)}%"></div>
      </div>
      <div class="table-secondary factura-progreso-texto">Pagado ${dinero(f.pagado)} de ${dinero(f.total)}</div>

      <h4 class="factura-subtitulo">Detalle</h4>
      <table class="report-table factura-lineas">
        <thead><tr><th>Descripción</th><th class="num">Valor</th></tr></thead>
        <tbody>${f.detalles.map((d) => `<tr><td>${esc(d.descripcion)}</td><td class="num">${dinero(d.monto)}</td></tr>`).join("")}</tbody>
        <tfoot>
          <tr><td>Subtotal</td><td class="num">${dinero(f.subtotal)}</td></tr>
          <tr><td>IVA ${Number(f.ivaPorcentaje).toLocaleString("es-EC")}%</td><td class="num">${dinero(f.iva)}</td></tr>
          <tr class="factura-total-fila"><td>Total</td><td class="num">${dinero(f.total)}</td></tr>
        </tfoot>
      </table>

      <h4 class="factura-subtitulo">Pagos</h4>
      ${f.pagos.length ? `
        <table class="report-table factura-lineas">
          <thead><tr><th>Fecha</th><th>Método</th><th>Referencia</th><th class="num">Monto</th></tr></thead>
          <tbody>${f.pagos.map((p) => `<tr><td>${fecha(p.fecha)}</td><td>${esc(p.metodo)}</td><td>${esc(p.referencia || "—")}</td><td class="num">${dinero(p.monto)}</td></tr>`).join("")}</tbody>
        </table>` : `<p class="table-secondary">Todavía no se registran pagos.</p>`}

      ${f.estado === "Anulada" ? `<div class="factura-anulada"><i class="bi bi-slash-circle"></i> Anulada el ${trailersysFormatDateTime(f.fechaAnulacion)}: ${esc(f.motivoAnulacion)}</div>` : ""}
      ${f.observaciones ? `<p class="table-secondary factura-obs"><strong>Observaciones:</strong> ${esc(f.observaciones)}</p>` : ""}

      ${!soloLectura && puedeGestionar && abierta ? `
        <form id="pagoForm" class="factura-pago-form card" novalidate>
          <h4 class="factura-subtitulo"><i class="bi bi-cash-coin"></i> Registrar pago</h4>
          <div class="form-grid">
            <div class="field"><label for="pagoMonto">Monto *</label><div class="input-wrap"><i class="bi bi-currency-dollar"></i><input type="number" id="pagoMonto" min="0.01" step="0.01" max="${Number(f.saldo)}" value="${Number(f.saldo).toFixed(2)}" /></div><span class="field-error"></span></div>
            <div class="field"><label for="pagoFecha">Fecha</label><div class="input-wrap"><i class="bi bi-calendar"></i><input type="date" id="pagoFecha" max="${new Date().toLocaleDateString("en-CA")}" value="${new Date().toLocaleDateString("en-CA")}" /></div></div>
            <div class="field"><label for="pagoMetodo">Método *</label><div class="input-wrap"><i class="bi bi-wallet2"></i><select id="pagoMetodo"><option>Transferencia</option><option>Depósito</option><option>Cheque</option><option>Efectivo</option><option>Tarjeta</option></select></div></div>
            <div class="field"><label for="pagoReferencia">Referencia</label><div class="input-wrap"><i class="bi bi-hash"></i><input type="text" id="pagoReferencia" maxlength="120" placeholder="N.º de transferencia o cheque" /></div></div>
          </div>
          <div class="modal-footer"><button type="submit" class="btn btn-primary" id="pagoGuardar"><i class="bi bi-check-lg"></i>Registrar pago</button></div>
        </form>` : ""}

      <div class="modal-footer factura-acciones">
        ${!soloLectura && puedeAnular && f.estado !== "Anulada" && !f.pagos.length ? `<button type="button" class="btn btn-ghost btn-danger-text" id="facturaAnular"><i class="bi bi-slash-circle"></i>Anular</button>` : ""}
        <button type="button" class="btn btn-ghost" id="facturaImprimir"><i class="bi bi-printer"></i>Imprimir / PDF</button>
      </div>`;

    $("facturaImprimir").addEventListener("click", () => imprimirFactura(f));
    $("facturaAnular")?.addEventListener("click", () => anularFactura(f));
    $("pagoForm")?.addEventListener("submit", (e) => registrarPago(e, f));
    trailersysOpenModal(detalleOverlay);
  }

  async function abrirDetalle(id, { soloLectura = false } = {}) {
    try {
      const f = soloLectura
        ? facturasCache.find((x) => String(x.id) === String(id))
        : await trailersysApiRequest("GET", `/facturas/${id}`);
      if (f) renderDetalle(f, { soloLectura });
    } catch (error) {
      trailersysToast(error.message || "No se pudo abrir la factura.", { type: "error" });
    }
  }

  async function registrarPago(event, f) {
    event.preventDefault();
    const input = $("pagoMonto");
    const monto = Number(input.value);
    const errorEl = input.closest(".field").querySelector(".field-error");
    errorEl.textContent = "";
    if (!(monto > 0)) { errorEl.textContent = "Ingresa un monto mayor a 0."; return; }
    if (monto > Number(f.saldo) + 0.001) { errorEl.textContent = `No puede superar el saldo (${dinero(f.saldo)}).`; return; }
    const btn = $("pagoGuardar");
    btn.disabled = true;
    try {
      const actualizada = await trailersysApiRequest("POST", `/facturas/${f.id}/pagos`, {
        monto: Number(monto.toFixed(2)),
        fecha: $("pagoFecha").value || null,
        metodo: $("pagoMetodo").value,
        referencia: $("pagoReferencia").value.trim() || null,
      });
      trailersysToast(actualizada.estado === "Pagada" ? `Factura ${actualizada.numero} pagada por completo.` : `Pago de ${dinero(monto)} registrado.`);
      renderDetalle(actualizada, { soloLectura: false });
      await cargarModulo();
    } catch (error) {
      trailersysToast(error.message || "No se pudo registrar el pago.", { type: "error" });
    } finally {
      if ($("pagoGuardar")) $("pagoGuardar").disabled = false;
    }
  }

  async function anularFactura(f) {
    const motivo = await trailersysPrompt({
      title: `Anular factura ${f.numero}`,
      text: "La factura dejará de contar en la cartera y sus viajes podrán volver a facturarse. Esta acción no se puede deshacer.",
      label: "Motivo de la anulación",
      placeholder: "Ej. Valor mal digitado",
      required: true,
      acceptLabel: "Anular factura",
    });
    if (motivo === null) return;
    try {
      const actualizada = await trailersysApiRequest("POST", `/facturas/${f.id}/anular`, { motivo });
      trailersysToast(`Factura ${actualizada.numero} anulada.`);
      renderDetalle(actualizada, { soloLectura: false });
      await cargarModulo();
    } catch (error) {
      trailersysToast(error.message || "No se pudo anular la factura.", { type: "error" });
    }
  }

  function imprimirFactura(f) {
    const ventana = window.open("", "_blank", "width=860,height=1000");
    if (!ventana) {
      trailersysToast("El navegador bloqueó la ventana de impresión. Permite las ventanas emergentes para este sitio.", { type: "warning" });
      return;
    }
    const filas = f.detalles.map((d) => `<tr><td>${esc(d.descripcion)}</td><td class="num">${dinero(d.monto)}</td></tr>`).join("");
    const pagos = f.pagos.map((p) => `<tr><td>${fecha(p.fecha)}</td><td>${esc(p.metodo)}</td><td>${esc(p.referencia || "—")}</td><td class="num">${dinero(p.monto)}</td></tr>`).join("");
    ventana.document.write(`<!DOCTYPE html><html lang="es"><head><meta charset="utf-8"><title>${esc(f.numero)}</title>
      <style>
        body{font-family:Inter,"Segoe UI",system-ui,sans-serif;color:#2b2a28;margin:40px;font-size:13px}
        .top{display:flex;justify-content:space-between;align-items:flex-start;border-bottom:3px solid #4f8f52;padding-bottom:16px;margin-bottom:20px}
        .logo{display:flex;align-items:center;gap:10px;font-weight:700;font-size:20px;color:#1f7a6c}
        .logo span{background:#1f7a6c;color:#fff;border-radius:8px;padding:6px 8px;font-size:14px}
        h1{font-size:22px;margin:0 0 4px;text-align:right} .muted{color:#7a776f}
        .grid{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:20px}
        .box{background:#f4f7f8;border-radius:10px;padding:12px 14px}
        table{width:100%;border-collapse:collapse;margin-bottom:18px}
        th{text-align:left;font-size:11px;text-transform:uppercase;letter-spacing:.04em;color:#7a776f;border-bottom:1px solid #d7e0e4;padding:8px}
        td{padding:8px;border-bottom:1px solid #edf1f3} .num{text-align:right;white-space:nowrap}
        tfoot td{border:none} .total td{font-weight:700;font-size:15px;border-top:2px solid #2b2a28}
        .estado{display:inline-block;padding:3px 10px;border-radius:999px;background:#edf4e9;color:#2f5732;font-weight:600}
        .nota{margin-top:30px;font-size:11px;color:#7a776f;border-top:1px solid #d7e0e4;padding-top:10px}
        @media print{body{margin:16mm}}
      </style></head><body>
      <div class="top">
        <div class="logo"><span>TS</span>TrailerSys</div>
        <div><h1>Factura ${esc(f.numero)}</h1><div class="muted" style="text-align:right">Estado: <span class="estado">${esc(f.estado)}</span></div></div>
      </div>
      <div class="grid">
        <div class="box"><div class="muted">Cliente</div><strong>${esc(f.clienteNombre)}</strong><br>RUC/CI: ${esc(f.clienteIdentificacion || "—")}<br>${esc(f.clienteDireccion || "")}${f.clienteCorreo ? `<br>${esc(f.clienteCorreo)}` : ""}</div>
        <div class="box"><div class="muted">Fechas</div>Emisión: <strong>${fecha(f.fechaEmision)}</strong><br>Vencimiento: <strong>${fecha(f.fechaVencimiento)}</strong><br>Saldo pendiente: <strong>${dinero(f.saldo)}</strong></div>
      </div>
      <table><thead><tr><th>Descripción</th><th class="num">Valor</th></tr></thead><tbody>${filas}</tbody>
        <tfoot><tr><td class="num">Subtotal</td><td class="num">${dinero(f.subtotal)}</td></tr>
        <tr><td class="num">IVA ${Number(f.ivaPorcentaje).toLocaleString("es-EC")}%</td><td class="num">${dinero(f.iva)}</td></tr>
        <tr class="total"><td class="num">Total</td><td class="num">${dinero(f.total)}</td></tr></tfoot></table>
      ${pagos ? `<h3 style="font-size:14px">Pagos registrados</h3><table><thead><tr><th>Fecha</th><th>Método</th><th>Referencia</th><th class="num">Monto</th></tr></thead><tbody>${pagos}</tbody></table>` : ""}
      ${f.observaciones ? `<p><strong>Observaciones:</strong> ${esc(f.observaciones)}</p>` : ""}
      <div class="nota">Documento interno de control de cobros. No reemplaza al comprobante electrónico autorizado por el SRI.</div>
      <script>window.onload=()=>{window.print();}<\/script>
      </body></html>`);
    ventana.document.close();
  }

  // ======================================================================
  // Modulo interno: Facturacion
  // ======================================================================
  const body = $("facturasBody");
  const vacio = $("facturasEmpty");
  const buscar = $("facturasBuscar");
  const filtroEstado = $("facturasEstado");

  async function cargarResumen() {
    try {
      const r = await trailersysApiRequest("GET", "/facturas/resumen");
      $("facturacionStats").innerHTML = [
        statCard("bi-wallet2", dinero(r.porCobrar), `Por cobrar · ${r.facturasAbiertas} factura${r.facturasAbiertas === 1 ? "" : "s"}`, "info"),
        statCard("bi-exclamation-octagon", dinero(r.vencido), `Vencido · ${r.facturasVencidas} factura${r.facturasVencidas === 1 ? "" : "s"}`, Number(r.vencido) > 0 ? "danger" : ""),
        statCard("bi-receipt", dinero(r.facturadoMes), "Facturado este mes"),
        statCard("bi-cash-coin", dinero(r.cobradoMes), "Cobrado este mes", "success"),
      ].join("");
    } catch (error) {
      $("facturacionStats").innerHTML = `<div class="dashboard-empty">${esc(error.message)}</div>`;
    }
  }

  async function cargarFacturas() {
    body.innerHTML = '<tr class="loading-row"><td colspan="8">Cargando facturas…</td></tr>';
    const params = new URLSearchParams();
    if (filtroEstado.value) params.set("estado", filtroEstado.value);
    if (buscar.value.trim()) params.set("search", buscar.value.trim());
    try {
      facturasCache = await trailersysApiRequest("GET", `/facturas?${params}`);
      const sinFiltros = !filtroEstado.value && !buscar.value.trim();
      vacio.hidden = !(facturasCache.length === 0 && sinFiltros);
      body.closest(".table-wrap").hidden = !vacio.hidden;
      body.innerHTML = facturasCache.length ? facturasCache.map((f) => `
        <tr class="fila-clicable" data-id="${f.id}">
          <td><div class="table-primary">${esc(f.numero)}</div><div class="table-secondary">${f.detalles.length} viaje${f.detalles.length === 1 ? "" : "s"}</div></td>
          <td><div class="table-primary">${esc(f.clienteNombre)}</div><div class="table-secondary">${esc(f.clienteIdentificacion || "")}</div></td>
          <td>${fecha(f.fechaEmision)}</td>
          <td>${fecha(f.fechaVencimiento)}${f.estado === "Vencida" ? `<div class="table-secondary factura-vencida">hace ${f.diasVencida} d</div>` : ""}</td>
          <td class="num">${dinero(f.total)}</td>
          <td class="num ${Number(f.saldo) > 0 && f.estado !== "Anulada" ? "factura-saldo" : ""}">${dinero(f.saldo)}</td>
          <td>${badge(f.estado)}</td>
          <td class="num"><button type="button" class="btn btn-ghost btn-sm" data-id="${f.id}"><i class="bi bi-eye"></i>Ver</button></td>
        </tr>`).join("") : '<tr class="loading-row"><td colspan="8">No hay facturas con esos filtros.</td></tr>';
    } catch (error) {
      body.innerHTML = `<tr class="loading-row"><td colspan="8">${esc(error.message)}</td></tr>`;
    }
  }

  async function cargarModulo() {
    if (!puedeVer) return cargarMisFacturas();
    await Promise.all([cargarResumen(), cargarFacturas()]);
  }

  if (puedeVer) {
    $("btnNuevaFactura").hidden = !puedeGestionar;
    body.addEventListener("click", (e) => {
      const fila = e.target.closest("[data-id]");
      if (fila) abrirDetalle(fila.dataset.id);
    });
    let timer = null;
    buscar.addEventListener("input", () => { clearTimeout(timer); timer = setTimeout(cargarFacturas, 300); });
    filtroEstado.addEventListener("change", cargarFacturas);
    trailersysApiRequest("GET", "/facturas/configuracion").then((c) => { configuracion = c; }).catch(() => {});
  }

  // --- Nueva factura ---
  const modal = $("facturaModalOverlay");
  const form = $("facturaForm");
  const lista = $("facturaViajesLista");
  const inputCliente = $("facturaCliente");
  const inputDias = $("facturaDiasCredito");
  const inputIva = $("facturaIva");
  let viajesFacturables = [];

  const clienteAuto = trailersysAutocomplete({
    input: $("facturaClienteBuscar"),
    hidden: inputCliente,
    resultados: $("facturaClienteResultados"),
    recurso: "clientes",
    etiqueta: (c) => c.nombre,
    detalle: (c) => c.identificacion,
    onSeleccionar: (c) => cargarViajesFacturables(c.id),
  });

  function abrirNuevaFactura() {
    form.reset();
    clienteAuto.limpiar();
    inputDias.value = configuracion.diasCredito;
    inputIva.value = configuracion.ivaPorcentaje;
    viajesFacturables = [];
    lista.innerHTML = "";
    $("facturaViajesInfo").textContent = "Elige un cliente para ver sus viajes finalizados.";
    form.querySelectorAll(".field-error").forEach((el) => { el.textContent = ""; });
    form.querySelectorAll(".has-error").forEach((el) => el.classList.remove("has-error"));
    actualizarTotales();
    trailersysOpenModal(modal);
    setTimeout(() => $("facturaClienteBuscar").focus(), 50);
  }

  function cerrarNuevaFactura() { trailersysCloseModal(modal); }

  async function cargarViajesFacturables(clienteId) {
    lista.innerHTML = '<div class="table-secondary factura-cargando">Buscando viajes finalizados…</div>';
    try {
      viajesFacturables = await trailersysApiRequest("GET", `/facturas/viajes-facturables?clienteId=${clienteId}`);
      $("facturaViajesInfo").textContent = viajesFacturables.length
        ? `${viajesFacturables.length} viaje${viajesFacturables.length === 1 ? "" : "s"} finalizado${viajesFacturables.length === 1 ? "" : "s"} sin facturar`
        : "";
      lista.innerHTML = viajesFacturables.length ? viajesFacturables.map((v) => `
        <label class="factura-viaje" data-id="${v.id}">
          <input type="checkbox" class="factura-viaje-check" value="${v.id}" />
          <div class="factura-viaje-info">
            <div class="table-primary">${esc(v.origen)} → ${esc(v.destino)}</div>
            <div class="table-secondary">Viaje #${v.id} · ${trailersysFormatDateTime(v.fechaSalida)} · ${esc(v.vehiculoPlaca)}${v.distanciaKm ? ` · ${Math.round(v.distanciaKm).toLocaleString("es-EC")} km` : ""}${v.cargaDescripcion ? ` · ${esc(v.cargaDescripcion)}` : ""}</div>
          </div>
          <div class="factura-viaje-monto input-wrap"><i class="bi bi-currency-dollar"></i><input type="number" min="0.01" step="0.01" placeholder="0,00" disabled aria-label="Valor del viaje ${v.id}" /></div>
        </label>`).join("")
        : '<div class="factura-sin-viajes"><i class="bi bi-inbox"></i> Este cliente no tiene viajes finalizados pendientes de facturar.</div>';
    } catch (error) {
      lista.innerHTML = `<div class="field-error">${esc(error.message)}</div>`;
    }
    actualizarTotales();
  }

  function itemsSeleccionados() {
    return [...lista.querySelectorAll(".factura-viaje")]
      .filter((el) => el.querySelector(".factura-viaje-check").checked)
      .map((el) => ({ viajeId: Number(el.dataset.id), monto: Number(el.querySelector('input[type="number"]').value), el }));
  }

  function actualizarTotales() {
    const items = itemsSeleccionados();
    const subtotal = items.reduce((acc, i) => acc + (i.monto > 0 ? Math.round(i.monto * 100) / 100 : 0), 0);
    const pct = Number(inputIva.value) || 0;
    const iva = Math.round(subtotal * pct) / 100;
    $("facturaSubtotal").textContent = dinero(subtotal);
    $("facturaIvaEtiqueta").textContent = `IVA ${pct.toLocaleString("es-EC")}%`;
    $("facturaIvaValor").textContent = dinero(iva);
    $("facturaTotal").textContent = dinero(subtotal + iva);
    const dias = Math.max(0, Number(inputDias.value) || 0);
    const vence = new Date();
    vence.setDate(vence.getDate() + dias);
    $("facturaVenceTexto").textContent = `Vence el ${vence.toLocaleDateString("es-EC", { day: "2-digit", month: "long", year: "numeric" })}`;
  }

  lista.addEventListener("change", (e) => {
    if (e.target.classList.contains("factura-viaje-check")) {
      const fila = e.target.closest(".factura-viaje");
      const monto = fila.querySelector('input[type="number"]');
      monto.disabled = !e.target.checked;
      fila.classList.toggle("seleccionado", e.target.checked);
      if (e.target.checked) setTimeout(() => monto.focus(), 0);
      else monto.value = "";
      $("facturaViajesError").textContent = "";
    }
    actualizarTotales();
  });
  lista.addEventListener("input", actualizarTotales);
  inputIva.addEventListener("input", actualizarTotales);
  inputDias.addEventListener("input", actualizarTotales);

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const campoCliente = $("fieldFacturaCliente");
    campoCliente.classList.remove("has-error");
    campoCliente.querySelector(".field-error").textContent = "";
    $("facturaViajesError").textContent = "";
    const items = itemsSeleccionados();
    let valido = true;
    if (!inputCliente.value) {
      campoCliente.classList.add("has-error");
      campoCliente.querySelector(".field-error").textContent = "Elige un cliente de la lista.";
      valido = false;
    } else if (!items.length) {
      $("facturaViajesError").textContent = "Marca al menos un viaje para facturar.";
      valido = false;
    } else if (items.some((i) => !(i.monto > 0))) {
      $("facturaViajesError").textContent = "Ingresa el valor de cada viaje marcado.";
      valido = false;
    }
    if (!valido) return;

    const btn = $("facturaGuardar");
    btn.disabled = true;
    try {
      const factura = await trailersysApiRequest("POST", "/facturas", {
        clienteId: Number(inputCliente.value),
        diasCredito: Number(inputDias.value) || 0,
        ivaPorcentaje: Number(inputIva.value) || 0,
        observaciones: $("facturaObservaciones").value.trim() || null,
        items: items.map((i) => ({ viajeId: i.viajeId, monto: Number(i.monto.toFixed(2)) })),
      });
      cerrarNuevaFactura();
      trailersysToast(`Factura ${factura.numero} emitida por ${dinero(factura.total)}.`);
      await cargarModulo();
      renderDetalle(factura, { soloLectura: false });
    } catch (error) {
      trailersysToast(error.message || "No se pudo emitir la factura.", { type: "error" });
    } finally {
      btn.disabled = false;
    }
  });

  $("btnNuevaFactura").addEventListener("click", abrirNuevaFactura);
  $("facturaModalClose").addEventListener("click", cerrarNuevaFactura);
  $("facturaCancelar").addEventListener("click", cerrarNuevaFactura);
  modal.addEventListener("click", (e) => { if (e.target === modal) cerrarNuevaFactura(); });

  // ======================================================================
  // Autoservicio del Cliente: Mis facturas
  // ======================================================================
  async function cargarMisFacturas() {
    const misBody = $("misFacturasBody");
    misBody.innerHTML = '<tr class="loading-row"><td colspan="7">Cargando facturas…</td></tr>';
    try {
      facturasCache = await trailersysApiRequest("GET", "/mis-facturas");
      const abiertas = facturasCache.filter((f) => f.estado !== "Pagada");
      const saldo = abiertas.reduce((a, f) => a + Number(f.saldo), 0);
      const vencidas = facturasCache.filter((f) => f.estado === "Vencida");
      const vencido = vencidas.reduce((a, f) => a + Number(f.saldo), 0);
      $("misFacturasStats").innerHTML = [
        statCard("bi-wallet2", dinero(saldo), `Saldo pendiente · ${abiertas.length} factura${abiertas.length === 1 ? "" : "s"}`, "info"),
        statCard("bi-exclamation-octagon", dinero(vencido), `Vencido · ${vencidas.length}`, vencido > 0 ? "danger" : ""),
        statCard("bi-check2-circle", String(facturasCache.length - abiertas.length), "Facturas pagadas", "success"),
      ].join("");
      $("misFacturasEmpty").hidden = facturasCache.length > 0;
      misBody.closest(".table-wrap").hidden = facturasCache.length === 0;
      misBody.innerHTML = facturasCache.map((f) => `
        <tr class="fila-clicable" data-id="${f.id}">
          <td><div class="table-primary">${esc(f.numero)}</div><div class="table-secondary">${f.detalles.length} viaje${f.detalles.length === 1 ? "" : "s"}</div></td>
          <td>${fecha(f.fechaEmision)}</td>
          <td>${fecha(f.fechaVencimiento)}</td>
          <td class="num">${dinero(f.total)}</td>
          <td class="num ${Number(f.saldo) > 0 ? "factura-saldo" : ""}">${dinero(f.saldo)}</td>
          <td>${badge(f.estado)}</td>
          <td class="num"><button type="button" class="btn btn-ghost btn-sm" data-id="${f.id}"><i class="bi bi-eye"></i>Ver</button></td>
        </tr>`).join("");
    } catch (error) {
      misBody.innerHTML = `<tr class="loading-row"><td colspan="7">${esc(error.message)}</td></tr>`;
    }
  }

  if (esCliente) {
    $("misFacturasBody").addEventListener("click", (e) => {
      const fila = e.target.closest("[data-id]");
      if (fila) abrirDetalle(fila.dataset.id, { soloLectura: true });
    });
  }

  window.addEventListener("trailersys:module-activated", (e) => {
    if (e.detail.module === "facturacion" && puedeVer) cargarModulo();
    if (e.detail.module === "mis-facturas" && esCliente) cargarMisFacturas();
  });
})();
