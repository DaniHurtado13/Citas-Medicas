/* Reglas de formulario que copian las anotaciones de los DTOs del back (@Email, @Size…) */

export const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Contraseñas: @Size(min = 8, max = 72) en el back (72 es el límite de BCrypt) */
export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;

/** Devuelve el mensaje de error de una contraseña nueva, o '' si es válida */
export function errorPassword(p: string): string {
  if (p.length < PASSWORD_MIN) return `Debe tener al menos ${PASSWORD_MIN} caracteres.`;
  if (p.length > PASSWORD_MAX) return `Debe tener máximo ${PASSWORD_MAX} caracteres.`;
  return '';
}
