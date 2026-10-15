import { Injectable } from '@angular/core';

const CLAVE = 'cc.token';

/**
 * Guarda el JWT en sessionStorage: sobrevive a recargar la página y se borra al cerrar la pestaña.
 * También lo mantiene en memoria, por si el navegador bloquea el almacenamiento (modo privado estricto).
 */
@Injectable({ providedIn: 'root' })
export class TokenStorage {
  private token: string | null = leerGuardado();

  leer(): string | null {
    return this.token;
  }

  guardar(token: string): void {
    this.token = token;
    try { sessionStorage.setItem(CLAVE, token); } catch { /* queda solo en memoria */ }
  }

  borrar(): void {
    this.token = null;
    try { sessionStorage.removeItem(CLAVE); } catch { /* nada que borrar */ }
  }
}

function leerGuardado(): string | null {
  try { return sessionStorage.getItem(CLAVE); } catch { return null; }
}

/** true si el token ya venció según su campo `exp`. Solo evita una petición inútil: quien decide es el back */
export function tokenVencido(token: string): boolean {
  try {
    const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const { exp } = JSON.parse(atob(payload)) as { exp?: number };
    return typeof exp === 'number' && exp * 1000 <= Date.now();
  } catch {
    return true; // un token ilegible no sirve
  }
}
