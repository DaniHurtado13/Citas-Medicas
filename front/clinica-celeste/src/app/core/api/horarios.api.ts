import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { HorarioRequest, HorarioResponse } from './dtos';

const URL = '/api/horarios';

/** Endpoints de /api/horarios: el horario semanal del doctor de la sesión */
@Injectable({ providedIn: 'root' })
export class HorariosApi {
  private readonly http = inject(HttpClient);

  /** Ordenados de lunes a domingo y por hora */
  listarMios() {
    return this.http.get<HorarioResponse[]>(URL);
  }

  crear(body: HorarioRequest) {
    return this.http.post<HorarioResponse>(URL, body);
  }

  actualizar(id: string, body: HorarioRequest) {
    return this.http.put<HorarioResponse>(`${URL}/${id}`, body);
  }

  eliminar(id: string) {
    return this.http.delete<void>(`${URL}/${id}`);
  }
}
