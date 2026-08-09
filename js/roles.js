/**
 * Catalogo de roles y permisos de modulo (seccion 6 del documento de proyecto).
 * Mientras no exista backend, esto es la unica fuente de verdad para el
 * control de acceso; al integrar PostgreSQL, el rol vendra del usuario
 * autenticado en vez de elegirse en el login.
 */
const TRAILERSYS_STORAGE_KEY = "trailersys_session";

const TRAILERSYS_ROLES = {
  administrador: {
    label: "Administrador",
    modules: [
      "dashboard", "vehiculos", "conductores", "clientes", "cargas",
      "viajes", "seguimiento", "mantenimientos", "reportes", "configuracion",
    ],
  },
  coordinador: {
    label: "Coordinador / Operador",
    modules: ["dashboard", "vehiculos", "conductores", "cargas", "viajes", "seguimiento"],
  },
  mantenimiento: {
    label: "Responsable de Mantenimiento",
    modules: ["dashboard", "vehiculos", "mantenimientos"],
  },
  conductor: {
    label: "Conductor",
    modules: ["dashboard", "viajes", "seguimiento"],
  },
  supervisor: {
    label: "Supervisor / Consulta",
    modules: ["dashboard", "vehiculos", "viajes", "reportes"],
  },
};

function trailersysGetSession() {
  const raw = sessionStorage.getItem(TRAILERSYS_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    return null;
  }
}

function trailersysSetSession(session) {
  sessionStorage.setItem(TRAILERSYS_STORAGE_KEY, JSON.stringify(session));
}

function trailersysClearSession() {
  sessionStorage.removeItem(TRAILERSYS_STORAGE_KEY);
}
