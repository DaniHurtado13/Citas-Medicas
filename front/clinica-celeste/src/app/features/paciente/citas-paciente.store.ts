import { inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CitasApi } from '../../core/api/citas.api';
import { CitaResponse } from '../../core/api/dtos';
import { todasLasPaginas } from '../../core/api/paginas';
import { mensajeDeError } from '../../core/http/errores';
import { CatalogoStore } from './catalogo.store';

const porFecha = (a: CitaResponse, b: CitaResponse) => a.fechaHora.localeCompare(b.fechaHora);

/**
 * Citas del paciente de la sesión. Inicio, Agendar, Mis citas y el menú lateral leen de aquí,
 * así una cancelación se ve al instante en todas las vistas sin volver a pedir nada.
 */
@Injectable({ providedIn: 'root' })
export class CitasPacienteStore {
  private readonly api = inject(CitasApi);
  private readonly auth = inject(AuthService);
  private readonly catalogo = inject(CatalogoStore);

  /** De la más antigua a la más reciente */
  readonly citas = signal<CitaResponse[]>([]);
  readonly cargado = signal(false);
  readonly error = signal('');

  private dueno: string | null = null;
  private carga: Promise<void> | null = null;

  /** Pide las citas de nuevo. Si cambió el paciente de la sesión, primero borra las del anterior */
  cargar(): Promise<void> {
    const pacienteId = this.auth.usuario()?.pacienteId ?? null;
    if (pacienteId !== this.dueno) {
      this.dueno = pacienteId;
      this.citas.set([]);
      this.cargado.set(false);
      this.carga = null;
    }
    if (!pacienteId) return Promise.resolve();
    this.carga ??= this.pedir(pacienteId).finally(() => (this.carga = null));
    return this.carga;
  }

  async reservar(doctorId: string, fechaHora: string, motivoConsulta: string): Promise<CitaResponse> {
    const cita = await firstValueFrom(this.api.reservar({ doctorId, fechaHora, motivoConsulta }));
    this.citas.update((l) => [...l, cita].sort(porFecha));
    return cita;
  }

  async cancelar(id: string): Promise<void> {
    await firstValueFrom(this.api.cancelar(id));
    this.marcarCancelada(id);
  }

  /** El back cancela la original y crea una nueva enlazada: se refleja igual aquí */
  async reprogramar(id: string, nuevaFechaHora: string): Promise<CitaResponse> {
    const nueva = await firstValueFrom(this.api.reprogramar(id, { nuevaFechaHora }));
    this.marcarCancelada(id);
    this.citas.update((l) => [...l, nueva].sort(porFecha));
    return nueva;
  }

  private marcarCancelada(id: string) {
    this.citas.update((l) => l.map((c) => (c.id === id ? { ...c, estado: 'CANCELADA' } : c)));
  }

  private async pedir(pacienteId: string): Promise<void> {
    try {
      const [citas] = await Promise.all([
        todasLasPaginas((p) => this.api.mias(p), { sort: 'fechaHora,asc' }),
        this.catalogo.cargar(),
      ]);
      await this.catalogo.completar(citas.map((c) => c.doctorId));
      if (pacienteId !== this.dueno) return; // la sesión cambió mientras llegaba la respuesta
      this.citas.set(citas);
      this.error.set('');
    } catch (e) {
      if (pacienteId === this.dueno) this.error.set(mensajeDeError(e));
    } finally {
      if (pacienteId === this.dueno) this.cargado.set(true);
    }
  }
}
