package de.cofinpro.account.admin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class AdminHandlerValidationUnitTest {

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
    void whenValidRoleToggleRequest_validateSucceeds(RoleToggleRequest roleToggleRequest) {
        assertEquals(0, validator.validate(roleToggleRequest).size());
    }

    static Stream<Arguments> whenValidRoleToggleRequest_validateSucceeds() {
        return Stream.of(
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "accountant", "grant")),
                Arguments.of(new RoleToggleRequest("h.w.ext_4@acme.com", "ADMINSTRATOR", "Grant")),
                Arguments.of(new RoleToggleRequest("w@acme.com", "User", "GRANT")),
                Arguments.of(new RoleToggleRequest("w@acme.com", "auditor", "GRANT")),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "accoun", "remove")),
                Arguments.of(new RoleToggleRequest("h.W@acMe.cOm", "u", "REMOVE")),
                Arguments.of(new RoleToggleRequest("h.w@ACME.com", "USER", "Remove"))
        );
    }
    
    @ParameterizedTest
    @MethodSource
    void whenInvalidRoleToggleRequest_validateFails(RoleToggleRequest roleToggleRequest) {
        assertEquals(1, validator.validate(roleToggleRequest).size());
    }

    static Stream<Arguments> whenInvalidRoleToggleRequest_validateFails() {
        return Stream.of(
                Arguments.of(new RoleToggleRequest("h.w@acme.comm", "user", "grant")),
                Arguments.of(new RoleToggleRequest("", "user", "grant")),
                Arguments.of(new RoleToggleRequest(null, "user", "grant")),
                Arguments.of(new RoleToggleRequest("h.wacme.com", "user", "grant")),
                Arguments.of(new RoleToggleRequest("w@acme.com", null, "grant")),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "", "grant")),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "user", "grand")),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "user", "")),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "user", null)),
                Arguments.of(new RoleToggleRequest("h.w@acme.com", "user", "revoke"))
        );
    }


    @ParameterizedTest
    @MethodSource
    void whenInvalidLockUserToggleRequest_validateFails(LockUserToggleRequest lockUserToggleRequest) {
        assertEquals(1, validator.validate(lockUserToggleRequest).size());
    }

    static Stream<Arguments> whenInvalidLockUserToggleRequest_validateFails() {
        return Stream.of(
                Arguments.of(new LockUserToggleRequest("h.w@acme.comm", "lock")),
                Arguments.of(new LockUserToggleRequest("", "UNLOCK")),
                Arguments.of(new LockUserToggleRequest(null,  "unlock")),
                Arguments.of(new LockUserToggleRequest("h.w@acme.com",  "loc")),
                Arguments.of(new LockUserToggleRequest("w@acme.com",  "UNLOCK ")),
                Arguments.of(new LockUserToggleRequest("h.w@acme.com",  "")),
                Arguments.of(new LockUserToggleRequest("h.w@acme.com", null)),
                Arguments.of(new LockUserToggleRequest("h.w@acme.com",  "sth"))
        );
    }
    @ParameterizedTest
    @MethodSource
    void whenValidLockUserToggleRequest_validateSucceeds(LockUserToggleRequest lockUserToggleRequest) {
        assertEquals(0, validator.validate(lockUserToggleRequest).size());
    }

    static Stream<Arguments> whenValidLockUserToggleRequest_validateSucceeds() {
        return Stream.of(
                Arguments.of(new LockUserToggleRequest("h.w@acme.com","lock")),
                Arguments.of(new LockUserToggleRequest("h.w.ext_4@acme.com", "Lock")),
                Arguments.of(new LockUserToggleRequest("w@acme.com", "UNLOCK")),
                Arguments.of(new LockUserToggleRequest("h.w@acme.com", "unLock")),
                Arguments.of(new LockUserToggleRequest("h.W@acMe.cOm", "unlock")),
                Arguments.of(new LockUserToggleRequest("h.w@ACME.com",  "LOCK"))
        );
    }
}