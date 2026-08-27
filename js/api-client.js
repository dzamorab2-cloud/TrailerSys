/**
 * Cliente HTTP para la API del backend (Spring Boot). Centraliza la URL
 * base, el header de autenticacion JWT y el manejo de errores ({@link
 * ApiError} del backend), para que cada modulo solo llame a
 * trailersysApiRequest() en vez de repetir fetch() + manejo de errores.
 */
const TRAILERSYS_API_BASE_URL = "http://localhost:8080/api";

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

  if (response.status === 401) {
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
  if (!pager || !pager.classList.contains("pagination")) {
    pager = document.createElement("div"); pager.className = "pagination"; anchorEl.after(pager);
  }
  pager.innerHTML = `<span>${Number(data.totalElements).toLocaleString("es-EC")} registros · Página ${data.number + 1} de ${Math.max(1, data.totalPages)}</span><div class="pagination-actions"><button class="btn btn-ghost pager-prev" ${data.first ? "disabled" : ""}>Anterior</button><button class="btn btn-ghost pager-next" ${data.last ? "disabled" : ""}>Siguiente</button></div>`;
  pager.querySelector(".pager-prev").onclick = () => onPage(data.number - 1);
  pager.querySelector(".pager-next").onclick = () => onPage(data.number + 1);
}
