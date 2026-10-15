import { Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { CitaResponse } from '../../core/api/dtos';
import { VistaStateService } from '../../core/vista-state.service';
import { ahora, parseISO } from '../../core/fechas';
import { Icon } from '../../shared/icons';
import { Badge, Carga } from '../../shared/ui';
import { FechaPipe, HoraPipe } from '../../shared/pipes';
import { AccionesCita } from './acciones-cita';
import { CatalogoStore } from './catalogo.store';
import { CitasPacienteStore } from './citas-paciente.store';

@Component({
  selector: 'app-paciente-mis-citas',
  imports: [RouterLink, Icon, Badge, Carga, FechaPipe, HoraPipe],
  template: `
    <section class="page-head">
      <div>
        <h1 class="page-title">Mis citas</h1>
        <p>Revisa tus próximas consultas, reprográmalas o consulta tu historial.</p>
      </div>
      <a class="btn btn-primary" routerLink="/paciente/agendar"><svg appIcon="plus"></svg> Agendar cita</a>
    </section>

    @if (!store.cargado() || store.error()) {
      <app-carga texto="Cargando tus citas…" [error]="store.error()" (reintentar)="store.cargar()" />
    } @else {
      <div class="tabs" role="tablist">
        <button class="tab" [class.is-active]="tab() === 'proximas'" role="tab" [attr.aria-selected]="tab() === 'proximas'" (click)="tab.set('proximas')">Próximas <em>{{ up().length }}</em></button>
        <button class="tab" [class.is-active]="tab() === 'historial'" role="tab" [attr.aria-selected]="tab() === 'historial'" (click)="tab.set('historial')">Historial <em>{{ past().length }}</em></button>
      </div>

      @if (list().length) {
        <ul class="visit-list">
          @for (c of list(); track c.id; let i = $index) {
            <li class="visit" [style.animation-delay.ms]="i * 45">
              <div class="date-block"><b>{{ dia(c) }}</b><span>{{ c.fechaHora | fecha: { month: 'short' } : true : true }}</span></div>
              <div class="visit-main">
                <strong>{{ c.doctorNombre }}</strong>
                <p>{{ c.motivoConsulta }}</p>
                <div class="visit-meta">
                  <span><svg appIcon="clock"></svg>{{ c.fechaHora | fecha: { weekday: 'long' } : true }}, {{ c.fechaHora | hora }}</span>
                  @if (catalogo.doctor(c.doctorId); as d) {
                    <span><svg appIcon="pin"></svg>{{ d.consultorio }}</span>
                    <span><svg appIcon="steth"></svg>{{ d.especialidadNombre }}</span>
                  }
                </div>
              </div>
              <div class="visit-side">
                <app-badge [estado]="c.estado" />
                @if (tab() === 'proximas') {
                  <div class="actions">
                    <button class="btn btn-ghost btn-sm" [disabled]="!acc.puedeReprogramar(c)" [title]="acc.puedeReprogramar(c) ? '' : acc.motivoBloqueo(c, 'reprogramar')" (click)="acc.reprogramar(c)">Reprogramar</button>
                    <button class="btn btn-soft-danger btn-sm" [disabled]="!acc.puedeCancelar(c)" [title]="acc.puedeCancelar(c) ? '' : acc.motivoBloqueo(c, 'cancelar')" (click)="acc.cancelar(c)">Cancelar</button>
                  </div>
                }
              </div>
            </li>
          }
        </ul>
      } @else {
        <section class="panel">
          <div class="empty">
            <svg appIcon="calendar"></svg>
            <p>{{ tab() === 'proximas' ? 'No tienes citas próximas.' : 'Aún no tienes historial de citas.' }}</p>
            @if (tab() === 'proximas') { <a class="btn btn-primary btn-sm" routerLink="/paciente/agendar">Agendar una cita</a> }
          </div>
        </section>
      }
    }`,
})
export class PacienteMisCitas {
  protected readonly store = inject(CitasPacienteStore);
  protected readonly catalogo = inject(CatalogoStore);
  protected readonly acc = inject(AccionesCita);
  protected readonly tab = inject(VistaStateService).citasTab;

  // Una cita reprogramada queda cancelada y enlazada a la nueva (citaOrigenId), por eso aparece en el historial
  protected readonly up = computed(() => {
    const now = ahora();
    return this.store.citas().filter((c) => c.fechaHora >= now && c.estado !== 'CANCELADA');
  });
  protected readonly past = computed(() => {
    const now = ahora();
    return this.store.citas().filter((c) => c.fechaHora < now || c.estado === 'CANCELADA').reverse();
  });
  protected readonly list = computed(() => (this.tab() === 'proximas' ? this.up() : this.past()));

  constructor() {
    this.store.cargar();
  }

  protected dia(c: CitaResponse) { return parseISO(c.fechaHora).getDate(); }
}
