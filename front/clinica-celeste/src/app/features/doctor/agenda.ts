import { Component, computed, effect, ElementRef, inject, signal, untracked, viewChild } from '@angular/core';
import { CitaResponse } from '../../core/api/dtos';
import { mensajeDeError } from '../../core/http/errores';
import { DialogService, ToastService } from '../../core/ui.service';
import { VistaStateService } from '../../core/vista-state.service';
import { ESTADOS, EstadoCita, estadoKey } from '../../core/models';
import { addDays, ahora, fechaDe, fmtHora, horaDe, iso, longDate, mesDe, startOfWeek, TODAY } from '../../core/fechas';
import { puedeTransicionar } from '../../core/transicion-estado-cita';
import { Icon, IconName } from '../../shared/icons';
import { Badge } from '../../shared/ui';
import { FechaPipe, HoraPipe, LongDatePipe } from '../../shared/pipes';
import { AgendaDoctorStore, CambioDoctor } from './agenda-doctor.store';

type Filtro = 'todas' | EstadoCita;

interface Accion { e: CambioDoctor; label: string; cls: string; ic?: IconName; }

// La agenda del back no incluye las canceladas, así que no hay filtro para ellas
const CHIPS: [Filtro, string][] = [
  ['todas', 'Todas'], ['PENDIENTE', 'Por confirmar'], ['CONFIRMADA', 'Confirmadas'], ['COMPLETADA', 'Atendidas'], ['NO_ASISTIO', 'No asistió'],
];

const AVISOS: Record<CambioDoctor, string> = {
  CONFIRMADA: 'Cita confirmada',
  COMPLETADA: 'Cita marcada como atendida',
  NO_ASISTIO: 'Quedó registrada la inasistencia',
  CANCELADA: 'Cita cancelada',
};

@Component({
  selector: 'app-doctor-agenda',
  imports: [Icon, Badge, FechaPipe, HoraPipe, LongDatePipe],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Agenda</h1>
        <p>Toca un día para ver sus citas, confirmar las pendientes o registrar si el paciente asistió.</p>
      </div>
    </section>

    <div class="agenda-toolbar">
      <div class="month-nav">
        <button class="icon-btn" (click)="mover(-1)" aria-label="Mes anterior"><svg appIcon="left"></svg></button>
        <h2>{{ vista.calMonth() | fecha: { month: 'long', year: 'numeric' } : true }}</h2>
        <button class="icon-btn" (click)="mover(1)" aria-label="Mes siguiente"><svg appIcon="right"></svg></button>
        <button class="btn btn-ghost btn-sm" (click)="irHoy()">Hoy</button>
      </div>
      <div class="chips" role="group" aria-label="Filtrar por estado">
        @for (ch of chips; track ch[0]) {
          <button class="chip" [class.is-active]="vista.calFilter() === ch[0]" (click)="vista.calFilter.set(ch[0])"
                  [attr.data-s]="key(ch[0])" [attr.aria-pressed]="vista.calFilter() === ch[0]">
            @if (ch[0] !== 'todas') {<i></i>}{{ ch[1] }} <em>{{ chipCount(ch[0]) }}</em>
          </button>
        }
      </div>
    </div>

    <div class="agenda-layout">
      <section class="panel p0" [attr.aria-busy]="!store.tiene(mesVisible())">
        <div class="cal-head">
          @for (d of diasSemana; track d) { <span>{{ d }}</span> }
        </div>
        <div class="cal-grid">
          @for (cell of cells(); track cell.iso) {
            <button class="cal-day" [class.is-out]="cell.out" [class.is-today]="cell.iso === hoy" [class.is-selected]="cell.iso === vista.calSel()"
                    (click)="seleccionar(cell.iso)" [attr.aria-label]="cell.label + ', ' + cell.list.length + ' citas'">
              <span class="cal-num">{{ cell.date.getDate() }}</span>
              @for (c of cell.list.slice(0, 3); track c.id) {
                <span class="cal-evt" [attr.data-s]="key(c.estado)">{{ horaCorta(c) }} {{ c.pacienteNombre.split(' ')[0] }}</span>
              }
              @if (cell.list.length > 3) { <span class="cal-more">{{ cell.list.length - 3 }} más</span> }
              <span class="cal-dots">
                @for (c of cell.list.slice(0, 4); track c.id) { <i [attr.data-s]="key(c.estado)"></i> }
              </span>
            </button>
          }
        </div>
      </section>

      <aside class="panel day-panel" #dayPanel>
        <header class="panel-head">
          <h2>{{ vista.calSel() | longDate }}</h2>
          <span class="muted small">{{ resumenDia() }}</span>
        </header>
        @if (!store.tiene(mesDe(vista.calSel()))) {
          <div class="empty" [attr.role]="store.error() ? 'alert' : 'status'">
            @if (store.error()) {
              <svg appIcon="alert"></svg><p>{{ store.error() }}</p>
              <button class="btn btn-ghost btn-sm" (click)="reintentar()"><svg appIcon="refresh"></svg> Reintentar</button>
            } @else {
              <svg appIcon="clock"></svg><p>Cargando la agenda…</p>
            }
          </div>
        } @else if (dayList().length) {
          <ul class="appt-list">
            @for (c of dayList(); track c.id; let i = $index) {
              <li class="appt" [style.animation-delay.ms]="i * 50">
                <div class="appt-top"><span class="appt-time">{{ c.fechaHora | hora }}</span><app-badge [estado]="c.estado" /></div>
                <strong class="appt-name">{{ c.pacienteNombre }}</strong>
                <p class="appt-motivo">{{ c.motivoConsulta }}</p>
                @let acc = acciones(c);
                @if (acc.length) {
                  <div class="appt-actions">
                    @for (a of acc; track a.e) {
                      <button [class]="a.cls" [disabled]="ocupada() === c.id" (click)="cambiar(c, a.e)">
                        @if (a.ic) {<svg [appIcon]="a.ic"></svg>} {{ a.label }}
                      </button>
                    }
                  </div>
                }
              </li>
            }
          </ul>
        } @else {
          <div class="empty">
            <svg appIcon="calendar"></svg>
            <p>{{ vista.calFilter() !== 'todas' ? 'No hay citas con este estado en el día seleccionado.' : 'Este día está libre.' }}</p>
            @if (vista.calFilter() !== 'todas') {
              <button class="btn btn-ghost btn-sm" (click)="vista.calFilter.set('todas')">Ver todas las citas</button>
            }
          </div>
        }
      </aside>
    </div>`,
})
export class DoctorAgenda {
  protected readonly store = inject(AgendaDoctorStore);
  private readonly dialog = inject(DialogService);
  private readonly toast = inject(ToastService);
  protected readonly vista = inject(VistaStateService);
  private readonly dayPanel = viewChild.required<ElementRef<HTMLElement>>('dayPanel');

  protected readonly chips = CHIPS;
  protected readonly diasSemana = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];
  protected readonly hoy = iso(TODAY);
  protected readonly key = estadoKey;
  protected readonly mesDe = mesDe;
  /** id de la cita cuyo cambio está en camino (evita el doble clic) */
  protected readonly ocupada = signal<string | null>(null);

  protected readonly mesVisible = computed(() => mesDe(this.vista.calMonth()));
  private readonly pasa = (c: CitaResponse) => this.vista.calFilter() === 'todas' || c.estado === this.vista.calFilter();

  private readonly monthAppts = computed(() => this.store.citas().filter((c) => c.fechaHora.startsWith(this.mesVisible())));

  protected readonly cells = computed(() => {
    const m = this.vista.calMonth();
    const first = new Date(m.getFullYear(), m.getMonth(), 1);
    const last = new Date(m.getFullYear(), m.getMonth() + 1, 0);
    const end = addDays(startOfWeek(last), 6);
    const out = [];
    for (let d = startOfWeek(first); d <= end; d = addDays(d, 1)) {
      const s = iso(d);
      out.push({ iso: s, date: d, out: d.getMonth() !== m.getMonth(), label: longDate(d), list: this.store.citas().filter((c) => fechaDe(c.fechaHora) === s && this.pasa(c)) });
    }
    return out;
  });

  protected readonly dayList = computed(() => this.store.citas().filter((c) => fechaDe(c.fechaHora) === this.vista.calSel() && this.pasa(c)));

  protected readonly resumenDia = computed(() => {
    if (!this.store.tiene(mesDe(this.vista.calSel()))) return '';
    const n = this.dayList().length;
    const f = this.vista.calFilter();
    if (!n) return 'Sin citas';
    return `${n} ${n === 1 ? 'cita' : 'citas'}${f !== 'todas' ? ` ${ESTADOS[f].toLowerCase()}` : ''}`;
  });

  constructor() {
    // Al entrar se pide de nuevo el mes visible; al navegar, solo los meses que falten
    this.store.refrescar([this.mesVisible(), mesDe(this.vista.calSel())]);
    effect(() => {
      const meses = [this.mesVisible(), mesDe(this.vista.calSel())];
      untracked(() => this.store.asegurar(meses));
    });
  }

  protected chipCount(k: Filtro) {
    return k === 'todas' ? this.monthAppts().length : this.monthAppts().filter((c) => c.estado === k).length;
  }

  protected horaCorta(c: CitaResponse) { return fmtHora(horaDe(c.fechaHora)).replace(' a. m.', '').replace(' p. m.', ''); }

  /**
   * Solo se ofrece lo que el back aceptaría:
   * antes de la hora, confirmar o cancelar; después, atendida o no asistió.
   * Una pendiente que ya pasó solo se puede cancelar.
   */
  protected acciones(c: CitaResponse): Accion[] {
    const empezo = c.fechaHora <= ahora();
    const out: Accion[] = [];
    if (!empezo && puedeTransicionar(c.estado, 'CONFIRMADA')) out.push({ e: 'CONFIRMADA', label: 'Confirmar', cls: 'btn btn-sm btn-primary', ic: 'check' });
    if (empezo && puedeTransicionar(c.estado, 'COMPLETADA')) out.push({ e: 'COMPLETADA', label: 'Atendida', cls: 'btn btn-sm btn-primary', ic: 'check' });
    if (empezo && puedeTransicionar(c.estado, 'NO_ASISTIO')) out.push({ e: 'NO_ASISTIO', label: 'No asistió', cls: 'btn btn-sm btn-ghost', ic: 'ban' });
    if ((!empezo || c.estado === 'PENDIENTE') && puedeTransicionar(c.estado, 'CANCELADA')) out.push({ e: 'CANCELADA', label: 'Cancelar', cls: 'btn btn-sm btn-soft-danger' });
    return out;
  }

  protected async cambiar(c: CitaResponse, e: CambioDoctor) {
    if (e === 'CANCELADA') {
      const ok = await this.dialog.confirm({
        title: `¿Cancelar la cita de ${c.pacienteNombre}?`,
        body: `Era el ${longDate(fechaDe(c.fechaHora)).toLowerCase()} a las ${fmtHora(horaDe(c.fechaHora))}. Le enviaremos un correo al paciente para avisarle.`,
        confirmText: 'Cancelar cita', cancelText: 'Mantener', danger: true, tone: 'cancelada', ic: 'x',
      });
      if (!ok) return;
    }
    this.ocupada.set(c.id);
    try {
      await this.store.cambiar(c, e);
      this.toast.show(AVISOS[e], e === 'CANCELADA' || e === 'NO_ASISTIO' ? 'x' : 'check', e === 'CANCELADA');
    } catch (err) {
      this.toast.show(mensajeDeError(err), 'alert', true);
    } finally {
      this.ocupada.set(null);
    }
  }

  protected reintentar() {
    this.store.refrescar([this.mesVisible(), mesDe(this.vista.calSel())]);
  }

  protected seleccionar(fecha: string) {
    this.vista.calSel.set(fecha);
    if (window.innerWidth <= 1180) this.dayPanel().nativeElement.scrollIntoView({ behavior: 'smooth', block: 'start' });
  }

  protected mover(n: number) {
    const m = this.vista.calMonth();
    this.vista.calMonth.set(new Date(m.getFullYear(), m.getMonth() + n, 1));
  }

  protected irHoy() {
    this.vista.calMonth.set(new Date(TODAY.getFullYear(), TODAY.getMonth(), 1));
    this.vista.calSel.set(this.hoy);
  }
}
