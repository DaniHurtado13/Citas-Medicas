import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { ActualizarDoctorRequest, CrearDoctorRequest, DesactivacionDoctorResponse, DoctorResponse, PaginaResponse, Paginacion } from './dtos';
import { parametros } from './paginas';

const URL = '/api/doctores';

export interface FiltroDoctores extends Paginacion {
  especialidadId?: string;
  incluirInactivos?: boolean; // solo admin
}

/** Endpoints de /api/doctores */
@Injectable({ providedIn: 'root' })
export class DoctoresApi {
  private readonly http = inject(HttpClient);

  listar(filtro: FiltroDoctores = {}) {
    return this.http.get<PaginaResponse<DoctorResponse>>(URL, { params: parametros({ ...filtro }) });
  }

  obtener(id: string) {
    return this.http.get<DoctorResponse>(`${URL}/${id}`);
  }

  /** Horas libres de un día ('2026-10-14T09:30:00'); ya descuenta bloqueos, citas tomadas y horas pasadas */
  disponibilidad(doctorId: string, fecha: string) {
    return this.http.get<string[]>(`${URL}/${doctorId}/disponibilidad`, { params: parametros({ fecha }) });
  }

  // ---------- Solo admin ----------
  /** multipart/form-data: el navegador arma la cabecera con el boundary, por eso no se fija Content-Type */
  crear(datos: CrearDoctorRequest) {
    const form = new FormData();
    for (const [k, v] of Object.entries(datos)) form.append(k, v instanceof File ? v : String(v));
    return this.http.post<DoctorResponse>(URL, form);
  }

  actualizar(id: string, body: ActualizarDoctorRequest) {
    return this.http.put<DoctorResponse>(`${URL}/${id}`, body);
  }

  /** Cancela sus citas futuras y avisa a los pacientes; devuelve cuántas canceló */
  desactivar(id: string) {
    return this.http.patch<DesactivacionDoctorResponse>(`${URL}/${id}/desactivar`, null);
  }

  activar(id: string) {
    return this.http.patch<DoctorResponse>(`${URL}/${id}/activar`, null);
  }

  /** Como blob: un <a href> directo daría 401 porque el navegador no enviaría el token */
  certificado(id: string) {
    return this.http.get(`${URL}/${id}/certificado`, { responseType: 'blob' });
  }
}
