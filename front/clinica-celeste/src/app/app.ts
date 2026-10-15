import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { Boot, Overlay, Toasts } from './layout/capas';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Boot, Toasts, Overlay],
  template: `
    <router-outlet />
    <app-boot />
    <app-toasts />
    <app-overlay />`,
})
export class App {}
