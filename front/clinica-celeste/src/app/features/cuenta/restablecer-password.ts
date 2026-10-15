import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { mensajeDeError } from '../../core/http/errores';
import { ToastService } from '../../core/ui.service';
import { errorPassword, PASSWORD_MAX } from '../../core/validacion';

/** Destino del enlace del correo de recuperación: /restablecer-password?token=… */
@Component({
  selector: 'app-restablecer-password',
  imports: [RouterLink],
  template: `
    <form class="login-form" novalidate (submit)="guardar($event)">
      <h1>Crea una contraseña nueva</h1>
      <p class="lead">Al guardarla se cerrarán las sesiones abiertas y entrarás con la nueva.</p>
      @if (!token) {
        <p class="form-error" role="alert">El enlace está incompleto. Ábrelo de nuevo desde el correo.</p>
      } @else {
        <div class="field" [class.has-error]="campo() === 'nueva'">
          <label for="n-pass">Contraseña nueva</label>
          <input id="n-pass" type="password" autocomplete="new-password" [attr.maxlength]="passwordMax" placeholder="Mínimo 8 caracteres" [value]="nueva()" (input)="nueva.set($any($event.target).value)">
        </div>
        <div class="field" [class.has-error]="campo() === 'confirmar'">
          <label for="n-conf">Repítela</label>
          <input id="n-conf" type="password" autocomplete="new-password" [attr.maxlength]="passwordMax" [value]="confirmar()" (input)="confirmar.set($any($event.target).value)">
        </div>
        <p class="form-error" role="alert">{{ error() }}</p>
        @if (vencido()) {
          <a routerLink="/olvide-password" class="btn btn-ghost btn-block">Pedir un enlace nuevo</a>
        }
        <button type="submit" class="btn btn-primary btn-block btn-lg" [class.is-loading]="guardando()" [disabled]="guardando()">
          <span class="btn-label">Guardar contraseña</span><span class="spinner" aria-hidden="true"></span>
        </button>
      }
      <p class="login-foot"><a routerLink="/login" class="link">Volver a iniciar sesión</a></p>
    </form>`,
  host: { style: 'display: contents' },
})
export class RestablecerPassword {
  private readonly api = inject(AuthApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
  protected readonly passwordMax = PASSWORD_MAX;
  protected readonly nueva = signal('');
  protected readonly confirmar = signal('');
  protected readonly campo = signal<'nueva' | 'confirmar' | null>(null);
  protected readonly error = signal('');
  protected readonly vencido = signal(false);
  protected readonly guardando = signal(false);

  protected async guardar(ev: Event) {
    ev.preventDefault();
    const problema = errorPassword(this.nueva());
    if (problema) return this.falla('nueva', problema);
    if (this.confirmar() !== this.nueva()) return this.falla('confirmar', 'Las contraseñas no coinciden.');
    this.campo.set(null);
    this.error.set('');

    this.guardando.set(true);
    try {
      await firstValueFrom(this.api.restablecerPassword(this.token!, this.nueva()));
      await this.router.navigateByUrl('/login');
      this.toast.show('Listo, tu contraseña cambió. Inicia sesión con la nueva.');
    } catch (e) {
      this.vencido.set(e instanceof HttpErrorResponse && e.status === 400);
      this.falla(null, mensajeDeError(e, { 400: 'El enlace venció o ya se usó. Pide uno nuevo.' }));
    } finally {
      this.guardando.set(false);
    }
  }

  private falla(campo: 'nueva' | 'confirmar' | null, msg: string) {
    this.campo.set(campo);
    this.error.set(msg);
  }
}
