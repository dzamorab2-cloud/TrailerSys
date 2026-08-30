(function () {
  const body = document.getElementById("reclamosBody");
  if (!body) return;
  const empty = document.getElementById("reclamosEmpty");
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));
  const labelNovedad = (v) => ({ COMPLETO: "Completo", INCOMPLETO: "Incompleto", DANADO: "Dañado", INCORRECTO: "Incorrecto", OTRO: "Otro" }[v] || v || "—");
  const labelEstadoReclamo = (v) => ({ ABIERTO: "Abierto", EN_REVISION: "En revisión", RESUELTO: "Resuelto" }[v] || v || "—");
  const peso = (kg) => {
    const kilos = Number(kg) || 0;
    return `${kilos.toLocaleString("es-EC")} kg / ${(kilos * 2.2046226218).toLocaleString("es-EC", { maximumFractionDigits: 2 })} lb`;
  };
  let cache = [];

  // GET /reclamos ya devuelve el ViajeResponse completo (con los datos de
  // conductor/vehiculo/carga denormalizados), asi que la guia no necesita
  // pedir nada aparte a /conductores/{id}, /vehiculos/{id} ni /cargas/{id}
  // como si hacia falta en Viajes antes de denormalizar esos campos.
  function showReclamoGuide(v) {
    trailersysShowGuide({
      tipo: "Reclamo", codigo: "REC", id: v.id, estado: labelEstadoReclamo(v.estadoReclamoCliente),
      secciones: [
        { titulo: "Novedad reportada por el cliente", icono: "bi-exclamation-triangle", campos: [
          ["Tipo de novedad", labelNovedad(v.novedadRecepcionCliente)],
          ["Detalle del cliente", v.observacionConfirmacionCliente || "Sin detalle"],
          ["Evidencia fotográfica", v.evidenciaRecepcionCliente ? "Adjunta (ver en el sistema)" : "No adjunta"],
          ["Reportado el", v.fechaConfirmacionCliente ? trailersysFormatDateTime(v.fechaConfirmacionCliente) : "—"]
        ] },
        { titulo: "Resolución", icono: "bi-reply", campos: [
          ["Estado del reclamo", labelEstadoReclamo(v.estadoReclamoCliente)],
          ["Respuesta al cliente", v.respuestaReclamoCliente || "Pendiente de responder"],
          ["Resuelto el", v.fechaResolucionReclamoCliente ? trailersysFormatDateTime(v.fechaResolucionReclamoCliente) : "—"]
        ] },
        { titulo: "Conductor", icono: "bi-person-badge", campos: [
          ["Nombre completo", v.conductorNombres], ["Identificación", v.conductorIdentificacion],
          ["Teléfono", v.conductorTelefono], ["Licencia", v.conductorLicenciaNumero],
          ["Categoría", v.conductorLicenciaCategoria]
        ] },
        { titulo: "Vehículo", icono: "bi-truck", campos: [
          ["Placa", v.vehiculoPlaca], ["Marca", v.vehiculoMarca], ["Modelo", v.vehiculoModelo],
          ["Tipo", v.vehiculoTipo], ["Año", v.vehiculoAnio], ["Color", v.vehiculoColor]
        ] },
        { titulo: "Entrega y recorrido", icono: "bi-signpost-split", campos: [
          ["Cliente", v.clienteNombre], ["Mercancía", v.cargaDescripcion || "Sin carga asociada"],
          ["Peso", v.cargaPeso != null ? peso(v.cargaPeso) : "—"],
          ["Origen", v.origen], ["Destino", v.destino],
          ["Fecha de salida", trailersysFormatDateTime(v.fechaSalida)]
        ] }
      ]
    });
  }

  async function render() {
    try {
      cache = await trailersysApiRequest("GET", "/reclamos");
      empty.hidden = cache.length > 0; body.closest(".table-wrap").hidden = cache.length === 0;
      body.innerHTML = cache.map((v) => `<tr><td>#${v.id}</td><td>${esc(v.clienteNombre)}</td><td>${esc(v.novedadRecepcionCliente)}</td><td>${esc(v.observacionConfirmacionCliente)}</td><td><span class="badge ${v.estadoReclamoCliente === "RESUELTO" ? "badge-success" : "badge-warning"}">${esc(v.estadoReclamoCliente)}</span></td><td><button class="btn btn-ghost" data-action="guia" data-id="${v.id}" title="Ver e imprimir guía"><i class="bi bi-file-earmark-text"></i> Guía</button> <button class="btn btn-ghost" data-action="responder" data-id="${v.id}"><i class="bi bi-reply"></i> Responder</button></td></tr>`).join("");
    } catch (e) { body.innerHTML = `<tr><td colspan="6">${esc(e.message)}</td></tr>`; }
  }
  body.onclick = async (e) => {
    const b = e.target.closest("button[data-id]");
    if (!b) return;
    const viaje = cache.find((item) => String(item.id) === b.dataset.id);
    if (!viaje) return;
    if (b.dataset.action === "guia") { showReclamoGuide(viaje); return; }
    const respuesta = prompt("Escribe la respuesta que verá el cliente:"); if (!respuesta?.trim()) return; const resolver = confirm("¿Marcar el reclamo como resuelto? (Cancelar lo deja en revisión)"); try { await trailersysApiRequest("PUT", `/reclamos/${b.dataset.id}`, { respuesta: respuesta.trim(), estado: resolver ? "RESUELTO" : "EN_REVISION" }); await render(); } catch (err) { alert(err.message); } };
  window.addEventListener("trailersys:module-activated", (e) => { if (e.detail.module === "reclamos") render(); });
})();
