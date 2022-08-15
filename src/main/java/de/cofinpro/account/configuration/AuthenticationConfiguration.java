package de.cofinpro.account.configuration;

import de.cofinpro.account.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * collection of authentication related beans and configuration stuff as constants for the authentication endpoints,
 * breached passwords set and check-method.
 */
@Configuration
public class AuthenticationConfiguration {

    /**
     * UserDetailsService bean, that just delegates the retrieval to the LoginReactiveRepository
     * (functional interface implementation of the findByUsername - method).
     * NOTE: the bean is instantiated by the Spring framework internally...
     * @param users the Login Reactive repository
     * @return UserDetailsService instance (anonymous via method-reference).
     */
    @Bean
    @Autowired
    public ReactiveUserDetailsService userDetailsService(LoginReactiveRepository users,
                                                         LoginRoleReactiveRepository roles) {
        return email -> users.findByEmail(email).ofType(Login.class)
                    .zipWith(roles.findRolesByEmail(email), Login::setRoles);
    }

    @Bean
    public PasswordEncoder getEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    @Autowired
    public List<Role> getRoles(RoleReactiveRepository roleRepository) {
        return roleRepository.findAll().collectList().block(Duration.ofMillis(200));
    }

    public static final int BCRYPT_STRENGTH = 13;

    public static final int MIN_PASSWORD_LENGTH = 12;

    public static final String USER_EXISTS_ERRORMSG = "User exist!";

    public static final String PASSWORD_TOO_SHORT_ERRORMSG = "The password length must be at least "
            + MIN_PASSWORD_LENGTH + " chars!";

    public static final String PASSWORD_HACKED_ERRORMSG = "The password is in the hacker's database!";

    public static final String SAME_PASSWORD_ERRORMSG = "The passwords must be different!";

    public static final String PASSWORD_UPDATEMSG = "The password has been updated successfully";

    /**
     * checks a provided password against a collection of known breached passwords.
     * @param password to check
     * @return check result (true, if password is breached)
     */
    public static boolean passwordIsHacked(String password) {
        return breachedPasswords.contains(password);
    }

    private static final Set<String> breachedPasswords = Set.of("PasswordForJanuary", "PasswordForFebruary",
            "PasswordForMarch", "PasswordForApril", "PasswordForMay", "PasswordForJune", "PasswordForJuly", "PasswordForAugust",
            "PasswordForSeptember", "PasswordForOctober", "PasswordForNovember", "PasswordForDecember");
}
