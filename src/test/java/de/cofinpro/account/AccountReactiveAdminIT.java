package de.cofinpro.account;

import de.cofinpro.account.admin.LockUserToggleRequest;
import de.cofinpro.account.admin.RoleToggleRequest;
import de.cofinpro.account.admin.UserDeletedResponse;
import de.cofinpro.account.authentication.SignupRequest;
import de.cofinpro.account.authentication.SignupResponse;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static de.cofinpro.account.AccountReactiveAuthenticationIT.signup;
import static de.cofinpro.account.configuration.AdminConfiguration.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(properties = { "spring.r2dbc.url=r2dbc:h2:file://././src/test/resources/data/admin_test_db" })
@AutoConfigureWebTestClient
class AccountReactiveAdminIT {

    static boolean usersSignedUp = false;

    @Autowired
    WebTestClient webClient;

    static final Path TEST_DB_PATH = Path.of("./src/test/resources/data/admin_test_db.mv.db");

    @BeforeAll
    static void dbSetup() throws IOException {
        Files.deleteIfExists(TEST_DB_PATH);
        Files.copy(Path.of("./src/test/resources/data/account_template.mv.db"), TEST_DB_PATH);
    }

    @BeforeEach
    void setup() {
        if (!usersSignedUp) {
            signup(webClient, new SignupRequest("system", "admin", "admin@acme.com", "attminattmin"));
            signup(webClient, new SignupRequest("Hans", "Wurst", "hw@acme.com", "useruseruser"));
            usersSignedUp = true;
        }
    }

    @Test
    void whenAdminAuthenticatedDeleteNoPath_Then404() {
        webClient.delete().uri("/api/admin/user")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void whenAdminAuthenticatedInvalidEmail_Then400() {
        webClient.delete().uri("/api/admin/user/a@acmecom")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .json("{\"message\": \"Invalid user email given: 'a@acmecom'!\"}");
    }

    @Test
    void whenAdminAuthenticatedUserNotExists_Then404() {
        webClient.delete().uri("/api/admin/user/not_there@acme.com")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .json("{\"message\": \"" + USER_NOT_FOUND_ERRORMSG + "\"}");
    }

    @Test
    void whenAdminAuthenticatedDeleteAdmin_Then400() {
        webClient.delete().uri("/api/admin/user/admin@acme.com")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .json("{\"message\": \"" + CANT_DELETE_ADMIN_ERRORMSG + "\"}");
    }

    @Test
    void whenAdminAuthenticatedDeleteUser_ThenOkAndDeleteStatusReturned() {
        signup(webClient, new SignupRequest("Anton", "Wurst", "aw@acme.com", "useruseruser"));
        webClient.delete().uri("/api/admin/user/aw@acme.com")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(UserDeletedResponse.class)
                .value(UserDeletedResponse::user, equalTo("aw@acme.com"))
                .value(UserDeletedResponse::status, equalTo(DELETED_SUCCESSFULLY));
    }

    @Test
    void whenAdminAuthenticated_ThenUsersReturned() {
        webClient.get().uri("/api/admin/user")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SignupResponse[].class)
                .value(list -> list[0].id(), equalTo(1L))
                .value(list -> list[1].id(), equalTo(2L))
                .value(list -> list[0].roles(), equalTo(List.of("ROLE_ADMINISTRATOR")))
                .value(list -> list[1].roles(), equalTo(List.of("ROLE_USER")))
                .value(list -> list[0].email(), equalTo("admin@acme.com"))
                .value(list -> list[1].email(), equalTo("hw@acme.com"))
                .value(list -> list[0].name(), equalTo("system"));
    }

    @Test
    void whenUserRoleAuthenticated_ThenAdminEndpointsReturn403() {
        webClient.get().uri("/api/admin/user")
                .headers(headers -> headers.setBasicAuth("hw@acme.com", "useruseruser"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .json("{\"message\": \"Access Denied!\"}");
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("hw@acme.com", "useruseruser"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .json("{\"message\": \"Access Denied!\"}");
        webClient.delete().uri("/api/admin/user/a@acme.com")
                .headers(headers -> headers.setBasicAuth("hw@acme.com", "useruseruser"))
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .json("{\"message\": \"Access Denied!\"}");
    }

    @Test
    void whenInvalidPutRole_Then400AndAllValidationErrors() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("acct@acmecom", "ACCOUNTANT", "granD"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(containsString("operation needs 'grant' or 'remove'"))
                .jsonPath("$.message").value(containsString("&&"))
                .jsonPath("$.message").value(containsString("Not a valid corporate Email"));
    }

    @Test
    void whenPutRoleInvalidUser_Then404() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("acct@acme.com", "ACCOUNTANT", "grant"))
                .exchange().expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(equalTo(USER_NOT_FOUND_ERRORMSG));
    }

    @Test
    void whenPutRoleInvalidRole_Then404() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("hw@acme.com", "ACCOUNT", "grant"))
                .exchange().expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.message").value(equalTo(ROLE_NOT_FOUND_ERRORMSG));
    }

    @Test
    void whenPutRoleUserHasntRole_Then400() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("admin@acme.com", "ACCOUNTANT", "remove"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(USER_HASNT_ROLE_ERRORMSG));
    }

    @Test
    void whenPutRoleUserHasRoleAlready_Then400() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("hw@acme.com", "user", "grant"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(USER_HAS_ROLE_ALREADY_ERRORMSG));
    }

    @Test
    void whenPutRoleRemoveLastRole_Then400() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("hw@acme.com", "user", "remove"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(USER_NEEDS_ROLE_ERRORMSG));
    }

    @Test
    void whenPutRoleUserGrantAdministrator_Then400() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("hw@acme.com", "ADMINISTRATOR", "GRANT"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(INVALID_ROLE_COMBINE_ERRORMSG));
    }

    @Test
    void whenPutRoleRemoveAdministrator_Then400() {
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("admin@acme.com", "ADMINISTRATOR", "remove"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(CANT_DELETE_ADMIN_ERRORMSG));
    }

    @Test
    void whenPutRoleValid_Then200AndRolesReturned() {
        signup(webClient, new SignupRequest("Anton", "A", "a@acme.com", "useruseruser"));
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("a@acme.com", "accountant", "GRANT"))
                .exchange().expectStatus().isOk()
                .expectBody(SignupResponse.class)
                .value(SignupResponse::email, equalTo("a@acme.com"))
                .value(SignupResponse::roles, containsInAnyOrder("ROLE_USER", "ROLE_ACCOUNTANT"));
        webClient.put().uri("/api/admin/user/role")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new RoleToggleRequest("a@acme.com", "ACCOUNTANT", "Remove"))
                .exchange().expectStatus().isOk()
                .expectBody(SignupResponse.class)
                .value(SignupResponse::roles, equalTo(List.of("ROLE_USER")));
    }

    @Test
    void whenPutLockAdministrator_Then400() {
        webClient.put().uri("/api/admin/user/access")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new LockUserToggleRequest("admin@acme.com", "lock"))
                .exchange().expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(CANT_LOCK_ADMIN_ERRORMSG));
    }

    @Test
    void whenPutLockAndUnlockUser_Then200AndUserLockedAndUnlocked() {
        webClient.put().uri("/api/admin/user/access")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new LockUserToggleRequest("hw@acme.com", "lock"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .json("{\"status\": \"User hw@acme.com locked!\"}");
        webClient.get().uri("/api/empl/payment")
                .headers(headers -> headers.setBasicAuth("hw@acme.com", "useruseruser"))
                .exchange().expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.message").value(equalTo("User account is locked"));
        webClient.put().uri("/api/admin/user/access")
                .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                .bodyValue(new LockUserToggleRequest("hw@acme.com", "unlock"))
                .exchange().expectStatus().isOk()
                .expectBody()
                .json("{\"status\": \"User hw@acme.com unlocked!\"}");
        webClient.get().uri("/api/empl/payment")
                .headers(headers -> headers.setBasicAuth("hw@acme.com", "useruseruser"))
                .exchange().expectStatus().isOk();
    }
}