package de.cofinpro.account.security;

import de.cofinpro.account.audit.AuditLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * handler for 403 errors with missing role for authorized endpoints
 */
@Component
public class AccountServerAccessDeniedHandler implements ServerAccessDeniedHandler {

    private final AuditLogger auditLogger;

    @Autowired
    public AccountServerAccessDeniedHandler(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    /**
     * do a security event log for access denied cases with the user name and throw a custom json exception.
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        return exchange.getPrincipal()
                .flatMap(user ->
                        auditLogger.logAccessDenied(user.getName(), exchange.getRequest().getPath().value()))
                .then(Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, denied.getMessage() + "!")));
    }
}
