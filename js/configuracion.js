/**
 * Configuracion: muestra los datos de la cuenta autenticada via
 * GET /api/auth/me (mas la foto de perfil, autoeditable via
 * PUT /api/auth/me/foto - mismo endpoint que ya usan los Dashboards de
 * Mantenimiento y Supervisor). La administracion de otros usuarios (para
 * Administrador) queda en admin.js, en la misma pantalla.
 */
(function () {
  const card = document.getElementById("perfilCard");
  const emptyState = document.getElementById("perfilEmptyState");
  const emptyText = document.getElementById("perfilEmptyText");

  const esc = (v) => String(v ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  const avatarEl = document.getElementById("perfilAvatar");
  const nombreEl = document.getElementById("perfilNombre");
  const usernameEl = document.getElementById("perfilUsername");
  const rolEl = document.getElementById("perfilRol");
  const correoEl = document.getElementById("perfilCorreo");
  const idEl = document.getElementById("perfilId");
  const sesionEl = document.getElementById("perfilSesion");
  const fotoActions = document.getElementById("perfilFotoActions");
  const fotoInput = document.getElementById("perfilFoto");

  // Límite real que valida el backend (AuthController.LIMITE_FOTO_CARACTERES,
  // 3.000.000 caracteres del data URL en base64 ya codificado) - no el
  // tamaño del archivo original. Un archivo de origen crece ~33% al pasar a
  // base64, así que comparar el tamaño del archivo (como hace el resto de
  // los formularios con foto en esta app) contra un límite pensado para el
  // texto ya codificado rechaza archivos que en los hechos sí entran, o dado
  // el margen usado en otros formularios, deja pasar al cliente uno que el
  // backend va a rechazar de todas formas con un mensaje menos claro.
  const LIMITE_FOTO_CARACTERES = 3_000_000;

  function renderAvatar(me) {
    if (me.foto) {
      avatarEl.innerHTML = `<img src="${esc(me.foto)}" alt="Foto de ${esc(me.nombre)}" />`;
    } else {
      avatarEl.textContent = me.username.trim().slice(0, 2).toUpperCase();
    }
  }

  fotoInput.addEventListener("change", async () => {
    const file = fotoInput.files[0];
    if (!file) return;
    try {
      const dataUrl = await new Promise((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result);
        reader.onerror = reject;
        reader.readAsDataURL(file);
      });
      if (dataUrl.length > LIMITE_FOTO_CARACTERES) {
        trailersysToast("La imagen es muy grande. Elige una foto más liviana o recórtala.", { type: "warning" });
        return;
      }
      const actualizado = await trailersysApiRequest("PUT", "/auth/me/foto", { foto: dataUrl });
      renderAvatar({ username: usernameEl.textContent.replace(/^@/, ""), nombre: nombreEl.textContent, foto: actualizado.foto });
    } catch (error) {
      trailersysToast(error.message || "No se pudo actualizar la foto.", { type: "error" });
    } finally {
      fotoInput.value = "";
    }
  });

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

    // Usuario.foto es la foto de la cuenta de acceso, distinta de la foto
    // propia de Cliente/Conductor (que ya tienen su propio paquete de
    // autoservicio con su propia foto, ligada a ESE registro - no a esta
    // cuenta). Mostrar aquí el botón para Cliente subiría una foto que no
    // se ve en ningún lado de su autoservicio, así que solo se ofrece a
    // roles internos (Administrador, Coordinador, Mantenimiento, Supervisor
    // - estos dos últimos ya la suben desde su propio Dashboard, no aquí).
    fotoActions.hidden = session?.role === "cliente" || session?.role === "conductor";

    renderAvatar(me);
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
        ].map(([label, value]) => `<div class="route-stat"><div class="route-stat-label">${esc(label)}</div><div class="route-stat-value">${esc(value ?? "—")}</div></div>`).join("");
      } catch { empresaCard.hidden = true; }
    }
  }

  // Cambio de la contraseña propia (PUT /api/auth/me/password). El backend
  // exige la contraseña actual y responde 409 si no coincide.
  const passwordForm = document.getElementById("perfilPasswordForm");
  const passwordMensaje = document.getElementById("perfilPasswordMensaje");
  const passwordGuardar = document.getElementById("perfilPasswordGuardar");

  function mostrarMensajePassword(texto, esError) {
    passwordMensaje.hidden = false;
    passwordMensaje.textContent = texto;
    passwordMensaje.style.color = esError ? "var(--color-danger, #c0392b)" : "var(--color-success, #1e8449)";
  }

  passwordForm?.addEventListener("submit", async (event) => {
    event.preventDefault();
    const actual = document.getElementById("perfilPasswordActual").value;
    const nueva = document.getElementById("perfilPasswordNueva").value;
    const confirmar = document.getElementById("perfilPasswordConfirmar").value;

    if (!actual || !nueva) {
      mostrarMensajePassword("Completa todos los campos.", true);
      return;
    }
    if (nueva.length < 8) {
      mostrarMensajePassword("La nueva contraseña debe tener al menos 8 caracteres.", true);
      return;
    }
    if (nueva !== confirmar) {
      mostrarMensajePassword("La confirmación no coincide con la nueva contraseña.", true);
      return;
    }

    passwordGuardar.disabled = true;
    try {
      await trailersysApiRequest("PUT", "/auth/me/password", { passwordActual: actual, passwordNueva: nueva });
      passwordForm.reset();
      mostrarMensajePassword("Contraseña actualizada correctamente.", false);
    } catch (error) {
      mostrarMensajePassword(error.message || "No se pudo actualizar la contraseña.", true);
    } finally {
      passwordGuardar.disabled = false;
    }
  });

  render();
})();
