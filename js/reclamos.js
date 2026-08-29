(function () {
  const body = document.getElementById("reclamosBody");
  if (!body) return;
  const empty = document.getElementById("reclamosEmpty");
  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));
  async function render() {
    try {
      const datos = await trailersysApiRequest("GET", "/reclamos");
      empty.hidden = datos.length > 0; body.closest(".table-wrap").hidden = datos.length === 0;
      body.innerHTML = datos.map((v) => `<tr><td>#${v.id}</td><td>${esc(v.clienteNombre)}</td><td>${esc(v.novedadRecepcionCliente)}</td><td>${esc(v.observacionConfirmacionCliente)}</td><td><span class="badge ${v.estadoReclamoCliente === "RESUELTO" ? "badge-success" : "badge-warning"}">${esc(v.estadoReclamoCliente)}</span></td><td><button class="btn btn-ghost" data-id="${v.id}"><i class="bi bi-reply"></i> Responder</button></td></tr>`).join("");
    } catch (e) { body.innerHTML = `<tr><td colspan="6">${esc(e.message)}</td></tr>`; }
  }
  body.onclick = async (e) => { const b = e.target.closest("button[data-id]"); if (!b) return; const respuesta = prompt("Escribe la respuesta que verá el cliente:"); if (!respuesta?.trim()) return; const resolver = confirm("¿Marcar el reclamo como resuelto? (Cancelar lo deja en revisión)"); try { await trailersysApiRequest("PUT", `/reclamos/${b.dataset.id}`, { respuesta: respuesta.trim(), estado: resolver ? "RESUELTO" : "EN_REVISION" }); await render(); } catch (err) { alert(err.message); } };
  window.addEventListener("trailersys:module-activated", (e) => { if (e.detail.module === "reclamos") render(); });
})();
