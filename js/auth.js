(function () {
  // Si ya hay una sesion activa, no tiene sentido mostrar el login de nuevo.
  if (trailersysGetSession()) {
    window.location.href = "app.html";
    return;
  }

  const form = document.getElementById("loginForm");
  const usuarioInput = document.getElementById("usuario");
  const contrasenaInput = document.getElementById("contrasena");
  const rolSelect = document.getElementById("rol");
  const alertBox = document.getElementById("loginAlert");
  const alertText = document.getElementById("loginAlertText");

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

  form.addEventListener("submit", function (event) {
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

    // Autenticacion simulada: sin backend aun, cualquier credencial no vacia
    // es valida. Al integrar PostgreSQL esto se reemplaza por una llamada
    // a la API que valide y devuelva el rol real del usuario.
    const role = rolSelect.value;
    trailersysSetSession({
      username: usuario,
      role,
      loginAt: new Date().toISOString(),
    });

    window.location.href = "app.html";
  });
})();
