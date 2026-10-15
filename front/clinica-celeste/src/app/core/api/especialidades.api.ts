import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { EspecialidadRequest, EspecialidadResponse } from './dtos';

const URL = '/api/especialidades';

/** Endpoints de /api/especialidades */
@Injectable({ providedIn: 'root' })
export class EspecialidadesApi {
  private readonly http = inject(HttpClient);

  listar() {
    return this.http.get<EspecialidadResponse[]>(URL);
  }

  // ---------- Solo admin ----------
  crear(body: EspecialidadRequest) {
    return this.http.post<EspecialidadResponse>(URL, body);
  }

  actualizar(id: string, body: EspecialidadRequest) {
    return this.http.put<EspecialidadResponse>(`${URL}/${id}`, body);
  }

  /** 409 si algún doctor (activo o no) la tiene */
  eliminar(id: string) {
    return this.http.delete<void>(`${URL}/${id}`);
  }
}
