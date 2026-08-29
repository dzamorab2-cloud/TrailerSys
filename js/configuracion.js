/**
 * Configuracion (solo lectura por ahora): muestra los datos de la cuenta
 * autenticada via GET /api/auth/me. La administracion de otros usuarios
 * queda para una fase futura (no hay backend para eso todavia).
 */
(function () {
  const card = document.getElementById("perfilCard");
  const emptyState = document.getElementById("perfilEmptyState");
  const emptyText = document.getElementById("perfilEmptyText");

  const avatarEl = document.getElementById("perfilAvatar");
  const nombreEl = document.getElementById("perfilNombre");
  const usernameEl = document.getElementById("perfilUsername");
  const rolEl = document.getElementById("perfilRol");
  const correoEl = document.getElementById("perfilCorreo");
  const idEl = document.getElementById("perfilId");
  const sesionEl = document.getElementById("perfilSesion");

  async function render() {
    let me;
    try {
      me = await trailersysApiRequest("GET", "/auth/me");
    } catch (error) {
      card.hidden = true;
      emptyState.hidden = false;
      emptyText.textContent = error.message || "Ocurrió un error al conectar con el servidor.";
      return;
    }

    card.hidden = false;
    emptyState.hidden = true;

    const session = trailersysGetSession();
    const roleInfo = TRAILERSYS_ROLES[session?.role];

    avatarEl.textContent = me.username.trim().slice(0, 2).toUpperCase();
    nombreEl.textContent = me.nombre;
    usernameEl.textContent = `@${me.username}`;
    rolEl.textContent = roleInfo ? roleInfo.label : me.rol;
    correoEl.textContent = me.correo || "Sin correo registrado";
    idEl.textContent = `#${me.id}`;
    sesionEl.textContent = session?.loginAt ? trailersysFormatDateTime(session.loginAt) : "—";

    const empresaCard = document.getElementById("perfilClienteEmpresa");
    if (session?.role === "cliente") {
      try {
        const empresa = await trailersysApiRequest("GET", "/mis-cargas/perfil");
        empresaCard.hidden = false;
        document.getElementById("perfilClienteEmpresaDatos").innerHTML = [
          ["Razón social", empresa.nombre], ["RUC / identificación", empresa.identificacion],
          ["Teléfono", empresa.telefono], ["Correo", empresa.correo || "Sin registrar"],
          ["Dirección", empresa.direccion], ["Servicios", empresa.servicios || "Sin registrar"],
        ].map(([label, value]) => `<div class="route-stat"><div class="route-stat-label">${label}</div><div class="route-stat-value">${String(value ?? "—").replace(/[&<>]/g, "")}</div></div>`).join("");
      } catch { empresaCard.hidden = true; }
    }
  }

  render();
})();
