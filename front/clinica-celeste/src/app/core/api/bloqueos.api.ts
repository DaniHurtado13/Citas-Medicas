import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { BloqueoRequest, BloqueoResponse } from './dtos';

const URL = '/api/bloqueos';

/** Endpoints de /api/bloqueos: periodos en que el doctor de la sesión no atiende */
@Injectable({ providedIn: 'root' })
export class BloqueosApi {
  private readonly http = inject(HttpClient);

  /** Solo los que no han terminado, por fecha de inicio */
  listarVigentes() {
    return this.http.get<BloqueoResponse[]>(URL);
  }

  crear(body: BloqueoRequest) {
    return this.http.post<BloqueoResponse>(URL, body);
  }

  actualizar(id: string, body: BloqueoRequest) {
    return this.http.put<BloqueoResponse>(`${URL}/${id}`, body);
  }

  eliminar(id: string) {
    return this.http.delete<void>(`${URL}/${id}`);
  }
}
