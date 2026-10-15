import { EstadoCita } from './models';

/* Espejo de TransicionEstadoCita.java: el front solo ofrece las acciones que el backend aceptaría */
const TRANSICIONES_VALIDAS: Record<EstadoCita, EstadoCita[]> = {
  PENDIENTE: ['CONFIRMADA', 'CANCELADA'],
  CONFIRMADA: ['COMPLETADA', 'CANCELADA', 'NO_ASISTIO'],
  COMPLETADA: [],
  CANCELADA: [],
  NO_ASISTIO: [],
};

export function puedeTransicionar(actual: EstadoCita, nuevo: EstadoCita): boolean {
  return TRANSICIONES_VALIDAS[actual].includes(nuevo);
}
