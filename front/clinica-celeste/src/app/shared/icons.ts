import { computed, Directive, inject, input } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

const ICONS = {
  home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
  calendar: '<rect x="3" y="4.5" width="18" height="16.5" rx="2.5"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/>',
  chart: '<path d="M4 20V11M10 20V5M16 20v-6M21 20H3"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>',
  steth: '<path d="M6 3H5v6a5 5 0 0 0 10 0V3h-1"/><path d="M10 14v1a5 5 0 0 0 10 0v-3"/><circle cx="20" cy="10" r="2"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  clipboard: '<rect x="5" y="4" width="14" height="17" rx="2"/><path d="M9 4V3h6v1M9 10h6M9 14h6M9 18h3"/>',
  left: '<path d="m15 18-6-6 6-6"/>',
  right: '<path d="m9 18 6-6-6-6"/>',
  check: '<path d="M5 12.5l4.5 4.5L19 7.5"/>',
  x: '<path d="M6 6l12 12M18 6 6 18"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  pin: '<path d="M12 21s-7-6.2-7-11.5a7 7 0 0 1 14 0C19 14.8 12 21 12 21z"/><circle cx="12" cy="9.5" r="2.5"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  eye: '<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>',
  alert: '<path d="M12 3 2 20h20L12 3z"/><path d="M12 10v4M12 17h.01"/>',
  leaf: '<path d="M5 19c0-8 5-14 15-15-1 10-7 15-15 15z"/><path d="M5 19l8-8"/>',
  refresh: '<path d="M20 11a8 8 0 0 0-14.9-3M4 5v4h4"/><path d="M4 13a8 8 0 0 0 14.9 3M20 19v-4h-4"/>',
  ban: '<circle cx="12" cy="12" r="9"/><path d="M5.6 5.6l12.8 12.8"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  bell: '<path d="M6 16v-5a6 6 0 0 1 12 0v5l1.5 2h-15z"/><path d="M10 20a2 2 0 0 0 4 0"/>',
  logout: '<path d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"/><path d="M10 17l-5-5 5-5M5 12h11"/>',
  // Logo de Clínica Celeste: escudo con cruz médica
  logo: '<path d="M12 2.8c-2.6 2-5.4 2.8-8 2.9V11c0 5.2 3.4 8.6 8 10.2 4.6-1.6 8-5 8-10.2V5.7c-2.6-.1-5.4-.9-8-2.9z"/><path d="M12 8.5v6M9 11.5h6"/>',
} as const;

export type IconName = keyof typeof ICONS;

/** Uso: <svg appIcon="calendar"></svg>. El propio <svg> es el ícono, así el CSS de .ic aplica igual que antes */
@Directive({
  selector: 'svg[appIcon]',
  host: {
    class: 'ic',
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    'stroke-width': '1.8',
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    'aria-hidden': 'true',
    '[innerHTML]': 'svg()',
  },
})
export class Icon {
  readonly appIcon = input.required<IconName>();
  private readonly sanitizer = inject(DomSanitizer);
  // Las figuras son constantes del código, no entrada del usuario
  protected readonly svg = computed(() => this.sanitizer.bypassSecurityTrustHtml(ICONS[this.appIcon()]));
}
