package com.citas.medicas.service;

import com.citas.medicas.entity.PropositoToken;
import com.citas.medicas.entity.TokenVerificacion;
import com.citas.medicas.entity.Usuario;
import com.citas.medicas.exception.TokenInvalidoException;
import com.citas.medicas.repository.TokenVerificacionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class TokenVerificacionService {

    private static final int BYTES_ALEATORIOS = 32;
    private static final String ENLACE_INVALIDO = "El enlace no es válido o ya venció. Pide uno nuevo.";

    private final SecureRandom aleatorio = new SecureRandom();
    private final TokenVerificacionRepository tokenRepository;
    private final Clock reloj;
    private final Duration vigenciaVerificacion;
    private final Duration vigenciaRecuperacion;

    public TokenVerificacionService(TokenVerificacionRepository tokenRepository,
                                    Clock reloj,
                                    @Value("${citas.verificacion.vigencia}") Duration vigenciaVerificacion,
                                    @Value("${citas.recuperacion.vigencia}") Duration vigenciaRecuperacion) {
        this.tokenRepository = tokenRepository;
        this.reloj = reloj;
        this.vigenciaVerificacion = vigenciaVerificacion;
        this.vigenciaRecuperacion = vigenciaRecuperacion;
    }

    @Transactional
    public String generar(Usuario usuario, PropositoToken proposito) {
        tokenRepository.borrarDelUsuario(usuario.getId(), proposito);

        byte[] bytes = new byte[BYTES_ALEATORIOS];
        aleatorio.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        TokenVerificacion registro = new TokenVerificacion();
        registro.setUsuario(usuario);
        registro.setHash(hash(token));
        registro.setProposito(proposito);
        registro.setExpiraEn(LocalDateTime.now(reloj).plus(vigenciaDe(proposito)));
        tokenRepository.save(registro);

        return token;
    }

    @Transactional
    public Usuario consumir(String token, PropositoToken proposito) {
        TokenVerificacion registro = tokenRepository.buscarConUsuario(hash(token), proposito)
                .filter(t -> t.getExpiraEn().isAfter(LocalDateTime.now(reloj)))
                .orElseThrow(() -> new TokenInvalidoException(ENLACE_INVALIDO));

        // Si dos peticiones llegan a la vez con el mismo token, solo una logra borrarlo
        if (tokenRepository.borrarPorId(registro.getId()) == 0) {
            throw new TokenInvalidoException(ENLACE_INVALIDO);
        }
        return registro.getUsuario();
    }

    private Duration vigenciaDe(PropositoToken proposito) {
        return switch (proposito) {
            case VERIFICAR_EMAIL -> vigenciaVerificacion;
            case RECUPERAR_PASSWORD -> vigenciaRecuperacion;
        };
    }

    static String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible", ex);
        }
    }
}
