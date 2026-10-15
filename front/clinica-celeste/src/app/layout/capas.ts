import { afterRenderEffect, Component, computed, ElementRef, inject, viewChild } from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import { BootService, DialogService, ToastService } from '../core/ui.service';
import { Icon } from '../shared/icons';

/* ---------- Modal de confirmación y cajón lateral ---------- */
@Component({
  selector: 'app-overlay',
  imports: [Icon, NgComponentOutlet],
  template: `
    @if (dialog.capa(); as c) {
      @if (c.kind === 'confirm') {
        <div class="overlay" [class.is-closing]="dialog.closing()" (click)="cerrarSiFondo($event)">
          <div class="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
            <div class="modal-icon" [attr.data-s]="c.opts.tone ?? 'confirmada'"><svg [appIcon]="c.opts.ic ?? 'alert'"></svg></div>
            <h3 id="modal-title">{{ c.opts.title }}</h3>
            <p>{{ c.opts.body }}</p>
            <div class="modal-actions">
              @if (!c.opts.onlyOk) {
                <button class="btn btn-ghost" (click)="dialog.close(false)">{{ c.opts.cancelText ?? 'Volver' }}</button>
              }
              <button #ok class="btn" [class.btn-danger]="c.opts.danger" [class.btn-primary]="!c.opts.danger" (click)="dialog.close(true)">
                {{ c.opts.confirmText ?? 'Confirmar' }}
              </button>
            </div>
          </div>
        </div>
      } @else {
        <div class="overlay drawer-overlay" [class.is-closing]="dialog.closing()" (click)="cerrarSiFondo($event)">
          <aside class="drawer" role="dialog" aria-modal="true">
            <ng-container *ngComponentOutlet="c.component; inputs: c.inputs" />
          </aside>
        </div>
      }
    }`,
  host: { '(document:keydown.escape)': 'dialog.close(false)' },
})
export class Overlay {
  protected readonly dialog = inject(DialogService);
  private readonly ok = viewChild<ElementRef<HTMLButtonElement>>('ok');

  /**
   * Cierra solo si el clic fue en el fondo oscuro. No devuelve nada a propósito: si un manejador
   * de evento devuelve false, Angular cancela la acción del navegador (y los formularios no se enviarían)
   */
  protected cerrarSiFondo(e: MouseEvent): void {
    if (e.target === e.currentTarget) this.dialog.close(false);
  }

  constructor() {
    // Lleva el foco al botón principal cuando se abre el modal
    afterRenderEffect(() => {
      const b = this.ok();
      if (b) setTimeout(() => b.nativeElement.focus(), 50);
    });
  }
}

/* ---------- Avisos flotantes ---------- */
@Component({
  selector: 'app-toasts',
  imports: [Icon],
  template: `
    @for (t of toast.toasts(); track t.id) {
      <div class="toast" [class.is-danger]="t.danger" [class.is-leaving]="t.leaving" (animationend)="t.leaving && toast.remove(t.id)">
        <svg [appIcon]="t.ic"></svg><span>{{ t.msg }}</span>
      </div>
    }`,
  host: { class: 'toasts', 'aria-live': 'polite' },
})
export class Toasts {
  protected readonly toast = inject(ToastService);
}

/* ---------- Pantalla de carga al iniciar sesión ---------- */
@Component({
  selector: 'app-boot',
  template: `
    @if (boot.visible()) {
      <div class="boot" [class.is-leaving]="boot.leaving()">
        <svg class="boot-sprig" viewBox="0 0 64 64" aria-hidden="true">
          <path class="b-stem" d="M32 60 C 31 46, 34 34, 32 8"/>
          <path class="b-leaf" style="--d:0s"   d="M32 46 C 22 44, 14 38, 12 30 C 22 30, 30 36, 32 46 Z"/>
          <path class="b-leaf" style="--d:.15s" d="M32 38 C 42 36, 50 30, 52 22 C 42 22, 34 28, 32 38 Z"/>
          <path class="b-leaf" style="--d:.3s"  d="M32 26 C 24 24, 18 18, 17 11 C 25 12, 31 18, 32 26 Z"/>
          <path class="b-leaf" style="--d:.45s" d="M32 18 C 38 16, 43 11, 44 5 C 38 6, 33 11, 32 18 Z"/>
        </svg>
        <p>{{ boot.text() }}</p>
        <div class="boot-bar"><span></span></div>
      </div>
    }`,
})
export class Boot {
  protected readonly boot = inject(BootService);
}
