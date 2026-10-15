import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { mensajeDeError } from '../../core/http/errores';
import { EMAIL_RE } from '../../core/validacion';

/**
 * Pide el enlace para restablecer la contraseña. El back responde igual exista o no la cuenta,
 * así que el mensaje de éxito tampoco lo revela.
 */
@Component({
  selector: 'app-olvide-password',
  imports: [RouterLink],
  template: `
    @if (!enviado()) {
      <form class="login-form" novalidate (submit)="enviar($event)">
        <h1>Recupera tu contraseña</h1>
        <p class="lead">Escribe el correo de tu cuenta y te enviaremos un enlace para crear una contraseña nueva.</p>
        <div class="field" [class.has-error]="error()">
          <label for="o-email">Correo</label>
          <input id="o-email" type="email" autocomplete="email" placeholder="nombre@correo.co" [value]="email()" (input)="email.set($any($event.target).value)">
        </div>
        <p class="form-error" role="alert">{{ error() }}</p>
        <button type="submit" class="btn btn-primary btn-block btn-lg" [class.is-loading]="enviando()" [disabled]="enviando()">
          <span class="btn-label">Enviar enlace</span><span class="spinner" aria-hidden="true"></span>
        </button>
        <p class="login-foot"><a routerLink="/login" class="link">Volver a iniciar sesión</a></p>
      </form>
    } @else {
      <div class="login-form" role="status">
        <h1>Revisa tu correo</h1>
        <p class="lead">Si existe una cuenta con <b>{{ email().trim() }}</b>, te llegará un enlace en unos minutos. Vence en 30 minutos.</p>
        <p class="muted">Al cambiar la contraseña se cerrarán las sesiones abiertas en otros dispositivos.</p>
        <a routerLink="/login" class="btn btn-ghost btn-block">Volver a iniciar sesión</a>
      </div>
    }`,
  host: { style: 'display: contents' },
})
export class OlvidePassword {
  private readonly api = inject(AuthApi);

  protected readonly email = signal('');
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  protected readonly enviado = signal(false);

  protected async enviar(ev: Event) {
    ev.preventDefault();
    const email = this.email().trim();
    if (!EMAIL_RE.test(email)) {
      this.error.set('Escribe un correo válido, por ejemplo nombre@correo.co.');
      return;
    }
    this.error.set('');
    this.enviando.set(true);
    try {
      await firstValueFrom(this.api.olvidePassword(email));
      this.enviado.set(true);
    } catch (e) {
      this.error.set(mensajeDeError(e)); // 429: demasiadas solicitudes desde esta red
    } finally {
      this.enviando.set(false);
    }
  }
}
