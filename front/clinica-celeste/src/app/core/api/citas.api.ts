import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { CitaResponse, PaginaResponse, Paginacion, ReprogramarCitaRequest, ReservarCitaRequest } from './dtos';
import { parametros } from './paginas';

const URL = '/api/citas';

/** Endpoints de /api/citas */
@Injectable({ providedIn: 'root' })
export class CitasApi {
  private readonly http = inject(HttpClient);

  // ---------- Paciente ----------
  reservar(body: ReservarCitaRequest) {
    return this.http.post<CitaResponse>(URL, body);
  }

  mias(p: Paginacion = {}) {
    return this.http.get<PaginaResponse<CitaResponse>>(`${URL}/mias`, { params: parametros({ ...p }) });
  }

  /** Cancela la original y devuelve la cita nueva, enlazada por citaOrigenId */
  reprogramar(id: string, body: ReprogramarCitaRequest) {
    return this.http.post<CitaResponse>(`${URL}/${id}/reprogramacion`, body);
  }

  // ---------- Doctor ----------
  /** Citas no canceladas del doctor de la sesión, por hora. El rango es de máximo 31 días */
  agenda(desde: string, hasta: string) {
    return this.http.get<CitaResponse[]>(`${URL}/agenda`, { params: parametros({ desde, hasta }) });
  }

  confirmar(id: string) {
    return this.http.patch<CitaResponse>(`${URL}/${id}/confirmar`, null);
  }

  /** Solo cuando la hora de la cita ya pasó */
  completar(id: string) {
    return this.http.patch<CitaResponse>(`${URL}/${id}/completar`, null);
  }

  /** Solo cuando la hora de la cita ya pasó */
  noAsistio(id: string) {
    return this.http.patch<CitaResponse>(`${URL}/${id}/no-asistio`, null);
  }

  // ---------- Paciente o doctor ----------
  cancelar(id: string) {
    return this.http.patch<void>(`${URL}/${id}/cancelar`, null);
  }
}
