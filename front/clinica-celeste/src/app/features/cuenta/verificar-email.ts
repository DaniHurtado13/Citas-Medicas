import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { mensajeDeError } from '../../core/http/errores';
import { EMAIL_RE } from '../../core/validacion';

type Estado = 'verificando' | 'listo' | 'fallo';

/**
 * Destino del enlace del correo: /verificar-email?token=…
 * Al abrirla se hace el POST con el token. Si venció o ya se usó, se ofrece pedir uno nuevo.
 */
@Component({
  selector: 'app-verificar-email',
  imports: [RouterLink],
  template: `
    <div class="login-form" [attr.role]="estado() === 'fallo' ? 'alert' : 'status'">
      @switch (estado()) {
        @case ('verificando') {
          <h1>Confirmando tu correo…</h1>
          <p class="lead">Un momento.</p>
        }
        @case ('listo') {
          <h1>Correo confirmado</h1>
          <p class="lead">Tu cuenta ya está activa. Inicia sesión para agendar tu primera cita.</p>
          <a routerLink="/login" class="btn btn-primary btn-block btn-lg">Iniciar sesión</a>
        }
        @case ('fallo') {
          <h1>No pudimos confirmar tu correo</h1>
          <p class="lead">{{ motivo() }}</p>
          @if (!reenviado()) {
            <form class="field" novalidate (submit)="reenviar($event)">
              <label for="v-email">Escribe tu correo y te enviamos un enlace nuevo</label>
              <input id="v-email" type="email" autocomplete="email" placeholder="nombre@correo.co" [value]="email()" (input)="email.set($any($event.target).value)">
              <p class="form-error">{{ error() }}</p>
              <button type="submit" class="btn btn-primary btn-block" [class.is-loading]="enviando()" [disabled]="enviando()">
                <span class="btn-label">Enviar enlace nuevo</span><span class="spinner" aria-hidden="true"></span>
              </button>
            </form>
          } @else {
            <p>Si la cuenta existe y aún no está confirmada, te llegará un correo nuevo en unos minutos.</p>
          }
          <p class="login-foot"><a routerLink="/login" class="link">Volver a iniciar sesión</a></p>
        }
      }
    </div>`,
  host: { style: 'display: contents' },
})
export class VerificarEmail {
  private readonly api = inject(AuthApi);

  protected readonly estado = signal<Estado>('verificando');
  protected readonly motivo = signal('');
  protected readonly email = signal('');
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly reenviado = signal(false);

  constructor() {
    const token = inject(ActivatedRoute).snapshot.queryParamMap.get('token');
    this.verificar(token);
  }

  private async verificar(token: string | null) {
    if (!token) {
      this.fallo('El enlace está incompleto. Ábrelo de nuevo desde el correo.');
      return;
    }
    try {
      await firstValueFrom(this.api.verificarEmail(token));
      this.estado.set('listo');
    } catch (e) {
      this.fallo(mensajeDeError(e, { 400: 'El enlace venció o ya se usó.' }));
    }
  }

  private fallo(motivo: string) {
    this.motivo.set(motivo);
    this.estado.set('fallo');
  }

  protected async reenviar(ev: Event) {
    ev.preventDefault();
    const email = this.email().trim();
    if (!EMAIL_RE.test(email)) {
      this.error.set('Escribe un correo válido.');
      return;
    }
    this.error.set('');
    this.enviando.set(true);
    try {
      await firstValueFrom(this.api.reenviarVerificacion(email));
      this.reenviado.set(true);
    } catch (e) {
      this.error.set(mensajeDeError(e));
    } finally {
      this.enviando.set(false);
    }
  }
}
