import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { AuthService } from './core/auth/auth.service';
import { authGuard, invitadoGuard, rolGuard } from './core/auth/guards';

export const routes: Routes = [
  // La raíz decide a dónde ir. Sin esta regla, "/" coincidía con el marco de acceso vacío (sin formulario)
  {
    path: '', pathMatch: 'full', redirectTo: () => {
      const auth = inject(AuthService);
      return auth.usuario() ? auth.inicio() : '/login';
    },
  },
  // Sin sesión: todas comparten el marco de layout/acceso.ts. Si ninguna hija coincide, el router sigue con el shell
  {
    path: '',
    loadComponent: () => import('./layout/acceso').then((m) => m.Acceso),
    children: [
      { path: 'login', canActivate: [invitadoGuard], loadComponent: () => import('./features/login/login').then((m) => m.Login) },
      { path: 'registro', canActivate: [invitadoGuard], loadComponent: () => import('./features/cuenta/registro').then((m) => m.Registro) },
      { path: 'olvide-password', canActivate: [invitadoGuard], loadComponent: () => import('./features/cuenta/olvide-password').then((m) => m.OlvidePassword) },
      // Destinos de los enlaces de los correos (las rutas las fija el back en CorreosDeCuenta.java)
      { path: 'verificar-email', loadComponent: () => import('./features/cuenta/verificar-email').then((m) => m.VerificarEmail) },
      { path: 'restablecer-password', loadComponent: () => import('./features/cuenta/restablecer-password').then((m) => m.RestablecerPassword) },
    ],
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then((m) => m.Shell),
    children: [
      // Doctor
      {
        path: 'doctor', canActivate: [rolGuard('DOCTOR')], children: [
          { path: 'resumen', data: { titulo: 'Resumen' }, loadComponent: () => import('./features/doctor/resumen').then((m) => m.DoctorResumen) },
          { path: 'agenda', data: { titulo: 'Agenda' }, loadComponent: () => import('./features/doctor/agenda').then((m) => m.DoctorAgenda) },
          { path: 'horario', data: { titulo: 'Mi horario' }, loadComponent: () => import('./features/doctor/horario/horario').then((m) => m.DoctorHorario) },
        ],
      },

      // Administrador
      {
        path: 'admin', canActivate: [rolGuard('ADMIN')], children: [
          { path: 'resumen', data: { titulo: 'Resumen general' }, loadComponent: () => import('./features/admin/resumen').then((m) => m.AdminResumen) },
          { path: 'doctores', data: { titulo: 'Doctores' }, loadComponent: () => import('./features/admin/doctores').then((m) => m.AdminDoctores) },
          { path: 'especialidades', data: { titulo: 'Especialidades' }, loadComponent: () => import('./features/admin/especialidades').then((m) => m.AdminEspecialidades) },
        ],
      },

      // Paciente
      {
        path: 'paciente', canActivate: [rolGuard('PACIENTE')], children: [
          { path: 'inicio', data: { titulo: 'Inicio' }, loadComponent: () => import('./features/paciente/inicio').then((m) => m.PacienteInicio) },
          { path: 'agendar', data: { titulo: 'Agendar cita' }, loadComponent: () => import('./features/paciente/agendar').then((m) => m.PacienteAgendar) },
          { path: 'mis-citas', data: { titulo: 'Mis citas' }, loadComponent: () => import('./features/paciente/mis-citas').then((m) => m.PacienteMisCitas) },
        ],
      },

      // Compartida
      { path: 'perfil', data: { titulo: 'Mi perfil' }, loadComponent: () => import('./features/perfil/perfil').then((m) => m.Perfil) },
    ],
  },
  // Absoluta: tras una redirección relativa Angular ya no aplica la regla de la raíz
  { path: '**', redirectTo: '/' },
];
