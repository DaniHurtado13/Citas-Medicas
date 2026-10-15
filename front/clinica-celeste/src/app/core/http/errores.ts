import { HttpErrorResponse } from '@angular/common/http';
import { ProblemDetail } from '../api/dtos';

const GENERICO = 'Algo salió mal. Intenta de nuevo.';

/**
 * Convierte un error HTTP en un mensaje para la persona.
 * `porEstado` deja que cada pantalla use su propio texto para un código (p. ej. 401 en el login);
 * si no lo define, se usa el `detail` del ProblemDetail, que el back ya escribe en español.
 */
export function mensajeDeError(err: unknown, porEstado: Partial<Record<number, string>> = {}): string {
  if (!(err instanceof HttpErrorResponse)) return GENERICO;
  if (err.status === 0) return 'No pudimos conectar con el servidor. Revisa tu conexión e intenta de nuevo.';

  const propio = porEstado[err.status];
  if (propio) return propio;

  if (err.status === 429) return `Hiciste demasiados intentos. Espera ${espera(err)} y vuelve a intentarlo.`;
  if (err.status >= 500) return 'El servidor tuvo un problema. Intenta de nuevo en unos minutos.';

  const problema = err.error as ProblemDetail | null;
  return problema?.detail ?? GENERICO;
}

/** Texto a partir de la cabecera Retry-After (en segundos) */
function espera(err: HttpErrorResponse): string {
  const s = Number(err.headers.get('Retry-After'));
  if (!s) return 'unos minutos';
  if (s < 60) return `${s} segundos`;
  const min = Math.ceil(s / 60);
  return min === 1 ? '1 minuto' : `${min} minutos`;
}

/** Errores de validación del back campo por campo ({ email: 'debe ser una dirección de correo…' }); vacío si no hay */
export function erroresDeCampos(err: unknown): Record<string, string> {
  if (!(err instanceof HttpErrorResponse) || err.status !== 400) return {};
  return (err.error as ProblemDetail | null)?.errores ?? {};
}
