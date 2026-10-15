import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { CitasApi } from '../../core/api/citas.api';
import { CitaResponse } from '../../core/api/dtos';
import { mensajeDeError } from '../../core/http/errores';
import { primerDiaDe, ultimoDiaDe } from '../../core/fechas';

/** Estados a los que el doctor puede llevar una cita desde su agenda */
export type CambioDoctor = 'CONFIRMADA' | 'COMPLETADA' | 'NO_ASISTIO' | 'CANCELADA';

const porFecha = (a: CitaResponse, b: CitaResponse) => a.fechaHora.localeCompare(b.fechaHora);

/**
 * Agenda del doctor de la sesión. El back entrega como máximo 31 días por petición y sin las canceladas,
 * así que se pide mes a mes ('2026-10') y se guarda por mes. Resumen, Agenda y el menú lateral leen de aquí.
 */
@Injectable({ providedIn: 'root' })
export class AgendaDoctorStore {
  private readonly api = inject(CitasApi);
  private readonly auth = inject(AuthService);

  private readonly porMes = signal(new Map<string, CitaResponse[]>());
  private readonly enCurso = new Map<string, Promise<void>>();
  private dueno: string | null = null;

  /** Citas no canceladas de todos los meses ya pedidos, por hora */
  readonly citas = computed(() => [...this.porMes().values()].flat().sort(porFecha));
  readonly error = signal('');

  /** ¿Ya llegó ese mes? */
  tiene(mes: string): boolean {
    return this.porMes().has(mes);
  }

  /** Vuelve a pedir los meses (al entrar a una vista, para ver las reservas nuevas) */
  async refrescar(meses: string[]): Promise<void> {
    this.verificarDueno();
    await Promise.all([...new Set(meses)].map((m) => this.pedir(m)));
  }

  /** Pide solo los meses que falten (al moverse por el calendario) */
  async asegurar(meses: string[]): Promise<void> {
    this.verificarDueno();
    await Promise.all([...new Set(meses)].filter((m) => !this.tiene(m)).map((m) => this.pedir(m)));
  }

  /** Aplica el cambio en el back y luego en la agenda local. Lanza el HttpErrorResponse si el back lo rechaza */
  async cambiar(c: CitaResponse, nuevo: CambioDoctor): Promise<void> {
    if (nuevo === 'CANCELADA') {
      await firstValueFrom(this.api.cancelar(c.id));
      this.reemplazar(c.id, null); // la agenda del back no muestra canceladas
      return;
    }
    const peticion =
      nuevo === 'CONFIRMADA' ? this.api.confirmar(c.id)
      : nuevo === 'COMPLETADA' ? this.api.completar(c.id)
      : this.api.noAsistio(c.id);
    this.reemplazar(c.id, await firstValueFrom(peticion));
  }

  private reemplazar(id: string, cita: CitaResponse | null) {
    this.porMes.update((m) => {
      const nuevo = new Map<string, CitaResponse[]>();
      m.forEach((lista, mes) => nuevo.set(mes, lista.flatMap((c) => (c.id !== id ? [c] : cita ? [cita] : []))));
      return nuevo;
    });
  }

  /** Si cambió el doctor de la sesión, se olvida la agenda del anterior */
  private verificarDueno() {
    const doctorId = this.auth.usuario()?.doctorId ?? null;
    if (doctorId === this.dueno) return;
    this.dueno = doctorId;
    this.porMes.set(new Map());
    this.enCurso.clear();
    this.error.set('');
  }

  private pedir(mes: string): Promise<void> {
    const actual = this.enCurso.get(mes);
    if (actual) return actual;
    const dueno = this.dueno;
    const p = firstValueFrom(this.api.agenda(primerDiaDe(mes), ultimoDiaDe(mes)))
      .then((citas) => {
        if (dueno !== this.dueno) return;
        this.porMes.update((m) => new Map(m).set(mes, citas));
        this.error.set('');
      })
      .catch((e) => {
        if (dueno === this.dueno) this.error.set(mensajeDeError(e));
      })
      .finally(() => {
        if (this.enCurso.get(mes) === p) this.enCurso.delete(mes);
      });
    this.enCurso.set(mes, p);
    return p;
  }
}
