import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthApi } from '../../core/api/auth.api';
import { erroresDeCampos, mensajeDeError } from '../../core/http/errores';
import { TipoDocumento, TIPOS_DOCUMENTO } from '../../core/models';
import { addDays, iso, TODAY } from '../../core/fechas';
import { EMAIL_RE, errorPassword, PASSWORD_MAX } from '../../core/validacion';

type Campo = 'nombre' | 'tipoDocumento' | 'numeroDocumento' | 'email' | 'telefono' | 'fechaNacimiento' | 'password' | 'confirmar';

/**
 * Registro de pacientes (los doctores los crea el admin). La cuenta nace sin confirmar:
 * el back envía un correo y hasta abrir el enlace no se puede iniciar sesión.
 */
@Component({
  selector: 'app-registro',
  imports: [RouterLink],
  template: `
    @if (!enviadoA()) {
      <form class="login-form" novalidate (submit)="registrar($event)">
        <h1>Crea tu cuenta</h1>

        <div class="form-grid">
          <div class="field span-2" [class.has-error]="err('nombre')">
            <label for="r-nombre">Nombre completo</label>
            <input id="r-nombre" autocomplete="name" maxlength="255" [value]="nombre()" (input)="nombre.set($any($event.target).value); limpiar('nombre')">
            <small class="field-msg">{{ err('nombre') }}</small>
          </div>
          <div class="field" [class.has-error]="err('tipoDocumento')">
            <label for="r-tipo">Documento</label>
            <select id="r-tipo" (change)="tipoDocumento.set($any($event.target).value); limpiar('tipoDocumento')">
              @for (t of tipos; track t) { <option [value]="t" [selected]="tipoDocumento() === t">{{ t }}</option> }
            </select>
          </div>
          <div class="field" [class.has-error]="err('numeroDocumento')">
            <label for="r-doc">Número</label>
            <input id="r-doc" inputmode="numeric" maxlength="255" [value]="numeroDocumento()" (input)="numeroDocumento.set($any($event.target).value); limpiar('numeroDocumento')">
            <small class="field-msg">{{ err('numeroDocumento') }}</small>
          </div>
          <div class="field span-2" [class.has-error]="err('email')">
            <label for="r-email">Correo</label>
            <input id="r-email" type="email" autocomplete="email" maxlength="255" placeholder="nombre@correo.co" [value]="email()" (input)="email.set($any($event.target).value); limpiar('email')">
            <small class="field-msg">{{ err('email') }}</small>
          </div>
          <div class="field" [class.has-error]="err('telefono')">
            <label for="r-tel">Celular</label>
            <input id="r-tel" type="tel" inputmode="numeric" autocomplete="tel" maxlength="10" placeholder="3001234567" [value]="telefono()" (input)="telefono.set($any($event.target).value); limpiar('telefono')">
            <small class="field-msg">{{ err('telefono') }}</small>
          </div>
          <div class="field" [class.has-error]="err('fechaNacimiento')">
            <label for="r-nac">Fecha de nacimiento</label>
            <input id="r-nac" type="date" [max]="ayer" [value]="fechaNacimiento()" (input)="fechaNacimiento.set($any($event.target).value); limpiar('fechaNacimiento')">
            <small class="field-msg">{{ err('fechaNacimiento') }}</small>
          </div>
          <div class="field" [class.has-error]="err('password')">
            <label for="r-pass">Contraseña</label>
            <input id="r-pass" type="password" autocomplete="new-password" [attr.maxlength]="passwordMax" placeholder="Mínimo 8 caracteres" [value]="password()" (input)="password.set($any($event.target).value); limpiar('password')">
            <small class="field-msg">{{ err('password') }}</small>
          </div>
          <div class="field" [class.has-error]="err('confirmar')">
            <label for="r-conf">Repite la contraseña</label>
            <input id="r-conf" type="password" autocomplete="new-password" [attr.maxlength]="passwordMax" [value]="confirmar()" (input)="confirmar.set($any($event.target).value); limpiar('confirmar')">
            <small class="field-msg">{{ err('confirmar') }}</small>
          </div>
        </div>

        <p class="form-error" role="alert">{{ error() }}</p>
        <button type="submit" class="btn btn-primary btn-block btn-lg" [class.is-loading]="enviando()" [disabled]="enviando()">
          <span class="btn-label">Crear cuenta</span><span class="spinner" aria-hidden="true"></span>
        </button>
        <p class="login-foot">¿Ya tienes cuenta? <a routerLink="/login" class="link">Inicia sesión</a></p>
      </form>
    } @else {
      <div class="login-form" role="status">
        <h1>Revisa tu correo</h1>
        <p class="lead">Te enviamos un enlace a <b>{{ enviadoA() }}</b> para confirmar tu cuenta. Vence en 24 horas.</p>
        <p class="muted">Cuando lo abras podrás iniciar sesión. Si no lo ves, busca en la carpeta de spam.</p>
        <a routerLink="/login" class="btn btn-primary btn-block btn-lg">Ir a iniciar sesión</a>
      </div>
    }`,
  host: { style: 'display: contents' },
})
export class Registro {
  private readonly api = inject(AuthApi);

  protected readonly tipos = TIPOS_DOCUMENTO;
  protected readonly ayer = iso(addDays(TODAY, -1));
  protected readonly passwordMax = PASSWORD_MAX;

  protected readonly nombre = signal('');
  protected readonly tipoDocumento = signal<TipoDocumento>('CC');
  protected readonly numeroDocumento = signal('');
  protected readonly email = signal('');
  protected readonly telefono = signal('');
  protected readonly fechaNacimiento = signal('');
  protected readonly password = signal('');
  protected readonly confirmar = signal('');

  protected readonly errores = signal<Partial<Record<Campo, string>>>({});
  protected readonly error = signal('');
  protected readonly enviando = signal(false);
  /** Correo al que se envió la confirmación; mientras sea null se muestra el formulario */
  protected readonly enviadoA = signal<string | null>(null);

  protected err(c: Campo) { return this.errores()[c] ?? ''; }

  /** Al escribir en un campo se quita su error: ya no aplica */
  protected limpiar(c: Campo) {
    if (this.errores()[c]) this.errores.update(({ [c]: _, ...resto }) => resto);
  }

  private validar(): Partial<Record<Campo, string>> {
    const e: Partial<Record<Campo, string>> = {};
    if (!this.nombre().trim()) e.nombre = 'Escribe tu nombre.';
    if (!this.numeroDocumento().trim()) e.numeroDocumento = 'Escribe tu número de documento.';
    if (!EMAIL_RE.test(this.email().trim())) e.email = 'Escribe un correo válido.';
    if (!/^\d{10}$/.test(this.telefono())) e.telefono = 'Escribe los 10 dígitos, sin espacios.';
    if (!this.fechaNacimiento()) e.fechaNacimiento = 'Elige tu fecha de nacimiento.';
    else if (this.fechaNacimiento() > this.ayer) e.fechaNacimiento = 'Debe ser una fecha pasada.';
    const p = errorPassword(this.password());
    if (p) e.password = p;
    else if (this.confirmar() !== this.password()) e.confirmar = 'Las contraseñas no coinciden.';
    return e;
  }

  protected async registrar(ev: Event) {
    ev.preventDefault();
    const errores = this.validar();
    this.errores.set(errores);
    if (Object.keys(errores).length) {
      this.error.set('Revisa los campos marcados.');
      return;
    }
    this.error.set('');
    const email = this.email().trim();

    this.enviando.set(true);
    try {
      await firstValueFrom(this.api.registro({
        nombre: this.nombre().trim(),
        tipoDocumento: this.tipoDocumento(),
        numeroDocumento: this.numeroDocumento().trim(),
        email,
        password: this.password(),
        telefono: this.telefono(),
        fechaNacimiento: this.fechaNacimiento(),
      }));
      this.enviadoA.set(email);
    } catch (e) {
      // 400 por campo, 409 (correo o documento ya registrados), 429 (demasiados registros desde esta red)
      this.errores.set(erroresDeCampos(e) as Partial<Record<Campo, string>>);
      this.error.set(mensajeDeError(e));
    } finally {
      this.enviando.set(false);
    }
  }
}
