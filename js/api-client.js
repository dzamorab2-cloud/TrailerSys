/**
 * Cliente HTTP para la API del backend (Spring Boot). Centraliza la URL
 * base, el header de autenticacion JWT y el manejo de errores ({@link
 * ApiError} del backend), para que cada modulo solo llame a
 * trailersysApiRequest() en vez de repetir fetch() + manejo de errores.
 */
// En desarrollo local el backend siempre esta en localhost:8080. Pero al
// compartir la app por un Dev Tunnel de VS Code (Terminal > PORTS > reenviar
// puerto), el navegador de quien abre el link NO tiene nada en su propio
// localhost:8080 - hay que apuntar al tunel del BACKEND, no al del frontend.
// Un Dev Tunnel tiene la forma https://<id>-<puerto>.<region>.devtunnels.ms;
// el <id> y la <region> son iguales para los dos puertos reenviados de la
// misma sesion, asi que basta con cambiar "-5500." (frontend) por "-8080."
// (backend) en el propio hostname para armar la URL del backend sin tener
// que configurarla a mano cada vez que se genera un link nuevo.
const TRAILERSYS_API_BASE_URL = (() => {
  const { protocol, hostname } = window.location;
  if (hostname.endsWith(".devtunnels.ms")) {
    return `${protocol}//${hostname.replace(/-\d+\./, "-8080.")}/api`;
  }
  return "http://localhost:8080/api";
})();

class TrailersysApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = "TrailersysApiError";
    this.status = status;
  }
}

async function trailersysApiRequest(method, path, body) {
  const session = trailersysGetSession();
  const headers = { "Content-Type": "application/json" };
  if (session?.token) {
    headers.Authorization = `Bearer ${session.token}`;
  }

  let response;
  try {
    response = await fetch(`${TRAILERSYS_API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch {
    throw new TrailersysApiError("No se pudo conectar con el servidor. Verifica tu conexión.", 0);
  }

  // Un 401 en /auth/login significa credenciales invalidas, no una sesion
  // expirada: aqui no hay sesion que limpiar ni token que renovar, asi que
  // se deja caer al manejo generico de abajo para mostrar el mensaje real
  // del backend ("Usuario o contraseña incorrectos.") en vez de forzar una
  // redireccion a index.html que interrumpe el propio formulario de login.
  if (response.status === 401 && path !== "/auth/login") {
    trailersysClearSession();
    window.location.href = "index.html";
    throw new TrailersysApiError("Sesión expirada.", 401);
  }

  if (response.status === 204) {
    return null;
  }

  const data = await response.json().catch(() => null);

  if (!response.ok) {
    const message = data?.message || "Ocurrió un error inesperado.";
    throw new TrailersysApiError(message, response.status);
  }

  return data;
}

async function trailersysPagedRequest(resource, page = 0, size = 24, extraParams = {}) {
  const params = new URLSearchParams({ page, size });
  Object.entries(extraParams).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") params.set(key, value);
  });
  return trailersysApiRequest("GET", `/paginas/${resource}?${params}`);
}

function trailersysRenderPager(anchor, data, onPage) {
  const anchorEl = typeof anchor === "string" ? document.getElementById(anchor) : anchor;
  if (!anchorEl) return;
  let pager = anchorEl.nextElementSibling;
  if (!data) {
    // Sin datos (lista vacia, sin resultados de busqueda, o error de carga):
    // si quedo un paginador de un render anterior con resultados, hay que
    // quitarlo - si no, se ve un "X registros - Pagina Y de Z" residual que
    // ya no corresponde a nada visible en pantalla.
    if (pager && pager.classList.contains("pagination")) pager.remove();
    return;
  }
  if (!pager || !pager.classList.contains("pagination")) {
    pager = document.createElement("div"); pager.className = "pagination"; anchorEl.after(pager);
  }
  pager.innerHTML = `<span>${Number(data.totalElements).toLocaleString("es-EC")} registros · Página ${data.number + 1} de ${Math.max(1, data.totalPages)}</span><div class="pagination-actions"><button class="btn btn-ghost pager-prev" ${data.first ? "disabled" : ""}>Anterior</button><button class="btn btn-ghost pager-next" ${data.last ? "disabled" : ""}>Siguiente</button></div>`;
  pager.querySelector(".pager-prev").onclick = () => onPage(data.number - 1);
  pager.querySelector(".pager-next").onclick = () => onPage(data.number + 1);
}
