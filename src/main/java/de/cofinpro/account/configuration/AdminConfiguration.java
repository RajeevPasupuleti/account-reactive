package de.cofinpro.account.configuration;

import de.cofinpro.account.persistence.Role;
import de.cofinpro.account.persistence.RoleReactiveRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * configuration collection for the admin endpoints. Offers a bean, that reads all system roles from the roles
 * table for validating use in the admin handler. Many string constants for user (error) messages.
 */
@Configuration
public class AdminConfiguration {

    @Bean
    public List<Role> getRoles(RoleReactiveRepository roles) {
        return roles.findAll().collectList().block(Duration.ofMillis(300));
    }

    public static final int LOGIN_FAILED_LIMIT = 5;
    public static final String ADMIN_ROLE = "ROLE_ADMINISTRATOR";
    public static final String DELETED_SUCCESSFULLY = "Deleted successfully!";
    public static final String USER_NOT_FOUND_ERRORMSG = "User not found!";
    public static final String ROLE_NOT_FOUND_ERRORMSG = "Role not found!";
    public static final String USER_HASNT_ROLE_ERRORMSG = "The user does not have a role!";
    public static final String USER_HAS_ROLE_ALREADY_ERRORMSG = "The user has the role already!";
    public static final String USER_NEEDS_ROLE_ERRORMSG = "The user must have at least one role!";
    public static final String CANT_DELETE_ADMIN_ERRORMSG = "Can't remove ADMINISTRATOR role!";
    public static final String CANT_LOCK_ADMIN_ERRORMSG = "Can't lock the ADMINISTRATOR!";
    public static final String INVALID_ROLE_COMBINE_ERRORMSG = "The user cannot combine administrative and business roles!";
}
