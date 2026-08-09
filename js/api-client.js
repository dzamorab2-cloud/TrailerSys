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
