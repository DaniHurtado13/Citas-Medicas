import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, ElementRef, inject, signal, untracked, viewChild } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { DoctoresApi } from '../../core/api/doctores.api';
import { CitaResponse } from '../../core/api/dtos';
import { mensajeDeError } from '../../core/http/errores';
import { DialogService } from '../../core/ui.service';
import { reservaVacia, VistaStateService } from '../../core/vista-state.service';
import { addDays, fechaDe, fechaHora, fmtHora, horaDe, iso, longDate, startOfWeek, TODAY } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { Avatar, Carga } from '../../shared/ui';
import { FechaPipe, HoraPipe, LongDatePipe } from '../../shared/pipes';
import { VENTANA_CAMBIOS_HORAS } from './acciones-cita';
import { CatalogoStore } from './catalogo.store';
import { CitasPacienteStore } from './citas-paciente.store';

const DIAS_VISIBLES = 6; // lunes a sábado

@Component({
  selector: 'app-paciente-agendar',
  imports: [Icon, Avatar, Carga, FechaPipe, HoraPipe, LongDatePipe],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">{{ b().reprog ? 'Reprogramar cita' : 'Agendar cita' }}</h1>
        <p>{{ b().reprog ? 'Elige el nuevo horario; se conservan el doctor y el motivo.' : 'Elige un doctor, revisa sus horarios libres y reserva el que te sirva.' }}</p>
      </div>
    </section>

    @if (reprogCita(); as r) {
      <div class="notice"><svg appIcon="refresh"></svg><span>Estás cambiando tu cita del {{ (r.fechaHora | longDate).toLowerCase() }} a las {{ r.fechaHora | hora }}; elige el nuevo horario.</span><button (click)="descartarReprog()">Descartar</button></div>
    }

    @if (!catalogo.cargado() || errorCatalogo()) {
      <app-carga texto="Cargando doctores…" [error]="errorCatalogo()" (reintentar)="cargarCatalogo()" />
    } @else {
      <div class="book-layout">
        <div class="col">
          <section class="panel">
            <h2 class="panel-title-lg" style="font-family:var(--font-display);font-size:1.22rem;font-weight:500"><span class="step" [class.is-done]="doc()">1</span>{{ b().reprog ? 'Tu doctor' : 'Elige un doctor' }}</h2>
            @if (!b().reprog) {
              <div class="chips" style="margin-top:16px" role="group" aria-label="Especialidad">
                <button class="chip" [class.is-active]="!b().espId" (click)="elegirEsp('')">Todas</button>
                @for (e of esps(); track e.id) {
                  <button class="chip" [class.is-active]="b().espId === e.id" (click)="elegirEsp(e.id)">{{ e.nombre }}</button>
                }
              </div>
            }
            <div class="doc-picks">
              @for (d of visibles(); track d.id) {
                <button class="doc-pick" [class.is-active]="b().docId === d.id" (click)="elegirDoc(d.id)" [attr.aria-pressed]="b().docId === d.id">
                  <app-avatar [name]="d.nombre" />
                  <div><strong>{{ d.nombre }}</strong><span>{{ d.especialidadNombre }}</span></div>
                </button>
              } @empty {
                <div class="empty"><svg appIcon="steth"></svg><p>No hay doctores disponibles en esta especialidad.</p></div>
              }
            </div>
          </section>

          <section class="panel">
            <h2 style="font-family:var(--font-display);font-size:1.22rem;font-weight:500;margin-bottom:16px"><span class="step" [class.is-done]="b().slot">2</span>Elige un horario</h2>
            @if (doc()) {
              <div class="week-nav">
                <button class="icon-btn" (click)="semana(-7)" [disabled]="b().weekStart <= minWeek" aria-label="Semana anterior"><svg appIcon="left"></svg></button>
                <strong>{{ b().weekStart | fecha: { day: 'numeric', month: 'short' } }} al {{ finSemana() | fecha: { day: 'numeric', month: 'short' } }}</strong>
                <button class="icon-btn" (click)="semana(7)" [disabled]="b().weekStart >= maxWeek" aria-label="Semana siguiente"><svg appIcon="right"></svg></button>
              </div>
              <div class="slot-grid" [attr.aria-busy]="libres() === null">
                @for (col of cols(); track col.iso) {
                  <div class="slot-col" [class.is-today]="col.iso === hoy">
                    <header><strong>{{ col.date | fecha: { weekday: 'short' } : true : true }}</strong><span>{{ col.date | fecha: { day: 'numeric', month: 'short' } : false : true }}</span></header>
                    @if (col.past) {
                      <div class="slot-empty">Fecha pasada</div>
                    } @else if (libres() === null) {
                      <div class="slot-empty">Buscando…</div>
                    } @else {
                      @for (h of col.horas; track h) {
                        @let active = b().slot?.fecha === col.iso && b().slot?.hora === h;
                        <button class="slot" [class.is-active]="active" (click)="elegirSlot(col.iso, h)"
                                [attr.aria-pressed]="active" [attr.aria-label]="col.label + ' ' + (h | hora)">{{ corta(h) }}</button>
                      } @empty {
                        <div class="slot-empty">Sin horarios libres</div>
                      }
                    }
                  </div>
                }
              </div>
              <div class="slot-legend"><span><i></i>Libre</span><span><i class="sel"></i>Tu elección</span></div>
            } @else {
              <div class="empty"><svg appIcon="calendar"></svg><p>Selecciona un doctor para ver los horarios disponibles.</p></div>
            }
          </section>
        </div>

        <aside class="panel book-summary">
          <h2><span class="step">3</span>{{ b().reprog ? 'Confirma el cambio' : 'Confirma tu cita' }}</h2>
          <div class="sum-rows">
            <div><span>Doctor</span><b [class.is-empty]="!doc()">{{ doc()?.nombre ?? 'Sin elegir' }}</b></div>
            <div><span>Especialidad</span><b [class.is-empty]="!doc()">{{ doc()?.especialidadNombre ?? 'Sin elegir' }}</b></div>
            <div><span>Fecha</span><b [class.is-empty]="!b().slot">{{ b().slot ? (b().slot!.fecha | longDate) : 'Sin elegir' }}</b></div>
            <div><span>Hora</span><b [class.is-empty]="!b().slot">{{ b().slot ? (b().slot!.hora | hora) : 'Sin elegir' }}</b></div>
            @if (doc(); as d) { <div><span>Lugar</span><b>{{ d.consultorio }}</b></div> }
          </div>
          <div class="field" [class.has-error]="motivoError()">
            <label for="motivo">Motivo de la consulta</label>
            <textarea #motivo id="motivo" maxlength="255" placeholder="Cuéntale al doctor qué te pasa o qué necesitas revisar"
                      [readOnly]="!!b().reprog" [value]="b().motivo" (input)="escribirMotivo($any($event.target).value)"></textarea>
          </div>
          <p class="form-error" role="alert">{{ error() }}</p>
          <button class="btn btn-primary btn-lg btn-block" [class.is-loading]="loading()" [disabled]="!b().slot || loading()" (click)="enviar()">
            <span class="btn-label">{{ b().reprog ? 'Reprogramar cita' : 'Agendar cita' }}</span><span class="spinner"></span>
          </button>
          <p class="muted small">Recibirás un correo cuando el doctor confirme la cita. Una vez confirmada, puedes cancelarla o reprogramarla hasta {{ ventana }} horas antes.</p>
        </aside>
      </div>
    }`,
})
export class PacienteAgendar {
  protected readonly catalogo = inject(CatalogoStore);
  private readonly citas = inject(CitasPacienteStore);
  private readonly doctoresApi = inject(DoctoresApi);
  private readonly dialog = inject(DialogService);
  private readonly vista = inject(VistaStateService);
  private readonly router = inject(Router);
  private readonly motivoEl = viewChild<ElementRef<HTMLTextAreaElement>>('motivo');

  protected readonly b = this.vista.reserva;
  protected readonly hoy = iso(TODAY);
  protected readonly ventana = VENTANA_CAMBIOS_HORAS;
  protected readonly minWeek = startOfWeek(TODAY);
  protected readonly maxWeek = addDays(this.minWeek, 21);
  protected readonly error = signal('');
  protected readonly errorCatalogo = signal('');
  protected readonly motivoError = signal(false);
  protected readonly loading = signal(false);

  /** Horas libres por fecha de la semana visible ('2026-10-19' → ['08:00', '08:30']); null mientras se piden */
  protected readonly libres = signal<Record<string, string[]> | null>(null);
  private pedido = 0;

  protected readonly esps = computed(() => {
    const ids = new Set(this.catalogo.activos().map((d) => d.especialidadId));
    return this.catalogo.especialidades().filter((e) => ids.has(e.id));
  });
  protected readonly doc = computed(() => (this.b().docId ? this.catalogo.doctor(this.b().docId!) ?? null : null));
  protected readonly visibles = computed(() => {
    if (this.b().reprog) return this.doc() ? [this.doc()!] : [];
    return this.catalogo.activos().filter((d) => !this.b().espId || d.especialidadId === this.b().espId);
  });
  protected readonly reprogCita = computed(() => (this.b().reprog ? this.citas.citas().find((c) => c.id === this.b().reprog) ?? null : null));
  protected readonly finSemana = computed(() => addDays(this.b().weekStart, DIAS_VISIBLES - 1));

  // Solo estos dos valores disparan una nueva consulta de disponibilidad (escribir el motivo no)
  private readonly docId = computed(() => this.b().docId);
  private readonly semanaIso = computed(() => iso(this.b().weekStart));

  protected readonly cols = computed(() => {
    if (!this.doc()) return [];
    const libres = this.libres();
    return this.diasDeLaSemana().map((date) => {
      const s = iso(date);
      return { iso: s, date, label: longDate(date), past: s < this.hoy, horas: libres?.[s] ?? [] };
    });
  });

  constructor() {
    this.cargarCatalogo();
    this.citas.cargar();
    effect(() => {
      const docId = this.docId();
      this.semanaIso();
      untracked(() => this.pedirSemana(docId));
    });
  }

  protected cargarCatalogo() {
    this.errorCatalogo.set('');
    this.catalogo.cargar().catch((e) => this.errorCatalogo.set(mensajeDeError(e)));
  }

  protected corta(h: string) { return fmtHora(h).replace(' a. m.', ' am').replace(' p. m.', ' pm'); }

  protected elegirEsp(espId: string) {
    this.b.update((r) => {
      const d = r.docId ? this.catalogo.doctor(r.docId) : null;
      return d && espId && d.especialidadId !== espId ? { ...r, espId, docId: null, slot: null } : { ...r, espId };
    });
  }

  protected elegirDoc(id: string) {
    if (this.b().reprog) return; // al reprogramar el doctor no cambia
    this.b.update((r) => ({ ...r, docId: id, slot: r.docId === id ? r.slot : null }));
  }

  protected semana(n: number) { this.b.update((r) => ({ ...r, weekStart: addDays(r.weekStart, n) })); }
  protected elegirSlot(fecha: string, hora: string) { this.b.update((r) => ({ ...r, slot: { fecha, hora } })); this.error.set(''); }

  protected escribirMotivo(v: string) {
    this.b.update((r) => ({ ...r, motivo: v }));
    this.motivoError.set(false);
    this.error.set('');
  }

  protected descartarReprog() { this.b.set(reservaVacia()); }

  protected async enviar() {
    const r = this.b();
    const doc = this.doc();
    if (!doc || !r.slot) return;
    if (!r.reprog && !r.motivo.trim()) {
      this.error.set('Escribe el motivo de la consulta para que el doctor pueda prepararse.');
      this.motivoError.set(true);
      this.motivoEl()?.nativeElement.focus();
      return;
    }

    this.loading.set(true);
    const fh = fechaHora(r.slot.fecha, r.slot.hora);
    let cita: CitaResponse;
    try {
      cita = r.reprog
        ? await this.citas.reprogramar(r.reprog, fh)
        : await this.citas.reservar(doc.id, fh, r.motivo.trim());
    } catch (e) {
      // 409: el horario se ocupó, o el paciente llegó a su límite de citas. El back explica cuál en `detail`
      this.error.set(mensajeDeError(e));
      if (e instanceof HttpErrorResponse && e.status === 409) {
        this.b.update((x) => ({ ...x, slot: null }));
        this.pedirSemana(doc.id);
      }
      return;
    } finally {
      this.loading.set(false);
    }

    const title = r.reprog ? 'Cita reprogramada' : 'Cita agendada';
    const when = `${longDate(fechaDe(cita.fechaHora)).toLowerCase()} a las ${fmtHora(horaDe(cita.fechaHora))}`;
    this.b.set(reservaVacia(r.reprog ? '' : r.espId));

    const goList = await this.dialog.confirm({
      title,
      body: `Tu cita con ${doc.nombre} quedó para el ${when}. Te avisaremos por correo cuando el doctor la confirme.`,
      confirmText: 'Ver mis citas', cancelText: 'Seguir aquí', ic: 'check', tone: 'confirmada',
    });
    if (goList) {
      this.vista.citasTab.set('proximas');
      this.router.navigateByUrl('/paciente/mis-citas');
    }
  }

  private diasDeLaSemana(): Date[] {
    return Array.from({ length: DIAS_VISIBLES }, (_, i) => addDays(this.b().weekStart, i));
  }

  /** Una petición por día que no haya pasado. Si el usuario cambia de semana antes de que lleguen, se ignoran */
  private async pedirSemana(docId: string | null) {
    const n = ++this.pedido;
    this.libres.set(null);
    if (!docId) return;
    const fechas = this.diasDeLaSemana().map(iso).filter((f) => f >= this.hoy);
    try {
      const respuestas = await Promise.all(
        fechas.map(async (f) => [f, (await firstValueFrom(this.doctoresApi.disponibilidad(docId, f))).map(horaDe)] as const),
      );
      if (n === this.pedido) this.libres.set(Object.fromEntries(respuestas));
    } catch (e) {
      if (n !== this.pedido) return;
      this.libres.set({});
      this.error.set(mensajeDeError(e));
    }
  }
}
