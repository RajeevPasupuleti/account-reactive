package de.cofinpro.account;

import de.cofinpro.account.admin.RoleToggleRequest;
import de.cofinpro.account.authentication.SignupRequest;
import de.cofinpro.account.domain.SalaryRecord;
import de.cofinpro.account.domain.SalaryResponse;
import de.cofinpro.account.domain.StatusResponse;
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
import static de.cofinpro.account.configuration.AccountConfiguration.*;
import static org.hamcrest.Matchers.equalTo;

@SpringBootTest(properties = { "spring.r2dbc.url=r2dbc:h2:file://././src/test/resources/data/domain_test_db" })
@AutoConfigureWebTestClient
class AccountReactiveDomainIT {

    @Autowired
    WebTestClient webClient;

    static boolean usersSignedUp = false;

    static final Path TEST_DB_PATH = Path.of("./src/test/resources/data/domain_test_db.mv.db");

    @BeforeAll
    static void dbSetup() throws IOException {
        Files.deleteIfExists(TEST_DB_PATH);
        Files.copy(Path.of("./src/test/resources/data/account_template.mv.db"), TEST_DB_PATH);
    }

    @BeforeEach
    void setup() {
        if (!usersSignedUp) {
            signup(webClient, new SignupRequest("system", "admin", "admin@acme.com", "attminattmin"));
            signup(webClient, new SignupRequest("Accountant", "role", "acct@acme.com", "acctacctacct"));
            webClient.put().uri("/api/admin/user/role")
                    .headers(headers -> headers.setBasicAuth("admin@acme.com", "attminattmin"))
                    .bodyValue(new RoleToggleRequest("acct@acme.com", "ACCOUNTANT", "grant"))
                            .exchange().expectStatus().isOk();
            signup(webClient, new SignupRequest("Pete", "Doe", "p.d@acme.com", "123456789012"));
            usersSignedUp = true;
        }
    }

    @Test
    void whenInvalidSalaryRequest_Then400() {
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("p.d@acme.com", "13-2022", 100))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo("Record 0: Wrong date!"))
                .jsonPath("$.path").value(equalTo("/api/acct/payments"));
    }

    @Test
    void whenInvalidPutSalaryRequest_Then400() {
        webClient.put().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("p.d@acme.com", "13-2022", 5000))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo("Wrong date!"))
                .jsonPath("$.path").value(equalTo("/api/acct/payments"));
        webClient.put().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("notuser.d@acme.com", "06-2022", 5000))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(NO_SUCH_SALES_RECORD_ERRORMSG));
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("p.d@acme.com", "06-2022", 5000))
                .exchange()
                .expectStatus().isOk();
        webClient.put().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("p.d@acme.com", "05-2022", 5000))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(NO_SUCH_SALES_RECORD_ERRORMSG));
    }

    @Test
    void whenValidSalaryRequest_ThenOkAndStatusResponseReturned() {
        signup(webClient, new SignupRequest("Alf", "Doe", "a.d@acme.com", "123456789012"));
        signup(webClient, new SignupRequest("Gary", "Doe", "g.d@acme.com", "123456789012"));
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("a.d@acme.com", "07-2022", 10000),
                        new SalaryRecord("g.d@acme.com", "06-2022", 5000)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StatusResponse.class)
                .value(StatusResponse::status, equalTo("2 records " + ADDED_SUCCESSFULLY));
    }

    @Test
    void whenValidSalaryRequestUserNotExists_Then400AndErrorMessage() {
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("not.there@acme.com", "06-2022", 5000)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo("Record 0: " + NO_SUCH_EMPLOYEE_ERRORMSG));
    }

    @Test
    void whenValidSalaryRequestSalaryExistsInDb_Then400AndErrorMessage() {
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("p.d@acme.com", "04-2022", 5000)))
                .exchange()
                .expectStatus().isOk();
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("p.d@acme.com", "05-2022", 5000),
                        new SalaryRecord("p.d@acme.com", "04-2022", 5000)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo("Record 1: " + RECORD_ALREADY_EXISTS_ERRORMSG));
    }

    @Test
    void whenDuplicateRecordsInRequest_Then400AndErrorMessage() {
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("p.d@acme.com", "01-2023", 5000),
                        new SalaryRecord("p.d@acme.com", "02-2023", 1000),
                        new SalaryRecord("p.d@acme.com", "03-2023", 2000),
                        new SalaryRecord("p.d@acme.com", "05-2023", 4000),
                        new SalaryRecord("p.d@acme.com", "03-2023", 5000)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(DUPLICATE_RECORDS_ERRORMSG));
    }

    @Test
    void whenTriplicateRecordsInRequest_Then400AndErrorMessage() {
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("p.d@acme.com", "03-2021", 5000),
                        new SalaryRecord("p.d@acme.com", "02-2021", 1000),
                        new SalaryRecord("p.d@acme.com", "03-2021", 2000),
                        new SalaryRecord("p.d@acme.com", "05-2021", 4000),
                        new SalaryRecord("p.d@acme.com", "03-2021", 5000)))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message").value(equalTo(DUPLICATE_RECORDS_ERRORMSG));
    }

    @Test
    void whenValidPutSalaryRequest_ThenOkAndStatusResponse() {
        signup(webClient, new SignupRequest("Hans", "Doe", "h.d@acme.com", "123456789012"));
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("h.d@acme.com", "05-2022", 2000),
                        new SalaryRecord("h.d@acme.com", "07-2022", 2000),
                        new SalaryRecord("h.d@acme.com", "06-2022", 2000)))
                .exchange()
                .expectStatus().isOk();
        webClient.put().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(new SalaryRecord("h.d@acme.com", "06-2022", 123456))
                .exchange()
                .expectStatus().isOk()
                .expectBody(StatusResponse.class)
                .value(StatusResponse::status, equalTo(UPDATED_SUCCESSFULLY));
    }


    @Test
    void whenGetPayment_ThenSalaryResponseReturned() {
        signup(webClient, new SignupRequest("Jan", "Doe", "j.d@acme.com", "123456789012"));
        webClient.post().uri("/api/acct/payments")
                .headers(headers -> headers.setBasicAuth("acct@acme.com", "acctacctacct"))
                .bodyValue(List.of(
                        new SalaryRecord("j.d@acme.com", "05-2022", 5010),
                        new SalaryRecord("j.d@acme.com", "07-2022", 7025),
                        new SalaryRecord("j.d@acme.com", "06-2021", 6000)))
                .exchange()
                .expectStatus().isOk();
        webClient.get().uri(uri -> uri.path("/api/empl/payment").queryParam("period", "07-2022").build())
                .headers(headers -> headers.setBasicAuth("j.d@acme.com", "123456789012"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SalaryResponse[].class)
                .value(list -> list[0].salary(), equalTo("70 dollar(s) 25 cent(s)"))
                .value(list -> list[0].name(), equalTo("Jan"))
                .value(list -> list[0].lastname(), equalTo("Doe"))
                .value(list -> list[0].period(), equalTo("July-2022"));
        webClient.get().uri("/api/empl/payment")
                .headers(headers -> headers.setBasicAuth("j.d@acme.com", "123456789012"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(SalaryResponse[].class)
                .value(list -> list[1].salary(), equalTo("50 dollar(s) 10 cent(s)"))
                .value(list -> list[0].salary(), equalTo("60 dollar(s) 00 cent(s)"))
                .value(list -> list[2].salary(), equalTo("70 dollar(s) 25 cent(s)"))
                .value(list -> list[0].name(), equalTo("Jan"))
                .value(list -> list[0].lastname(), equalTo("Doe"))
                .value(list -> list[1].period(), equalTo("May-2022"))
                .value(list -> list[2].period(), equalTo("July-2022"))
                .value(list -> list[0].period(), equalTo("June-2021"));
        webClient.get().uri(uri -> uri.path("/api/empl/payment").queryParam("period", "7-2022").build())
                .headers(headers -> headers.setBasicAuth("j.d@acme.com", "123456789012"))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.message")
                .value(equalTo("Wrong Date: Use mm-yyyy format!"));
    }
}
