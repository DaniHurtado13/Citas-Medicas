import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { CitaResponse } from '../../core/api/dtos';
import { VistaStateService } from '../../core/vista-state.service';
import { ahora, iso, lastMonths, parseISO, TODAY } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { Avatar, Badge, BarChart, Carga, StatStrip } from '../../shared/ui';
import { FechaPipe, HoraPipe, LongDatePipe } from '../../shared/pipes';
import { AccionesCita } from './acciones-cita';
import { CatalogoStore } from './catalogo.store';
import { CitasPacienteStore } from './citas-paciente.store';

@Component({
  selector: 'app-paciente-inicio',
  imports: [RouterLink, Icon, Avatar, Badge, BarChart, Carga, StatStrip, FechaPipe, HoraPipe, LongDatePipe],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Hola, {{ firstName() }}</h1>
        <p>{{ store.cargado() ? mensaje() : 'Revisando tus citas…' }}</p>
      </div>
      <a class="btn btn-primary" routerLink="/paciente/agendar"><svg appIcon="plus"></svg> Agendar cita</a>
    </section>

    @if (!store.cargado() || store.error()) {
      <app-carga texto="Cargando tus citas…" [error]="store.error()" (reintentar)="store.cargar()" />
    } @else {
      <app-stat-strip [items]="stats()" />

      <div class="grid-2">
        <div class="col">
          <section class="panel next-card">
            <header class="panel-head"><h2>Tu próxima cita</h2>@if (next(); as n) {<app-badge [estado]="n.estado" />}</header>
            @if (next(); as n) {
              <div class="next-when">{{ n.fechaHora | longDate }}, <span class="nowrap">{{ n.fechaHora | hora }}</span></div>
              <p class="next-who">{{ n.doctorNombre }}{{ especialidad(n) ? ', ' + especialidad(n).toLowerCase() : '' }}</p>
              <p class="next-why">{{ n.motivoConsulta }}.{{ consultorio(n) ? ' ' + consultorio(n) + '.' : '' }}</p>
              <div class="appt-actions" style="margin-top:16px">
                <button class="btn btn-ghost btn-sm" [disabled]="!acc.puedeReprogramar(n)" [title]="acc.puedeReprogramar(n) ? '' : acc.motivoBloqueo(n, 'reprogramar')" (click)="acc.reprogramar(n)"><svg appIcon="refresh"></svg> Reprogramar</button>
                <button class="btn btn-ghost btn-sm" [disabled]="!acc.puedeCancelar(n)" [title]="acc.puedeCancelar(n) ? '' : acc.motivoBloqueo(n, 'cancelar')" (click)="acc.cancelar(n)">Cancelar cita</button>
              </div>
            } @else {
              <p>Cuando agendes una cita aparecerá aquí.</p>
              <div class="appt-actions" style="margin-top:16px"><a class="btn btn-ghost btn-sm" routerLink="/paciente/agendar">Agendar ahora</a></div>
            }
          </section>
          <section class="panel">
            <header class="panel-head"><h2>Tus citas por mes</h2><span class="muted small">Últimos 6 meses</span></header>
            <app-bar-chart [data]="months()" [hl]="5" />
          </section>
        </div>

        <section class="panel">
          <header class="panel-head"><h2>Historial reciente</h2><button class="link small" style="border:0;background:none" (click)="verHistorial()">Ver todo</button></header>
          @if (history().length) {
            <ul class="history">
              @for (c of history(); track c.id) {
                <li><app-avatar [name]="c.doctorNombre" /><div><strong>{{ c.motivoConsulta }}</strong><span>{{ c.doctorNombre }}, {{ c.fechaHora | fecha: { day: 'numeric', month: 'short', year: 'numeric' } }}</span></div><app-badge [estado]="c.estado" /></li>
              }
            </ul>
          } @else {
            <div class="empty"><svg appIcon="clipboard"></svg><p>Aquí verás las citas a las que ya asististe.</p></div>
          }
        </section>
      </div>
    }`,
})
export class PacienteInicio {
  private readonly auth = inject(AuthService);
  private readonly catalogo = inject(CatalogoStore);
  private readonly vista = inject(VistaStateService);
  private readonly router = inject(Router);
  protected readonly store = inject(CitasPacienteStore);
  protected readonly acc = inject(AccionesCita);

  private readonly citas = this.store.citas;
  protected readonly firstName = computed(() => this.auth.nombre().split(' ')[0]);

  private readonly upcoming = computed(() => {
    const now = ahora();
    return this.citas().filter((c) => c.fechaHora >= now && c.estado !== 'CANCELADA');
  });
  protected readonly next = computed(() => this.upcoming()[0]);

  protected readonly mensaje = computed(() => {
    const n = this.next();
    if (!n) return 'No tienes citas próximas.';
    const days = Math.round((parseISO(n.fechaHora).getTime() - TODAY.getTime()) / 86400000);
    return days === 0 ? 'Tienes una cita hoy.' : days === 1 ? 'Tu próxima cita es mañana.' : `Tu próxima cita es en ${days} días.`;
  });

  protected readonly stats = computed(() => {
    const year = String(TODAY.getFullYear());
    const done = this.citas().filter((c) => c.estado === 'COMPLETADA');
    return [
      { label: `Citas en ${year}`, value: this.citas().filter((c) => c.fechaHora.startsWith(year)).length },
      { label: 'Asistidas', value: done.length },
      { label: 'Canceladas', value: this.citas().filter((c) => c.estado === 'CANCELADA').length },
      { label: 'Doctores que te han atendido', value: new Set(done.map((c) => c.doctorId)).size },
    ];
  });

  protected readonly months = computed(() => lastMonths(6).map((m) => {
    const k = iso(m.d).slice(0, 7);
    return { l: m.l, v: this.citas().filter((c) => c.fechaHora.startsWith(k) && c.estado !== 'CANCELADA').length };
  }));

  protected readonly history = computed(() => {
    const now = ahora();
    return this.citas().filter((c) => c.fechaHora < now).reverse().slice(0, 4);
  });

  constructor() {
    this.store.cargar();
  }

  protected especialidad(c: CitaResponse) { return this.catalogo.doctor(c.doctorId)?.especialidadNombre ?? ''; }
  protected consultorio(c: CitaResponse) { return this.catalogo.doctor(c.doctorId)?.consultorio ?? ''; }

  protected verHistorial() {
    this.vista.citasTab.set('historial');
    this.router.navigateByUrl('/paciente/mis-citas');
  }
}
