package de.cofinpro.account.configuration;

import de.cofinpro.account.audit.AuditHandler;
import de.cofinpro.account.domain.AccountHandler;
import de.cofinpro.account.authentication.AuthenticationHandler;
import de.cofinpro.account.admin.AdminHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;

/**
 * routing configuration (webflux.fn) defining the route-handler methods for all andpoints of account-reactive
 */
@Configuration
public class RoutingConfiguration {

    @Bean
    public RouterFunction<ServerResponse> routes(AuthenticationHandler authenticationHandler,
                                                 AccountHandler accountHandler,
                                                 AdminHandler adminHandler,
                                                 AuditHandler auditHandler) {
        return route().add(authenticationRoutes(authenticationHandler))
                .add(accountRoutes(accountHandler))
                .add(adminRoutes(adminHandler))
                .add(auditRoutes(auditHandler))
                .build();
    }

    /**
     * route handling for the audit specific routes
     * @param auditHandler handler
     */
    private RouterFunction<ServerResponse> auditRoutes(AuditHandler auditHandler) {
        return route()
                .GET("/api/security/events", auditHandler::getAuditEvents)
                .build();
    }

    /**
     * route handling for the authentication specific routes
     * @param authenticationHandler handler
     */
    private RouterFunction<ServerResponse> authenticationRoutes(AuthenticationHandler authenticationHandler) {
        return route()
                .POST("/api/auth/signup", authenticationHandler::signup)
                .POST("/api/auth/changepass", authenticationHandler::changePassword)
                .build();
    }

    /**
     * route handling for the admin specific routes
     * @param adminHandler handler
     */
    private RouterFunction<ServerResponse> adminRoutes(AdminHandler adminHandler) {
        return route()
                .GET("/api/admin/user", adminHandler::displayUsers)
                .DELETE("/api/admin/user/{email}", adminHandler::deleteUser)
                .PUT("/api/admin/user/role", adminHandler::toggleRole)
                .PUT("/api/admin/user/access", adminHandler::toggleUserLock)
                .build();
    }

    /**
     * route handling for all domain (account / payroll) specific routes
     * @param accountHandler handler
     */
    private RouterFunction<ServerResponse> accountRoutes(AccountHandler accountHandler) {
        return route()
                .GET("/api/empl/payment", accountHandler::accessPayrolls)
                .POST("/api/acct/payments", accountHandler::uploadPayrolls)
                .PUT("/api/acct/payments", accountHandler::changePayrolls)
                .build();
    }
}