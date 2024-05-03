package de.cofinpro.account.security;

import de.cofinpro.account.audit.BruteForceProtector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * error handling component for the authentication failures on authenticated endpoints.
 */
@Component
public class AccountServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private final BruteForceProtector bruteForceProtector;

    @Autowired
    public AccountServerAuthenticationEntryPoint(BruteForceProtector bruteForceProtector) {
        this.bruteForceProtector = bruteForceProtector;
    }

    /**
     * overridden method to return a custom json exception and do security logging if a username was given
     * as authentication.
     */
    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException ex) {
        if (ex instanceof AccountBadCredentialsException exception) {
            return bruteForceProtector.handleLoginFail(exception.getUser(), exchange.getRequest().getPath().value())
                    .then(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage())));
        }
        return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, ex.getMessage()));
    }
}
