import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { fmt, TODAY } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { Carga, HBarRow, HBars, Stat, StatStrip } from '../../shared/ui';
import { AdminStore } from './admin.store';

/**
 * Resumen del admin con lo que el back sí entrega (decisión D1, opción a): doctores y especialidades.
 * No hay endpoints de reportes de pacientes ni de citas de toda la clínica, así que no se muestran.
 */
@Component({
  selector: 'app-admin-resumen',
  imports: [RouterLink, Icon, Carga, HBars, StatStrip],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Resumen general</h1>
        <p>Doctores y especialidades de la clínica, al {{ fechaHoy }}.</p>
      </div>
      <a class="btn btn-primary" routerLink="/admin/doctores"><svg appIcon="steth"></svg> Gestionar doctores</a>
    </section>

    @if (!store.cargado() || store.error()) {
      <app-carga texto="Cargando el resumen…" [error]="store.error()" (reintentar)="store.cargar()" />
    } @else {
      <app-stat-strip [items]="stats()" />

      <div class="grid-halves">
        <section class="panel">
          <header class="panel-head"><h2>Doctores activos por especialidad</h2><span class="muted small">Los que reciben reservas</span></header>
          @if (porEspecialidad().length) {
            <app-hbars [rows]="porEspecialidad()" />
          } @else {
            <div class="empty"><svg appIcon="steth"></svg><p>Aún no hay doctores activos.</p></div>
          }
        </section>

        <section class="panel">
          <header class="panel-head"><h2>Especialidades sin doctores activos</h2><span class="muted small">Los pacientes no pueden agendar en ellas</span></header>
          @if (sinDoctor().length) {
            <div class="pref-list">
              @for (e of sinDoctor(); track e.id) {
                <div class="pref"><div><strong>{{ e.nombre }}</strong><span>{{ e.descripcion }}</span></div></div>
              }
            </div>
            <div class="appt-actions"><a class="btn btn-ghost btn-sm" routerLink="/admin/doctores">Asignar o habilitar doctores</a></div>
          } @else {
            <div class="empty"><svg appIcon="check"></svg><p>Todas las especialidades tienen al menos un doctor activo.</p></div>
          }
        </section>
      </div>
    }`,
})
export class AdminResumen {
  protected readonly store = inject(AdminStore);
  protected readonly fechaHoy = fmt(TODAY, { day: 'numeric', month: 'long' });

  private readonly activos = computed(() => this.store.doctores().filter((d) => d.activo));

  protected readonly sinDoctor = computed(() => {
    const conDoctor = new Set(this.activos().map((d) => d.especialidadId));
    return this.store.especialidades().filter((e) => !conDoctor.has(e.id));
  });

  protected readonly stats = computed<Stat[]>(() => {
    const total = this.store.doctores().length;
    const activos = this.activos();
    const inactivos = total - activos.length;
    const promedio = activos.length ? Math.round(activos.reduce((s, d) => s + d.duracionConsultaMin, 0) / activos.length) : 0;
    return [
      { label: 'Doctores activos', value: activos.length, small: ` de ${total}` },
      { label: 'Deshabilitados', value: inactivos, note: inactivos ? 'No reciben reservas' : 'Todos atienden' },
      { label: 'Especialidades', value: this.store.especialidades().length, note: `${this.sinDoctor().length} sin doctores activos` },
      { label: 'Duración promedio', value: promedio || '—', small: promedio ? ' min' : '', note: 'Por cita, entre los activos' },
    ];
  });

  protected readonly porEspecialidad = computed<HBarRow[]>(() => {
    const cuenta = new Map<string, number>();
    this.activos().forEach((d) => cuenta.set(d.especialidadNombre, (cuenta.get(d.especialidadNombre) ?? 0) + 1));
    return [...cuenta.entries()].sort((a, b) => b[1] - a[1]).map(([nombre, n]) => [nombre, n]);
  });

  constructor() {
    this.store.cargar();
  }
}
