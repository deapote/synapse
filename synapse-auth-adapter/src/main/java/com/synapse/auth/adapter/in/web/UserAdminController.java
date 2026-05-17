package com.synapse.auth.adapter.in.web;

import cn.dev33.satoken.stp.StpUtil;
import com.synapse.auth.adapter.in.web.dto.*;
import com.synapse.auth.model.RoleName;
import com.synapse.auth.port.in.UserAdminUseCase;
import com.synapse.shared.exception.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class UserAdminController {
    private final UserAdminUseCase userAdminUseCase;
    private final int maxPageSize;

    public UserAdminController(UserAdminUseCase userAdminUseCase,
                               @Value("${synapse.web.max-page-size:100}") int maxPageSize) {
        this.userAdminUseCase = userAdminUseCase;
        this.maxPageSize = maxPageSize;
    }

    @PostMapping("/users")
    public Mono<UserAdminUseCase.UserView> createUser(@RequestBody CreateUserRequest request) {
        return adminCall(() -> userAdminUseCase.createUser(new UserAdminUseCase.CreateUserCommand(
                request.username(), request.displayName(), request.password(), request.roles())));
    }

    @GetMapping("/users")
    public Mono<List<UserAdminUseCase.UserView>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return adminCall(() -> {
            validatePage(page, size);
            return userAdminUseCase.listUsers(page, size);
        });
    }

    @PutMapping("/users/{id}/roles")
    public Mono<UserAdminUseCase.UserView> assignRoles(@PathVariable String id,
                                                       @RequestBody AssignRolesRequest request) {
        return adminCall(() -> userAdminUseCase.assignRoles(id, request.roles()));
    }

    @PutMapping("/users/{id}/enabled")
    public Mono<UserAdminUseCase.UserView> setEnabled(@PathVariable String id,
                                                      @RequestBody SetEnabledRequest request) {
        return adminCall(() -> userAdminUseCase.setEnabled(id, request.enabled()));
    }

    @GetMapping("/roles")
    public Mono<List<RoleResponse>> listRoles() {
        return adminCall(() -> userAdminUseCase.listRoles().stream()
                .map(role -> new RoleResponse(role.name(), role.permissions()))
                .toList());
    }

    @PutMapping("/roles/{roleName}/permissions")
    public Mono<RoleResponse> assignRolePermissions(@PathVariable RoleName roleName,
                                                    @RequestBody AssignRolePermissionsRequest request) {
        return adminCall(() -> {
            UserAdminUseCase.RoleView role = userAdminUseCase.assignRolePermissions(roleName, request.permissions());
            return new RoleResponse(role.name(), role.permissions());
        });
    }

    private <T> Mono<T> adminCall(CheckedSupplier<T> supplier) {
        return SaTokenReactorBridge.blockingCall(() -> {
            StpUtil.checkPermission("AUTH_ADMIN");
            return supplier.get();
        });
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > maxPageSize) {
            throw new DomainException("分页参数非法");
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }
}
