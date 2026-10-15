import { Component, computed, input, output } from '@angular/core';
import { ESTADOS, EstadoCita, estadoKey } from '../core/models';
import { Icon } from './icons';

export const initials = (n: string) =>
  n.split(/[\s.@]+/).filter(Boolean).slice(0, 2).map((p) => p[0]).join('').toUpperCase();
export const toneOf = (name: string) => 1 + (name.length % 4);

/* ---------- Avatar con iniciales ---------- */
@Component({
  selector: 'app-avatar',
  template: '{{ ini() }}',
  host: { class: 'avatar', '[attr.data-tone]': 'tone()' },
})
export class Avatar {
  readonly name = input.required<string>();
  // Para correos solo cuenta la parte antes de la @
  protected readonly base = computed(() => this.name().split('@')[0]);
  protected readonly ini = computed(() => initials(this.base()));
  protected readonly tone = computed(() => toneOf(this.name()));
}

/* ---------- Etiqueta de estado de cita ---------- */
@Component({
  selector: 'app-badge',
  template: '{{ label() }}',
  host: { class: 'badge', '[attr.data-s]': 'key()' },
})
export class Badge {
  readonly estado = input.required<EstadoCita>();
  protected readonly key = computed(() => estadoKey(this.estado()));
  protected readonly label = computed(() => ESTADOS[this.estado()]);
}

/* ---------- Franja de estadísticas ---------- */
export interface Stat { label: string; value: string | number; small?: string; note?: string; up?: boolean; }

@Component({
  selector: 'app-stat-strip',
  template: `
    @for (s of items(); track s.label) {
      <div class="stat">
        <div class="stat-label">{{ s.label }}</div>
        <div class="stat-value">{{ s.value }}@if (s.small) {<small>{{ s.small }}</small>}</div>
        @if (s.note) { <div class="stat-note" [class.up]="s.up">{{ s.note }}</div> }
      </div>
    }`,
  host: { class: 'stats' },
})
export class StatStrip {
  readonly items = input.required<Stat[]>();
}

/* ---------- Gráfica de barras verticales ---------- */
export interface BarDatum { l: string; v: number; }

@Component({
  selector: 'app-bar-chart',
  template: `
    @for (d of data(); track $index; let i = $index) {
      <div class="bar-col">
        <div class="bar" [class.is-hl]="i === hl()" [style.--h]="(d.v / max()) * 100 + '%'" [style.--i]="i"
             [attr.data-v]="d.v" [title]="d.l + ': ' + d.v"></div>
        <span class="bar-label">{{ d.l }}</span>
      </div>
    }`,
  host: { class: 'bars' },
})
export class BarChart {
  readonly data = input.required<BarDatum[]>();
  readonly hl = input(-1);
  protected readonly max = computed(() => Math.max(1, ...this.data().map((d) => d.v)));
}

/* ---------- Barras horizontales ---------- */
/** [etiqueta, valor, color opcional de la barra] */
export type HBarRow = [string, number, string?];

@Component({
  selector: 'app-hbars',
  template: `
    @for (r of rows(); track r[0]; let i = $index) {
      <div class="hbar-row">
        <span [title]="r[0]">{{ r[0] }}</span>
        <div class="hbar-track"><div class="hbar-fill" [style.--w]="(r[1] / max()) * 100 + '%'" [style.--i]="i" [style.--bar]="r[2] ?? null"></div></div>
        <b>{{ r[1] }}{{ suffix() }}</b>
      </div>
    }`,
  host: { class: 'hbars' },
})
export class HBars {
  readonly rows = input.required<HBarRow[]>();
  readonly suffix = input('');
  protected readonly max = computed(() => Math.max(1, ...this.rows().map((r) => r[1])));
}

/* ---------- Carga desde la API: "Cargando…" o el error con un botón para reintentar ---------- */
@Component({
  selector: 'app-carga',
  imports: [Icon],
  template: `
    <section class="panel">
      <div class="empty" [attr.role]="error() ? 'alert' : 'status'">
        @if (error()) {
          <svg appIcon="alert"></svg>
          <p>{{ error() }}</p>
          <button class="btn btn-ghost btn-sm" (click)="reintentar.emit()"><svg appIcon="refresh"></svg> Reintentar</button>
        } @else {
          <svg appIcon="clock"></svg>
          <p>{{ texto() }}</p>
        }
      </div>
    </section>`,
})
export class Carga {
  readonly texto = input('Cargando…');
  readonly error = input('');
  readonly reintentar = output<void>();
}
