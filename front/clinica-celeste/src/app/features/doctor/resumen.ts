import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';
import { VistaStateService } from '../../core/vista-state.service';
import { ESTADOS, EstadoCita, estadoKey } from '../../core/models';
import { addDays, ahora, cap, fechaDe, fmt, greeting, horaDe, iso, longDate, mesDe, nowHM, parseISO, startOfWeek, TODAY } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { Badge, BarChart, Carga, StatStrip } from '../../shared/ui';
import { HoraPipe, LongDatePipe } from '../../shared/pipes';
import { AgendaDoctorStore } from './agenda-doctor.store';

/** Meses que necesita el resumen: los últimos 30 días (asistencia), esta semana, los próximos 7 días y el mes siguiente */
const MESES = [...new Set([mesDe(addDays(TODAY, -30)), mesDe(TODAY), mesDe(addDays(TODAY, 7)), mesDe(new Date(TODAY.getFullYear(), TODAY.getMonth() + 1, 1))])];

@Component({
  selector: 'app-doctor-resumen',
  imports: [RouterLink, Icon, Badge, BarChart, Carga, StatStrip, HoraPipe, LongDatePipe],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">{{ saludo }}, {{ firstName() }}</h1>
        <p>{{ hoyTexto }}. {{ mensajeHoy() }}</p>
      </div>
      <a class="btn btn-primary" routerLink="/doctor/agenda"><svg appIcon="calendar"></svg> Abrir agenda</a>
    </section>

    @if (!listo() || store.error()) {
      <app-carga texto="Cargando tu agenda…" [error]="store.error()" (reintentar)="store.refrescar(meses)" />
    } @else {
      <app-stat-strip [items]="stats()" />

      <div class="grid-2">
        <section class="panel">
          <header class="panel-head"><h2>Citas de hoy</h2><span class="muted small">{{ today().length }} en total</span></header>
          @if (today().length) {
            <ul class="timeline">
              @for (c of today(); track c.id) {
                <li class="tl-item" [class.is-past]="hora(c.fechaHora) < now">
                  <time>{{ c.fechaHora | hora }}</time>
                  <div class="tl-body"><strong>{{ c.pacienteNombre }}</strong><span>{{ c.motivoConsulta }}</span></div>
                  <app-badge [estado]="c.estado" />
                </li>
              }
            </ul>
          } @else {
            <div class="empty"><svg appIcon="leaf"></svg><p>No tienes citas hoy. Aprovecha para revisar las de mañana.</p></div>
          }
        </section>

        <div class="col">
          <section class="panel next-card">
            <header class="panel-head"><h2>Tu próxima cita</h2></header>
            @if (next(); as n) {
              <div class="next-when">{{ fecha(n.fechaHora) === hoyIso ? 'Hoy' : (n.fechaHora | longDate) }}, <span class="nowrap">{{ n.fechaHora | hora }}</span></div>
              <p class="next-who">{{ n.pacienteNombre }}</p>
              <p class="next-why">{{ n.motivoConsulta }}</p>
              <div class="appt-actions" style="margin-top:16px">
                <button class="btn btn-ghost btn-sm" (click)="verEnAgenda(fecha(n.fechaHora))">Ver en la agenda</button>
              </div>
            } @else {
              <p>No hay citas próximas.</p>
            }
          </section>

          <section class="panel">
            <header class="panel-head"><h2>Próximos 7 días</h2><span class="muted small">Citas activas</span></header>
            <app-bar-chart [data]="days()" [hl]="0" />
          </section>

          <section class="panel">
            <header class="panel-head"><h2>Estados del mes</h2><span class="muted small">{{ month().length }} citas</span></header>
            @if (month().length) {
              <div class="stackbar">
                @for (c of counts(); track c[0]; let i = $index) {
                  <span [attr.data-s]="key(c[0])" [style.--w]="(c[1] / total()) * 100 + '%'" [style.animation-delay.ms]="i * 80" [title]="estados[c[0]] + ': ' + c[1]"></span>
                }
              </div>
              <div class="legend">
                @for (c of counts(); track c[0]) {
                  <span [attr.data-s]="key(c[0])"><i></i>{{ estados[c[0]] }} <b>{{ c[1] }}</b></span>
                }
              </div>
            } @else {
              <p class="muted">Aún no hay citas este mes.</p>
            }
          </section>
        </div>
      </div>
    }`,
})
export class DoctorResumen {
  private readonly auth = inject(AuthService);
  private readonly vista = inject(VistaStateService);
  private readonly router = inject(Router);
  protected readonly store = inject(AgendaDoctorStore);

  protected readonly meses = MESES;
  protected readonly estados = ESTADOS;
  protected readonly key = estadoKey;
  protected readonly fecha = fechaDe;
  protected readonly hora = horaDe;
  protected readonly saludo = greeting();
  protected readonly hoyTexto = longDate(TODAY);
  protected readonly hoyIso = iso(TODAY);
  protected readonly now = nowHM();

  protected readonly listo = computed(() => MESES.every((m) => this.store.tiene(m)));
  // La agenda del back ya viene sin canceladas: todas las citas son activas o ya resueltas
  private readonly citas = this.store.citas;
  protected readonly firstName = computed(() => this.auth.nombre().split(' ')[0]);
  protected readonly today = computed(() => this.citas().filter((c) => fechaDe(c.fechaHora) === this.hoyIso));
  protected readonly todayActive = computed(() => this.today().filter((c) => c.estado === 'PENDIENTE' || c.estado === 'CONFIRMADA'));

  protected readonly mensajeHoy = computed(() => {
    if (!this.listo()) return '';
    const n = this.todayActive().length;
    return n ? `Tienes ${n} ${n === 1 ? 'cita' : 'citas'} hoy.` : 'Hoy no tienes citas programadas.';
  });

  protected readonly next = computed(() => {
    const limite = ahora();
    return this.citas().find((c) => c.fechaHora >= limite && (c.estado === 'CONFIRMADA' || c.estado === 'PENDIENTE'));
  });

  protected readonly stats = computed(() => {
    const t = this.hoyIso;
    const ws = startOfWeek(TODAY), we = addDays(ws, 6);
    const week = this.citas().filter((c) => { const d = parseISO(c.fechaHora); return d >= ws && d <= we; }).length;
    const pend = this.citas().filter((c) => fechaDe(c.fechaHora) >= t && c.estado === 'PENDIENTE').length;
    // Asistencia: atendidas sobre (atendidas + no asistió) en los últimos 30 días
    const desde = iso(addDays(TODAY, -30));
    const past30 = this.citas().filter((c) => fechaDe(c.fechaHora) < t && fechaDe(c.fechaHora) >= desde && (c.estado === 'COMPLETADA' || c.estado === 'NO_ASISTIO'));
    const asist = past30.length ? Math.round((past30.filter((c) => c.estado === 'COMPLETADA').length / past30.length) * 100) : null;
    return [
      { label: 'Citas hoy', value: this.todayActive().length },
      { label: 'Esta semana', value: week },
      { label: 'Por confirmar', value: pend, note: 'Hasta fin del próximo mes' },
      asist === null
        ? { label: 'Asistencia', value: '—', note: 'Sin citas cerradas en 30 días' }
        : { label: 'Asistencia', value: asist, small: ' %', note: 'Últimos 30 días', up: asist >= 80 },
    ];
  });

  protected readonly days = computed(() => Array.from({ length: 7 }, (_, i) => {
    const d = addDays(TODAY, i);
    const s = iso(d);
    return {
      l: i === 0 ? 'Hoy' : cap(fmt(d, { weekday: 'short' }).replace('.', '')),
      v: this.citas().filter((c) => fechaDe(c.fechaHora) === s).length,
    };
  }));

  protected readonly month = computed(() => this.citas().filter((c) => c.fechaHora.startsWith(this.hoyIso.slice(0, 7))));
  protected readonly counts = computed(() =>
    (['COMPLETADA', 'CONFIRMADA', 'PENDIENTE', 'NO_ASISTIO'] as EstadoCita[])
      .map((e) => [e, this.month().filter((c) => c.estado === e).length] as [EstadoCita, number])
      .filter((c) => c[1]));
  protected readonly total = computed(() => this.counts().reduce((s, c) => s + c[1], 0) || 1);

  constructor() {
    this.store.refrescar(MESES);
  }

  protected verEnAgenda(fecha: string) {
    const d = parseISO(fecha);
    this.vista.calSel.set(fecha);
    this.vista.calMonth.set(new Date(d.getFullYear(), d.getMonth(), 1));
    this.router.navigateByUrl('/doctor/agenda');
  }
}
