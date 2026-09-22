package ec.edu.uteq.soporte.authservice.presentation;

import ec.edu.uteq.soporte.authservice.application.AuthService;
import ec.edu.uteq.soporte.authservice.application.InvalidTokenException;
import ec.edu.uteq.soporte.authservice.application.RegisterCommand;
import ec.edu.uteq.soporte.authservice.application.TokenPair;
import ec.edu.uteq.soporte.authservice.application.TokenValidation;
import ec.edu.uteq.soporte.authservice.domain.User;
import ec.edu.uteq.soporte.authservice.presentation.dto.ApiResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.AuthResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.LoginRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.LogoutRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RefreshRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.RegisterRequest;
import ec.edu.uteq.soporte.authservice.presentation.dto.UserResponse;
import ec.edu.uteq.soporte.authservice.presentation.dto.ValidateResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        RegisterCommand command = new RegisterCommand(request.email(), request.password(), request.fullName());
        User created = authService.register(command);
        return ApiResponse.of(UserResponse.from(created), "Usuario registrado");
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        TokenPair pair = authService.login(request.email(), request.password());
        return ApiResponse.of(toAuthResponse(pair), "Sesion iniciada");
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        TokenPair pair = authService.refresh(request.refreshToken());
        return ApiResponse.of(toAuthResponse(pair), "Token renovado");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());
        return ApiResponse.of(null, "Sesion cerrada");
    }

    // "/validate" queda permitAll() en SecurityConfig: el parseo del token ocurre aqui
    // mismo (no via SecurityContext) para que un token invalido/expirado devuelva un
    // 401 con el envoltorio ApiResponse en vez del handler generico de Spring Security.
    @GetMapping("/validate")
    public ApiResponse<ValidateResponse> validate(@RequestHeader("Authorization") String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new InvalidTokenException("Encabezado Authorization ausente o mal formado");
        }
        String token = authorizationHeader.substring("Bearer ".length());
        TokenValidation validation = authService.validate(token);
        return ApiResponse.of(toValidateResponse(validation), "Token valido");
    }

    private static AuthResponse toAuthResponse(TokenPair pair) {
        return new AuthResponse(pair.accessToken(), pair.refreshToken(), pair.accessTokenExpiresAt());
    }

    private static ValidateResponse toValidateResponse(TokenValidation validation) {
        return new ValidateResponse(
                validation.userId(),
                validation.email(),
                validation.role(),
                validation.zone(),
                validation.permissions(),
                validation.expiresAt());
    }
}
