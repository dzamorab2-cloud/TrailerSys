/**
 * Capa de datos local (localStorage) para el prototipo frontend.
 * Cada modulo llama a estas funciones igual que llamaria a una API;
 * al integrar el backend con PostgreSQL, solo esta capa cambia por
 * llamadas fetch() reales, sin tocar la logica de los modulos.
 */
const TRAILERSYS_DB_PREFIX = "trailersys_db_";

function trailersysList(collection) {
  const raw = localStorage.getItem(TRAILERSYS_DB_PREFIX + collection);
  if (!raw) return [];
  try {
    return JSON.parse(raw);
  } catch {
    return [];
  }
}

function trailersysPersist(collection, items) {
  localStorage.setItem(TRAILERSYS_DB_PREFIX + collection, JSON.stringify(items));
}

function trailersysSeedIfEmpty(collection, seedItems) {
  const key = TRAILERSYS_DB_PREFIX + collection;
  if (localStorage.getItem(key) === null) {
    trailersysPersist(collection, seedItems);
  }
}

function trailersysUpsert(collection, item) {
  const items = trailersysList(collection);
  const index = items.findIndex((existing) => existing.id === item.id);
  if (index === -1) {
    item.id = item.id || `${collection}-${Date.now()}-${Math.round(Math.random() * 1000)}`;
    items.push(item);
  } else {
    items[index] = item;
  }
  trailersysPersist(collection, items);
  return item;
}

function trailersysRemove(collection, id) {
  const items = trailersysList(collection).filter((item) => item.id !== id);
  trailersysPersist(collection, items);
}
