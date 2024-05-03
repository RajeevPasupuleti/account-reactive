package de.cofinpro.account.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AccountHandlerValidationUnitTest {

    Validator validator;

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
    void whenValidSalaryRecord_validateSucceeds(SalaryRecord salaryRecord) {
        assertEquals(0, validator.validate(salaryRecord).size());
    }

    static Stream<Arguments> whenValidSalaryRecord_validateSucceeds() {
        return Stream.of(
                Arguments.of(new SalaryRecord("h.w@acme.com", "01-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w.ext_4@acme.com", "01-2021", 1000)),
                Arguments.of(new SalaryRecord("w@acme.com", "01-1021", 0)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "12-2021", 1)),
                Arguments.of(new SalaryRecord("h.W@acMe.cOm", "11-9999", 10000000000L)),
                Arguments.of(new SalaryRecord("h.w@ACME.com", "10-1000", 100_000_000_000_00L)),
                Arguments.of(new SalaryRecord("h.w@acme.COM", "09-5555", 1000))
        );
    }
    @ParameterizedTest
    @MethodSource
    void whenInvalidSalaryRecord_validateFails(SalaryRecord salaryRecord) {
        assertEquals(1, validator.validate(salaryRecord).size());
    }

    @SuppressWarnings("ConstantConditions")
    static Stream<Arguments> whenInvalidSalaryRecord_validateFails() {
        return Stream.of(
                Arguments.of(new SalaryRecord("h.w@acme.comm", "01-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.de", "01-2021", 1000)),
                Arguments.of(new SalaryRecord("h.wacme.com", "01-2021", 1000)),
                Arguments.of(new SalaryRecord("w@acme.com", "00-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "21-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "13-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "12-0021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "120021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "1a-2021", 1000)),
                Arguments.of(new SalaryRecord("h.w@acme.com", "12-3021", -1000))
        );
    }
}