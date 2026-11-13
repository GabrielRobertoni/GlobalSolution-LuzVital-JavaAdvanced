package br.com.fiap.luzvital.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Senhas novas (cadastro pela API) são sempre gravadas com BCrypt.
 * <p>
 * Compatibilidade com a carga do banco da disciplina: os usuários do script Oracle têm em ds_senha um texto
 * fictício sem hash (ex.: 'hash_carlos123'). Como a API não altera os dados do banco, esses usuários de
 * demonstração entram digitando exatamente esse texto. A comparação é feita em tempo constante.
 */
public class SenhaEncoder implements PasswordEncoder {

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    @Override
    public String encode(CharSequence senha) {
        return bcrypt.encode(senha);
    }

    @Override
    public boolean matches(CharSequence senha, String gravada) {
        if (senha == null || gravada == null || gravada.isEmpty()) {
            return false;
        }
        if (ehBcrypt(gravada)) {
            return bcrypt.matches(senha, gravada);
        }
        return MessageDigest.isEqual(senha.toString().getBytes(StandardCharsets.UTF_8),
                gravada.getBytes(StandardCharsets.UTF_8));
    }

    static boolean ehBcrypt(String gravada) {
        return gravada.startsWith("$2a$") || gravada.startsWith("$2b$") || gravada.startsWith("$2y$");
    }
}
