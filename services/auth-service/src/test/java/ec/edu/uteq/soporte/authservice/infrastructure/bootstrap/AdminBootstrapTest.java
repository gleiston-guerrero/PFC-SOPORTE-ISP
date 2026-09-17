package ec.edu.uteq.soporte.authservice.infrastructure.bootstrap;

import ec.edu.uteq.soporte.authservice.domain.Role;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.domain.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AdminBootstrap no tenia ninguna prueba: su unico comportamiento no trivial (crear el
 * ADMIN por defecto solo si todavia no existe ninguno) es justo el tipo de logica que
 * se rompe en silencio -- si el guardia existsByRole se invierte o se borra sin darse
 * cuenta, el sistema empieza a crear un ADMIN duplicado en cada arranque, o deja de
 * crearlo cuando hace falta, y ninguna prueba lo hubiera detectado hasta ahora.
 */
@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String EMAIL = "admin@soporte.local";
    private static final String PASSWORD_EN_CLARO = "Admin123!";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AdminBootstrap bootstrap() {
        return new AdminBootstrap(userRepository, passwordEncoder, EMAIL, PASSWORD_EN_CLARO);
    }

    @Test
    void cuandoNoExisteNingunAdminCreaUnoConLaContrasenaCifrada() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD_EN_CLARO)).thenReturn("$2a$10$hash-simulado-de-bcrypt");

        bootstrap().run(mockArgs());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User creado = captor.getValue();
        assertThat(creado.getEmail()).isEqualTo(EMAIL);
        assertThat(creado.getRole()).isEqualTo(Role.ADMIN);
        assertThat(creado.isActive()).isTrue();
        // La contrasena guardada tiene que ser el hash, nunca el texto en claro que
        // llego por configuracion -- si esto fallara, la contrasena de arranque
        // quedaria legible tal cual en la base de datos.
        assertThat(creado.getPasswordHash()).isEqualTo("$2a$10$hash-simulado-de-bcrypt");
        assertThat(creado.getPasswordHash()).isNotEqualTo(PASSWORD_EN_CLARO);
    }

    @Test
    void cuandoYaExisteUnAdminNoCreaOtro() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);

        bootstrap().run(mockArgs());

        verify(userRepository, never()).save(any());
    }

    private ApplicationArguments mockArgs() {
        return org.mockito.Mockito.mock(ApplicationArguments.class);
    }
}
