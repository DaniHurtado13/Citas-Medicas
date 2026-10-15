package com.citas.medicas.config;

import com.citas.medicas.entity.Rol;
import com.citas.medicas.entity.TipoDocumento;
import com.citas.medicas.repository.UsuarioRepository;
import com.citas.medicas.service.UsuarioService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminInicialTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private UsuarioService usuarioService;

    private AdminInicial con(String email, String password, String documento) {
        return new AdminInicial(usuarioRepository, usuarioService, email, password, TipoDocumento.CC, documento);
    }

    @Test
    void siYaExisteUnAdminNoCreaNadaAunqueHayaVariables() {
        when(usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)).thenReturn(true);

        con("otro@citas.com", "clave12345", "1000000001").run(null);

        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any());
    }

    @Test
    void sinAdminYSinVariablesSoloAvisa() {
        when(usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)).thenReturn(false);

        con("", "", "").run(null);

        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any());
    }

    @Test
    void sinAdminYConVariablesCreaElAdminConEmailNormalizado() {
        when(usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)).thenReturn(false);

        con("  Admin@Citas.COM ", "clave12345", " 1000000001 ").run(null);

        verify(usuarioService).crearUsuario(TipoDocumento.CC, "1000000001", "admin@citas.com", "clave12345", Rol.ADMIN);
    }

    @Test
    void passwordCortaImpideArrancar() {
        when(usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)).thenReturn(false);
        AdminInicial admin = con("admin@citas.com", "corta", "1000000001");

        assertThrows(IllegalStateException.class, () -> admin.run(null));
        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any());
    }

    @Test
    void passwordDeMasDe72BytesImpideArrancar() {
        when(usuarioRepository.existsByRolAndDemoFalse(Rol.ADMIN)).thenReturn(false);
        AdminInicial admin = con("admin@citas.com", "ñ".repeat(37), "1000000001");

        assertThrows(IllegalStateException.class, () -> admin.run(null));
        verify(usuarioService, never()).crearUsuario(any(), any(), any(), any(), any());
    }
}
