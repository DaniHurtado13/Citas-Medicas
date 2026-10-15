import { Injectable, signal } from '@angular/core';
import { EstadoCita } from './models';
import { iso, startOfWeek, TODAY } from './fechas';

export interface Reserva {
  espId: string;
  docId: string | null;
  weekStart: Date;
  slot: { fecha: string; hora: string } | null;
  motivo: string;
  reprog: string | null; // id de la cita que se está reprogramando
}

export const reservaVacia = (espId = ''): Reserva =>
  ({ espId, docId: null, weekStart: startOfWeek(TODAY), slot: null, motivo: '', reprog: null });

/** Estado de las vistas que debe sobrevivir al cambiar de página (filtros, pestañas, borrador de reserva) */
@Injectable({ providedIn: 'root' })
export class VistaStateService {
  readonly calMonth = signal(new Date(TODAY.getFullYear(), TODAY.getMonth(), 1));
  readonly calSel = signal(iso(TODAY));
  readonly calFilter = signal<'todas' | EstadoCita>('todas');

  readonly docSearch = signal('');
  readonly docEsp = signal('');
  readonly docEstado = signal<'todos' | 'activos' | 'inactivos'>('todos');

  readonly reserva = signal<Reserva>(reservaVacia());
  readonly citasTab = signal<'proximas' | 'historial'>('proximas');

  reset() {
    this.calMonth.set(new Date(TODAY.getFullYear(), TODAY.getMonth(), 1));
    this.calSel.set(iso(TODAY));
    this.calFilter.set('todas');
    this.docSearch.set('');
    this.docEsp.set('');
    this.docEstado.set('todos');
    this.reserva.set(reservaVacia());
    this.citasTab.set('proximas');
  }
}
