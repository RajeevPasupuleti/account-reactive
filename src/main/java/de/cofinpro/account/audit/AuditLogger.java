package de.cofinpro.account.audit;

import de.cofinpro.account.admin.LockUserToggleRequest;
import de.cofinpro.account.admin.RoleToggleRequest;
import de.cofinpro.account.persistence.SecurityEvent;
import de.cofinpro.account.persistence.SecurityEventReactiveRepository;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * component bean, that takes over all the security logging. It autowires the SecurityEventReactiveRepository.
 */
@Component
public class AuditLogger {

    private final SecurityEventReactiveRepository auditRepository;

    public AuditLogger(SecurityEventReactiveRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * logs all signup activity
     */
    public Mono<SecurityEvent> logCreateUser(String newUser) {
        return auditRepository.save(SecurityEvent.builder().action("CREATE_USER")
                .subject("Anonymous").object(newUser).path("/api/auth/signup").build());
    }

    /**
     * detailed log of the admin's action on toggling roles GRANT or REMOVE.
     */
    public Mono<SecurityEvent> logToggleRole(String admin, RoleToggleRequest roleToggleRequest) {
        String action = roleToggleRequest.operation().toUpperCase() + "_ROLE";
        String role = roleToggleRequest.role().toUpperCase();
        String object = roleToggleRequest.operation().equalsIgnoreCase("GRANT")
                ? "Grant role %s to %s".formatted(role, roleToggleRequest.user())
                : "Remove role %s from %s".formatted(role, roleToggleRequest.user());
        return auditRepository.save(SecurityEvent.builder().action(action).subject(admin)
                .object(object).path("/api/admin/user/role").build());
    }

    /**
     * security log of the lock or unlock action of a user by the admin.
     */
    public Mono<SecurityEvent> logToggleUserLock(String admin, LockUserToggleRequest lockToggleRequest) {
        String action = lockToggleRequest.operation().toUpperCase() + "_USER";
        String object = lockToggleRequest.operation().equalsIgnoreCase("LOCK")
                ? "Lock user %s".formatted(lockToggleRequest.user())
                : "Unlock user %s".formatted(lockToggleRequest.user());
        return auditRepository.save(SecurityEvent.builder().action(action).subject(admin)
                .object(object).path("/api/admin/user/access").build());
    }

    /**
     * log of admin's action of deleting a user.
     */
    public Mono<SecurityEvent> logDeleteUser(String admin, String user) {
        return auditRepository.save(SecurityEvent.builder().action("DELETE_USER").subject(admin)
                .object(user).path("/api/admin/user").build());
    }

    /**
     * security log of change password actions
     */
    public Mono<SecurityEvent> logChangePassword(String email) {
        return auditRepository.save(SecurityEvent.builder().action("CHANGE_PASSWORD").subject(email)
                .object(email).path("/api/auth/changepass").build());
    }

    /**
     * failure logging of unauthorized access to authorized endpoints
     */
    public Mono<SecurityEvent> logAccessDenied(String user, String path) {
        return auditRepository.save(SecurityEvent.builder().action("ACCESS_DENIED").subject(user)
                .object(path).path(path).build());
    }

    /**
     * audit logging for failed logins to authenticated endpoints.
     */
    public Mono<SecurityEvent> logFailedLogin(String user, String path) {
        return auditRepository.save(SecurityEvent.builder().action("LOGIN_FAILED").subject(user)
                .object(path).path(path).build());
    }

    /**
     * logging od a brut force event, where a user gets locked by the system.
     */
    public Mono<SecurityEvent> logBruteForce(String user, String path) {
        return auditRepository.save(SecurityEvent.builder().action("BRUTE_FORCE").subject(user)
                        .object(path).path(path).build())
                .then(auditRepository.save(SecurityEvent.builder().action("LOCK_USER").subject(user)
                        .object("Lock user %s".formatted(user)).path(path).build()));
    }
}
