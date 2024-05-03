package de.cofinpro.account.authentication;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationHandlerValidatorUnitTest {

    Validator validator; // JPAUnitValidator not working on immutable Records :-(

    @BeforeEach
    void setup() {
        if (validator == null) {
            try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
                this.validator = validatorFactory.getValidator();
            }
        }
    }

    @ParameterizedTest
    @MethodSource
    void signupInvalidRequests(SignupRequest signupRequest) {
        assertEquals(1, validator.validate(signupRequest).size());
    }

    static Stream<Arguments> signupInvalidRequests() {
        return Stream.of(
                Arguments.of(new SignupRequest("", "Seeler", "t.s@acme.com", "123456")),
                Arguments.of(new SignupRequest(null, "Seeler", "t.s@acme.com", "123456")),
                Arguments.of(new SignupRequest("Toni", "", "t.s@acme.com", "123456")),
                Arguments.of(new SignupRequest("Toni", null, "t.s@acme.com", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", null, "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "juergen.wisskirchen@acme.de", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "juergen.wisskirchen@cofinpro.com", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "t.s@acme.com", "")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "t.s@acme.com", null))
        );
    }

    @ParameterizedTest
    @MethodSource
    void signupValidRequests(SignupRequest signupRequest) {
        assertEquals(0, validator.validate(signupRequest).size());
    }

    static Stream<Arguments> signupValidRequests() {
        return Stream.of(
                Arguments.of(new SignupRequest("T", "S", "t.s@acme.com", "123")),
                Arguments.of(new SignupRequest(" ", " ", "s@acme.com", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "toni.seeleer.ext@ACME.COM", "123456654321")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "t.s@acme.COM", "123456")),
                Arguments.of(new SignupRequest("T.-Martin", "Dr. Seeler", "a@AcMe.com", "123456")),
                Arguments.of(new SignupRequest("Toni", "Seeler", "t.s@acme.com", "=PÖ_#+$§@23"))
        );
    }
}
