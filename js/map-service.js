/**
 * Servicio de mapas 100% gratuito y sin API key, pensado para un
 * prototipo frontend sin backend:
 *  - Geocodificacion: Nominatim (OpenStreetMap) convierte un nombre de
 *    ciudad/direccion en coordenadas.
 *  - Ruteo: OSRM (servidor de demostracion publico) calcula la ruta,
 *    la distancia y la duracion estimada entre dos coordenadas.
 *  - Mapa visual: Leaflet + tiles de OpenStreetMap (cargados en app.html).
 *
 * Los resultados de geocodificacion se cachean en localStorage porque
 * el servicio publico de Nominatim pide no exceder ~1 solicitud por
 * segundo y no repetir busquedas identicas innecesariamente. Si el
 * proyecto crece a produccion con mucho trafico, esta es la pieza a
 * reemplazar por un proveedor propio o de pago (aqui queda aislada en
 * un solo archivo para que ese cambio no toque el resto de los modulos).
 */
const TRAILERSYS_GEOCODE_CACHE_KEY = "trailersys_geocode_cache";

function trailersysGeocodeCacheGet(query) {
  try {
    const cache = JSON.parse(localStorage.getItem(TRAILERSYS_GEOCODE_CACHE_KEY) || "{}");
    return cache[query.toLowerCase().trim()] || null;
  } catch {
    return null;
  }
}

function trailersysGeocodeCacheSet(query, value) {
  let cache = {};
  try {
    cache = JSON.parse(localStorage.getItem(TRAILERSYS_GEOCODE_CACHE_KEY) || "{}");
  } catch {
    cache = {};
  }
  cache[query.toLowerCase().trim()] = value;
  localStorage.setItem(TRAILERSYS_GEOCODE_CACHE_KEY, JSON.stringify(cache));
}

/**
 * Convierte un nombre de lugar (ej. "Quito, Ecuador") en coordenadas.
 * Devuelve { lat, lng, label } o null si no se encontro / hubo un error de red.
 */
async function trailersysGeocode(query) {
  const cached = trailersysGeocodeCacheGet(query);
  if (cached) return cached;

  const url = `https://nominatim.openstreetmap.org/search?format=json&limit=1&q=${encodeURIComponent(query)}`;

  try {
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    if (!response.ok) return null;
    const results = await response.json();
    if (!results.length) return null;

    const result = {
      lat: Number(results[0].lat),
      lng: Number(results[0].lon),
      label: results[0].display_name,
    };
    trailersysGeocodeCacheSet(query, result);
    return result;
  } catch {
    return null;
  }
}

/**
 * Calcula la ruta por carretera entre dos coordenadas usando OSRM.
 * Devuelve { distanceKm, durationMin, path } donde path es un arreglo
 * de [lat, lng] listo para dibujar en Leaflet, o null si hubo un error.
 */
async function trailersysGetRoute(origin, destination) {
  const url = `https://router.project-osrm.org/route/v1/driving/${origin.lng},${origin.lat};${destination.lng},${destination.lat}?overview=full&geometries=geojson`;

  try {
    const response = await fetch(url);
    if (!response.ok) return null;
    const data = await response.json();
    if (!data.routes || !data.routes.length) return null;

    const route = data.routes[0];
    return {
      distanceKm: route.distance / 1000,
      durationMin: route.duration / 60,
      path: route.geometry.coordinates.map(([lng, lat]) => [lat, lng]),
    };
  } catch {
    return null;
  }
}

function trailersysFormatDuration(minutes) {
  const total = Math.round(minutes);
  const hours = Math.floor(total / 60);
  const mins = total % 60;
  if (hours === 0) return `${mins} min`;
  if (mins === 0) return `${hours} h`;
  return `${hours} h ${mins} min`;
}

function trailersysFormatDateTime(isoOrDate) {
  const date = isoOrDate instanceof Date ? isoOrDate : new Date(isoOrDate);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString("es-EC", { dateStyle: "medium", timeStyle: "short" });
}
