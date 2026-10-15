import { Injectable, signal, Type } from '@angular/core';
import { IconName } from '../shared/icons';
import { wait } from './fechas';

/* ==========================================================
   Toasts
   ========================================================== */
export interface Toast { id: number; msg: string; ic: IconName; danger: boolean; leaving: boolean; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  /** `danger` pinta el aviso en rojo (p. ej. "Cita cancelada") */
  show(msg: string, ic: IconName = 'check', danger = false) {
    const id = ++this.seq;
    this.toasts.update((t) => [...t, { id, msg, ic, danger, leaving: false }]);
    setTimeout(() => this.toasts.update((t) => t.map((x) => (x.id === id ? { ...x, leaving: true } : x))), 3200);
  }

  remove(id: number) { this.toasts.update((t) => t.filter((x) => x.id !== id)); }
}

/* ==========================================================
   Capa superior: modal de confirmación y cajón lateral
   ========================================================== */
export interface ConfirmOptions {
  title: string;
  body: string;
  confirmText?: string;
  cancelText?: string;
  tone?: string;
  ic?: IconName;
  danger?: boolean;
  onlyOk?: boolean;
}

export type Capa =
  | { kind: 'confirm'; opts: ConfirmOptions; resolve: (v: boolean) => void }
  | { kind: 'drawer'; component: Type<unknown>; inputs: Record<string, unknown> };

@Injectable({ providedIn: 'root' })
export class DialogService {
  readonly capa = signal<Capa | null>(null);
  readonly closing = signal(false);

  /** Devuelve una promesa: true si el usuario confirma */
  confirm(opts: ConfirmOptions): Promise<boolean> {
    return new Promise((resolve) => {
      this.closing.set(false);
      this.capa.set({ kind: 'confirm', opts, resolve });
    });
  }

  openDrawer(component: Type<unknown>, inputs: Record<string, unknown>) {
    this.closing.set(false);
    this.capa.set({ kind: 'drawer', component, inputs });
  }

  close(result = false) {
    const c = this.capa();
    if (!c || this.closing()) return;
    if (c.kind === 'confirm') c.resolve(result);
    this.closing.set(true);
    setTimeout(() => {
      if (this.capa() === c) this.capa.set(null);
      this.closing.set(false);
    }, 240);
  }
}

/* ==========================================================
   Pantalla de carga al iniciar sesión
   ========================================================== */
@Injectable({ providedIn: 'root' })
export class BootService {
  readonly visible = signal(false);
  readonly leaving = signal(false);
  readonly text = signal('');

  show(text: string) {
    this.text.set(text);
    this.leaving.set(false);
    this.visible.set(true);
  }

  async hide() {
    this.leaving.set(true);
    await wait(340);
    this.visible.set(false);
    this.leaving.set(false);
  }
}
