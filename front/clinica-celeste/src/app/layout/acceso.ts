import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Icon } from '../shared/icons';

/**
 * Marco de las páginas sin sesión (login, registro, confirmar correo, recuperar contraseña):
 * panel de la marca a la izquierda y el formulario de cada página a la derecha.
 * Es el equivalente de shell.ts para quien todavía no ha entrado.
 */
@Component({
  selector: 'app-acceso',
  imports: [RouterOutlet, Icon],
  template: `
    <div class="login-art" aria-hidden="true">
      <div class="brand brand-light">
        <svg appIcon="logo"></svg>
        <span>Clínica Celeste</span>
      </div>

      <div class="art-copy">
        <h2>Consultas a tiempo, sin filas ni llamadas.</h2>
        <p>Pacientes, doctores y administración comparten la misma agenda, siempre al día.</p>
      </div>
    </div>

    <div class="login-side">
      <div class="login-top">
        <div class="brand brand-mobile">
          <svg appIcon="logo"></svg>
          <span>Clínica Celeste</span>
        </div>
      </div>
      <router-outlet />
    </div>`,
  host: { class: 'login' },
})
export class Acceso {}
