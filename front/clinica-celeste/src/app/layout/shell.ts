import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRouteSnapshot, NavigationCancel, NavigationEnd, NavigationError, NavigationStart, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from '../core/auth/auth.service';
import { ToastService } from '../core/ui.service';
import { ahora, longDate, mesDe, TODAY } from '../core/fechas';
import { Rol } from '../core/models';
import { Icon, IconName } from '../shared/icons';
import { Avatar, initials, toneOf } from '../shared/ui';
import { CitasPacienteStore } from '../features/paciente/citas-paciente.store';
import { AgendaDoctorStore } from '../features/doctor/agenda-doctor.store';

interface NavItem { path: string; label: string; ic: IconName; count?: boolean; }

const NAV: Record<Rol, NavItem[]> = {
  DOCTOR: [
    { path: '/doctor/resumen', label: 'Resumen', ic: 'chart' },
    { path: '/doctor/agenda', label: 'Agenda', ic: 'calendar', count: true },
    { path: '/doctor/horario', label: 'Mi horario', ic: 'clock' },
    { path: '/perfil', label: 'Mi perfil', ic: 'user' },
  ],
  ADMIN: [
    { path: '/admin/resumen', label: 'Resumen', ic: 'chart' },
    { path: '/admin/doctores', label: 'Doctores', ic: 'steth' },
    { path: '/admin/especialidades', label: 'Especialidades', ic: 'clipboard' },
    { path: '/perfil', label: 'Mi perfil', ic: 'user' },
  ],
  PACIENTE: [
    { path: '/paciente/inicio', label: 'Inicio', ic: 'home' },
    { path: '/paciente/agendar', label: 'Agendar cita', ic: 'plus' },
    { path: '/paciente/mis-citas', label: 'Mis citas', ic: 'clipboard', count: true },
    { path: '/perfil', label: 'Mi perfil', ic: 'user' },
  ],
};

const ROLE_TEXT: Record<Rol, string> = { DOCTOR: 'Panel del doctor', ADMIN: 'Panel de administración', PACIENTE: 'Portal del paciente' };

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Avatar],
  template: `
    <aside class="sidebar" aria-label="Navegación principal">
      <div class="brand">
        <svg appIcon="logo"></svg>
        <span>Clínica Celeste</span>
      </div>
      <p class="side-role">{{ roleText() }}</p>
      <nav class="nav">
        @for (n of nav(); track n.path) {
          <a class="nav-link" [routerLink]="n.path" routerLinkActive="is-active">
            <svg [appIcon]="n.ic"></svg><span>{{ n.label }}</span>
            @if (n.count) { <span class="nav-count">{{ pendingCount() }}</span> }
          </a>
        }
      </nav>
      <div class="side-foot">
        <div class="me">
          <app-avatar [name]="auth.nombre()" />
          <div><strong>{{ auth.nombre() }}</strong><span>{{ auth.subtitulo() }}</span></div>
        </div>
        <button class="btn btn-ghost btn-block" (click)="logout()">
          <svg appIcon="logout"></svg>
          Cerrar sesión
        </button>
      </div>
    </aside>
    <div class="scrim" (click)="menuOpen.set(false)"></div>

    <div class="main">
      <header class="topbar">
        <button class="icon-btn menu-btn" (click)="menuOpen.set(true)" aria-label="Abrir menú">
          <svg appIcon="menu"></svg>
        </button>
        <div class="topbar-title">
          <span>{{ titulo() }}</span>
          <small>{{ hoy }}</small>
        </div>
        <div class="topbar-actions">
          <button class="avatar avatar-btn" [attr.data-tone]="tone()" routerLink="/perfil" aria-label="Ir a mi perfil">{{ ini() }}</button>
        </div>
      </header>

      <main class="content" [class.view-enter]="entering()" tabindex="-1">
        @if (skeleton()) {
          <div class="sk sk-title"></div>
          <div class="sk sk-line"></div>
          <div class="sk sk-strip"></div>
          <div class="sk-grid"><div class="sk sk-panel"></div><div class="sk sk-panel"></div></div>
        }
        <div class="view-host" [hidden]="skeleton()"><router-outlet /></div>
      </main>
    </div>`,
  host: {
    class: 'app',
    '[class.menu-open]': 'menuOpen()',
  },
})
export class Shell {
  protected readonly auth = inject(AuthService);
  private readonly citasPaciente = inject(CitasPacienteStore);
  private readonly agendaDoctor = inject(AgendaDoctorStore);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  protected readonly hoy = longDate(TODAY);
  protected readonly menuOpen = signal(false);
  protected readonly skeleton = signal(false);
  protected readonly entering = signal(true);
  protected readonly titulo = signal('');

  protected readonly nav = computed(() => NAV[this.auth.rol()!]);
  protected readonly roleText = computed(() => ROLE_TEXT[this.auth.rol()!]);
  protected readonly ini = computed(() => initials(this.auth.nombre().split('@')[0]));
  protected readonly tone = computed(() => toneOf(this.auth.nombre()));

  /** Doctor: citas por confirmar desde hoy (en los meses ya pedidos). Paciente: próximas citas activas */
  protected readonly pendingCount = computed(() => {
    const now = ahora();
    if (this.auth.rol() === 'DOCTOR') return this.agendaDoctor.citas().filter((c) => c.fechaHora >= now.slice(0, 10) && c.estado === 'PENDIENTE').length;
    if (this.auth.rol() === 'PACIENTE') return this.citasPaciente.citas().filter((c) => c.fechaHora >= now && c.estado !== 'CANCELADA').length;
    return 0;
  });

  constructor() {
    if (this.auth.rol() === 'PACIENTE') this.citasPaciente.cargar();
    if (this.auth.rol() === 'DOCTOR') this.agendaDoctor.asegurar([mesDe(TODAY), mesDe(new Date(TODAY.getFullYear(), TODAY.getMonth() + 1, 1))]);
    this.titulo.set(this.tituloActual());
    let pending = false;
    let t1: ReturnType<typeof setTimeout> | undefined;
    let t2: ReturnType<typeof setTimeout> | undefined;

    // Cambio de vista con una breve carga de esqueleto, igual que la versión original
    const sub = this.router.events.subscribe((e) => {
      if (e instanceof NavigationStart) {
        pending = true;
        clearTimeout(t1); clearTimeout(t2);
        this.menuOpen.set(false);
        this.entering.set(false);
        this.skeleton.set(true);
        window.scrollTo({ top: 0, behavior: 'smooth' });
      } else if (e instanceof NavigationEnd) {
        this.titulo.set(this.tituloActual());
        if (!pending) return;
        pending = false;
        t1 = setTimeout(() => {
          this.skeleton.set(false);
          this.entering.set(true);
          t2 = setTimeout(() => this.entering.set(false), 900);
        }, 420);
      } else if (e instanceof NavigationCancel || e instanceof NavigationError) {
        pending = false;
        this.skeleton.set(false);
      }
    });
    t2 = setTimeout(() => this.entering.set(false), 900);
    inject(DestroyRef).onDestroy(() => { sub.unsubscribe(); clearTimeout(t1); clearTimeout(t2); });
  }

  private tituloActual(): string {
    let r: ActivatedRouteSnapshot | null = this.router.routerState.snapshot.root;
    let t = '';
    while (r) { t = r.data['titulo'] ?? t; r = r.firstChild; }
    return t;
  }

  protected async logout() {
    await this.auth.salir();
    window.scrollTo(0, 0);
    this.toast.show('Cerraste sesión');
  }
}
