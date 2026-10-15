import { HttpParams } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { PaginaResponse, Paginacion } from './dtos';

/** Arma los query params omitiendo los valores vacíos (HttpClient no acepta `undefined`) */
export function parametros(valores: Record<string, string | number | boolean | undefined | null>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(valores)) {
    if (v !== undefined && v !== null && v !== '') p = p.set(k, String(v));
  }
  return p;
}

/** Pide página tras página hasta tenerlas todas. Pensado para listas pequeñas (doctores, historial de un paciente) */
export async function todasLasPaginas<T>(
  pedir: (p: Paginacion) => Observable<PaginaResponse<T>>,
  base: Paginacion = {},
  size = 100,
): Promise<T[]> {
  const primera = await firstValueFrom(pedir({ ...base, page: 0, size }));
  const resto = await Promise.all(
    Array.from({ length: primera.totalPaginas - 1 }, (_, i) => firstValueFrom(pedir({ ...base, page: i + 1, size }))),
  );
  return [primera, ...resto].flatMap((p) => p.contenido);
}
