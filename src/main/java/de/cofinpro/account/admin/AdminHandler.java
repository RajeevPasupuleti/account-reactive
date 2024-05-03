package de.cofinpro.account.admin;

import de.cofinpro.account.audit.AuditLogger;
import de.cofinpro.account.authentication.SignupResponse;
import de.cofinpro.account.domain.StatusResponse;
import de.cofinpro.account.persistence.Login;
import de.cofinpro.account.persistence.LoginReactiveRepository;
import de.cofinpro.account.persistence.LoginRole;
import de.cofinpro.account.persistence.LoginRoleReactiveRepository;
import de.cofinpro.account.persistence.Role;
import de.cofinpro.account.persistence.SalaryReactiveRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;
import org.springframework.validation.Validator;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static de.cofinpro.account.configuration.AdminConfiguration.*;
import static de.cofinpro.account.configuration.AuthenticationConfiguration.EMAIL_REGEX;
import static java.lang.Boolean.TRUE;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

/**
 * service layer handler class for all admin specific endpoints /api/admin/user (GET, DELETE) and
 * /api/admin/user/{role, access} (PUT).
 */
@Service
public class AdminHandler {

    private final LoginReactiveRepository userRepository;
    private final LoginRoleReactiveRepository roleRepository;
    private final SalaryReactiveRepository salaryRepository;
    private final AuditLogger auditLogger;
    private final List<Role> systemRoles;
    private final Validator validator;

    @Autowired
    public AdminHandler(LoginReactiveRepository userRepository,
                        LoginRoleReactiveRepository roleRepository,
                        SalaryReactiveRepository salaryRepository,
                        AuditLogger auditLogger,
                        List<Role> systemRoles,
                        Validator validator) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.salaryRepository = salaryRepository;
        this.auditLogger = auditLogger;
        this.systemRoles = systemRoles;
        this.validator = validator;
    }

    /**
     * GET /api/admin/user endpoint to display all users ascending by id with their roles, which are zipped to the Login.
     */
    public Mono<ServerResponse> displayUsers(ServerRequest ignoredServerRequest) {
        return ok().body(userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"))
                        .flatMap(login -> Mono.just(login)
                                .zipWith(roleRepository.findRolesByEmail(login.getEmail()), Login::setRoles))
                        .map(Login::toSignupResponse), SignupResponse.class);
    }

    /**
     * DELETE /api/admin/user/{email} endpoint with path variable to have a user deleted from the system with all its
     * associated roles and possible salary table entries.
     * this public method just extracts and checks the given path variables and calls a private method
     */
    public Mono<ServerResponse> deleteUser(ServerRequest request) {
        String email = request.pathVariable("email");
        if (!email.matches(EMAIL_REGEX)) {
            return Mono.error(new ServerWebInputException("Invalid user email given: '" + email + "'!"));
        }
        return ok().body(deleteUser(email, request.principal()), UserDeletedResponse.class);
    }

    /**
     * validates if deletion is possible and deletes all user associated entries from database tables.
     * logs delete_user security event
     * @param email     user's email key
     * @return error Mono if user not found or admin role requested for deletion, a success status response else.
     */
    private Mono<UserDeletedResponse> deleteUser(String email, Mono<? extends Principal> principal) {
        return roleRepository.findRolesByEmail(email)
                .flatMap(this::isAdmin)
                .flatMap(isAdmin -> {
                    if (TRUE.equals(isAdmin)) {
                        return Mono.error(new ServerWebInputException(CANT_DELETE_ADMIN_ERRORMSG));
                    } else {
                        return roleRepository.deleteAllByEmail(email)
                                .then(salaryRepository.deleteAllByEmail(email))
                                .then(userRepository.deleteByEmail(email))
                                .then(principal)
                                .flatMap(admin -> auditLogger.logDeleteUser(admin.getName(), email))
                                .then(Mono.just(new UserDeletedResponse(email, DELETED_SUCCESSFULLY)));
                    }
                });
    }

    /**
     * checks if the specified user has the admin role. If no role to the user is found, it does not exist in the system
     * and an error is raised - otherwise a Boolean Mono with the check result is returned.
     * @param roles the roles to the user from the RoleLogin table.
     * @return check result mono - true, if user has admin role, false else.
     */
    private Mono<Boolean> isAdmin(List<String> roles) {
        if (roles.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND_ERRORMSG));
        }
        return Mono.just(roles.contains(ADMIN_ROLE));
    }

    /**
     * PUT /api/admin/user/role endpoint that consumes a RoleToggleRequest in the Request body with information on
     * which role to toggle for which user.
     */
    public Mono<ServerResponse> toggleRole(ServerRequest request) {
        return request.bodyToMono(RoleToggleRequest.class)
                        .flatMap(req -> ok().body(validateAndToggleRole(req, request.principal()),
                                SignupResponse.class));
    }

    /**
     * PUT /api/admin/user/access endpoint that consumes a LockUserToggleRequest in the Request body with information on
     * which user to lock or unlock.
     */
    public Mono<ServerResponse> toggleUserLock(ServerRequest request) {
        return request.bodyToMono(LockUserToggleRequest.class)
                .flatMap(req -> ok().body(validateAndToggleLock(req, request.principal()), StatusResponse.class));
    }

    /**
     * hibernate validates the request and locks a user, if found and not admin.
     * @param lockToggleRequest json with all information to process
     * @return the StatusResponse Mono or error mono (400 or 404) in case of fail.
     */
    private Mono<StatusResponse> validateAndToggleLock(LockUserToggleRequest lockToggleRequest,
                                                       Mono<? extends Principal> principal) {
        String hibernateValidationErrors = validateHibernate(lockToggleRequest, LockUserToggleRequest.class);
        if (!hibernateValidationErrors.isEmpty()) {
            return Mono.error(new ServerWebInputException(hibernateValidationErrors));
        }
        return roleRepository.findRolesByEmail(lockToggleRequest.user())
                .flatMap(this::isAdmin)
                .flatMap(performLockToggleIfAdminOrError(lockToggleRequest, principal));
    }

    private Function<Boolean, Mono<StatusResponse>>
    performLockToggleIfAdminOrError(LockUserToggleRequest lockToggleRequest, Mono<? extends Principal> principal) {
        boolean lockRequested = lockToggleRequest.operation().equalsIgnoreCase("lock");
        return isAdmin -> {
            if (TRUE.equals(isAdmin)) {
                return Mono.error(new ServerWebInputException(CANT_LOCK_ADMIN_ERRORMSG));
            } else {
                return userRepository
                        .toggleLock(lockToggleRequest.user(), lockRequested)
                        .then(principal)
                        .flatMap(admin -> auditLogger.logToggleUserLock(admin.getName(), lockToggleRequest))
                        .then(Mono.just(new StatusResponse("User %s %sed!".formatted(lockToggleRequest.user(),
                                lockRequested ? "lock" : "unlock"))));
            }
        };
    }

    /**
     * hibernate validation, a role existence check vs the Roles-Table in the database and diverse
     * rule checks are applied. If everything passes, the role is toggled for the specified user (granted
     * or revoked) and a complete user SignupResponse is returned. Security log of toggle role.
     * @param roleToggleRequest json with all information to process
     * @return the SignupResponse Mono or error mono (400 or 404) for various rule faíls.
     */
    private Mono<SignupResponse> validateAndToggleRole(RoleToggleRequest roleToggleRequest,
                                                       Mono<? extends Principal> principal) {
        String hibernateValidationErrors = validateHibernate(roleToggleRequest, RoleToggleRequest.class);
        if (!hibernateValidationErrors.isEmpty()) {
            return Mono.error(new ServerWebInputException(hibernateValidationErrors));
        }
        if (systemRoles.stream().map(Role::getRoleName)
                .noneMatch(role -> role.endsWith(roleToggleRequest.role().toUpperCase()))) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, ROLE_NOT_FOUND_ERRORMSG));
        }
        return roleRepository
                .findRolesByEmail(roleToggleRequest.user())
                .flatMap(userRoles -> isOperationValid(userRoles, roleToggleRequest))
                .flatMap(requestedRole -> roleToggleRequest.operation().equalsIgnoreCase("remove")
                        ? roleRepository.deleteByEmailAndRole(roleToggleRequest.user(), requestedRole)
                        : roleRepository.save(LoginRole.builder().email(roleToggleRequest.user()).role(requestedRole).build())
                ).then(principal)
                .flatMap(admin -> auditLogger.logToggleRole(admin.getName(), roleToggleRequest))
                .then(updatedUserResponse(roleToggleRequest.user()));
    }

    /**
     * method that selects the just modified (regarding role addition removal) user's record from the database
     * to display the new user state as the PUT response in the "happy case".
     * @param email user email key
     */
    private Mono<SignupResponse> updatedUserResponse(String email) {
        return userRepository.findByEmail(email)
                .flatMap(login -> Mono.just(login)
                        .zipWith(roleRepository.findRolesByEmail(login.getEmail()), Login::setRoles))
                .map(Login::toSignupResponse);
    }

    /**
     * check the rules applied by the specification on which role grants or revokes are allowed and possible.
     * @param userRoles the user's roles BEFORE the PUT from the database.
     * @return error mono in case some rule is violated, a Mono with the requestedRole for further processing
     *         if all rules pass.
     */
    private Mono<String> isOperationValid(List<String> userRoles, RoleToggleRequest roleToggleRequest) {
        if (userRoles.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, USER_NOT_FOUND_ERRORMSG));
        }

        boolean isRemove = roleToggleRequest.operation().equalsIgnoreCase("remove");
        String requestedRole = "ROLE_" + roleToggleRequest.role().toUpperCase();
        if (isRemove && !userRoles.contains(requestedRole)) {
            return Mono.error(new ServerWebInputException(USER_HASNT_ROLE_ERRORMSG));
        }
        if (isRemove && userRoles.size() == 1) {
            return Mono.error(new ServerWebInputException(requestedRole.equals(ADMIN_ROLE)
                        ? CANT_DELETE_ADMIN_ERRORMSG : USER_NEEDS_ROLE_ERRORMSG));
        }
        if (!isRemove && userRoles.contains(requestedRole)) {
            return Mono.error(new ServerWebInputException(USER_HAS_ROLE_ALREADY_ERRORMSG));
        }
        if (!isRemove &&
                (requestedRole.equals(ADMIN_ROLE) || userRoles.contains(ADMIN_ROLE))) {
            return Mono.error(new ServerWebInputException(INVALID_ROLE_COMBINE_ERRORMSG));
        }
        return Mono.just(requestedRole);
    }

    /**
     * perform hibernate validation on the annotations of a generic type request.
     * @return empty string, if validation passes, a joined error string containing all joined errors if not.
     */
    private <T> String validateHibernate(T request, Class<T> classOfRequest) {
        Errors errors = new BeanPropertyBindingResult(request, classOfRequest.getName());
        validator.validate(request, errors);
        return errors.hasErrors()
                ? errors.getAllErrors().stream().map(DefaultMessageSourceResolvable::getDefaultMessage)
                    .collect(Collectors.joining(" && "))
                : "";
    }
}