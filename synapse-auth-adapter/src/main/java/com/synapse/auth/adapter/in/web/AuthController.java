package com.synapse.auth.adapter.in.web;

import cn.dev33.satoken.stp.StpUtil;
import com.synapse.auth.adapter.in.web.dto.LoginResponse;
import com.synapse.auth.adapter.in.web.dto.LoginRequest;
import com.synapse.auth.adapter.in.web.dto.UserResponse;
import com.synapse.auth.port.in.AuthenticationUseCase;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationUseCase authenticationUseCase;

    public AuthController(AuthenticationUseCase authenticationUseCase) {
        this.authenticationUseCase = authenticationUseCase;
    }

    @PostMapping("/login")
    public Mono<LoginResponse> login(@RequestBody LoginRequest request) {
        return SaTokenReactorBridge.blockingCall(() -> {
            AuthenticationUseCase.LoginResult result = authenticationUseCase.login(
                    new AuthenticationUseCase.LoginCommand(request.username(), request.password()));
            return new LoginResponse(result.userId(), result.username(), result.displayName(),
                    result.roles(), result.permissions(), StpUtil.getTokenName(), StpUtil.getTokenValue());
        });
    }

    @PostMapping("/logout")
    public Mono<Void> logout() {
        return SaTokenReactorBridge.blockingAction(authenticationUseCase::logout);
    }

    @GetMapping("/me")
    public Mono<UserResponse> me() {
        return SaTokenReactorBridge.blockingCall(() -> {
            AuthenticationUseCase.CurrentUser user = authenticationUseCase.currentUser();
            return new UserResponse(user.id().value(), user.username(), user.displayName(),
                    user.roles(), user.permissions(), true, user.createdAt());
        });
    }
}
