(function () {
  // Si ya hay una sesion activa, no tiene sentido mostrar el login de nuevo.
  if (trailersysGetSession()) {
    window.location.href = "app.html";
    return;
  }

  // El backend devuelve el rol en mayusculas (enum Rol); TRAILERSYS_ROLES
  // usa las claves en minusculas, asi que se traduce aqui una sola vez.
  const ROL_BACKEND_A_FRONTEND = {
    ADMINISTRADOR: "administrador",
    COORDINADOR: "coordinador",
    MANTENIMIENTO: "mantenimiento",
    CONDUCTOR: "conductor",
    SUPERVISOR: "supervisor",
    CLIENTE: "cliente",
  };

  const form = document.getElementById("loginForm");
  const usuarioInput = document.getElementById("usuario");
  const contrasenaInput = document.getElementById("contrasena");
  const alertBox = document.getElementById("loginAlert");
  const alertText = document.getElementById("loginAlertText");
  const submitBtn = form.querySelector('button[type="submit"]');

  function setFieldError(fieldWrapId, message) {
    const wrap = document.getElementById(fieldWrapId);
    wrap.classList.toggle("has-error", Boolean(message));
    wrap.querySelector(".field-error").textContent = message || "";
  }

  function showAlert(message) {
    alertText.textContent = message;
    alertBox.hidden = false;
  }

  function hideAlert() {
    alertBox.hidden = true;
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    hideAlert();

    const usuario = usuarioInput.value.trim();
    const contrasena = contrasenaInput.value;
    let hasError = false;

    if (!usuario) {
      setFieldError("fieldUsuario", "El usuario es obligatorio.");
      hasError = true;
    } else {
      setFieldError("fieldUsuario", "");
    }

    if (!contrasena) {
      setFieldError("fieldContrasena", "La contraseña es obligatoria.");
      hasError = true;
    } else {
      setFieldError("fieldContrasena", "");
    }

    if (hasError) {
      showAlert("Usuario y contraseña son obligatorios.");
      return;
    }

    submitBtn.disabled = true;
    try {
      const data = await trailersysApiRequest("POST", "/auth/login", {
        username: usuario,
        password: contrasena,
      });

      const role = ROL_BACKEND_A_FRONTEND[data.rol];
      trailersysSetSession({
        token: data.token,
        id: data.id,
        username: data.username,
        nombre: data.nombre,
        role,
        loginAt: new Date().toISOString(),
      });

      window.location.href = "app.html";
    } catch (error) {
      showAlert(error.message || "Usuario o contraseña incorrectos.");
    } finally {
      submitBtn.disabled = false;
    }
  });
})();
