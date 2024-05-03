package de.cofinpro.account.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;

/**
 * Spring WebFlux security configuration, that sets up the Security WebFilterChain with access information to
 * the endpoints, Http-Basic authentication manager and error handling and CSRF disabling.
 * Further, beans provide the ReactiveUserDetailsService and a BcryptPasswordEncoder for use in the authentication
 * manager.
 */
@EnableWebFluxSecurity
@Configuration
public class AccountWebSecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
                                                            ReactiveAuthenticationManager authenticationManager,
                                                            ServerAccessDeniedHandler accessDeniedHandler,
                                                            ServerAuthenticationEntryPoint authenticationEntryPoint) {
        http.csrf().disable()
                .httpBasic(httpBasicSpec -> httpBasicSpec
                        .authenticationManager(authenticationManager)
                        // when moving next line to exceptionHandlingSpecs, get empty body 401 for authentication failures (e.g. Invalid Credentials)
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                .authorizeExchange()
                .pathMatchers("/api/auth/signup").permitAll()
                .pathMatchers(HttpMethod.GET,"/actuator", "/actuator/**").permitAll()
                // without next line: accessDeniedHandler not working for POST (i.e. CSRF-relevant calls)
                .pathMatchers("/error", "/error/**").permitAll()
                .pathMatchers("/api/security/**").hasRole("AUDITOR")
                .pathMatchers("/api/admin/**").hasRole("ADMINISTRATOR")
                .pathMatchers("/api/acct/**").hasRole("ACCOUNTANT")
                .pathMatchers(HttpMethod.GET, "/api/empl/payment").hasAnyRole("ACCOUNTANT", "USER")
                .pathMatchers("/api/**").authenticated()
                .and()
                .exceptionHandling(exceptionHandlingSpec -> exceptionHandlingSpec
                        // next line needed to have full error Json for 403 (instead of empty body)
                        .accessDeniedHandler(accessDeniedHandler)
                ).formLogin();
        return http.build();
    }
}
