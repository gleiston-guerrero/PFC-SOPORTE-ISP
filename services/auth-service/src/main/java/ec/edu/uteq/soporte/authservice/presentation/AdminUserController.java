package ec.edu.uteq.soporte.authservice.presentation;

import ec.edu.uteq.soporte.authservice.application.AuthService;
import ec.edu.uteq.soporte.authservice.application.CreateUserCommand;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.presentation.dto.ApiResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.CreateUserRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Alta de usuarios con rol elegido (CLIENTE/TECNICO/ADMIN) -- a diferencia del
 * registro publico, que siempre fuerza CLIENTE. Requiere un access token con rol
 * ADMIN (ver infrastructure/security/SecurityConfig + JwtAuthenticationFilter).
 */
@RestController
@RequestMapping("/api/v1/auth/admin")
public class AdminUserController {

    private final AuthService authService;

    public AdminUserController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        CreateUserCommand command = new CreateUserCommand(
                request.email(), request.password(), request.fullName(), request.role(), request.zone());
        User created = authService.createUserAsAdmin(command);
        return ApiResponse.of(UserResponse.from(created), "Usuario creado");
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<List<UserResponse>> listUsers() {
        List<UserResponse> users = authService.listUsers().stream().map(UserResponse::from).toList();
        return ApiResponse.of(users, "OK");
    }
}
