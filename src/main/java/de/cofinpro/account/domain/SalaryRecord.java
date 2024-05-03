package de.cofinpro.account.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static de.cofinpro.account.configuration.AuthenticationConfiguration.EMAIL_REGEX;
import static de.cofinpro.account.configuration.AccountConfiguration.PERIOD_REGEX;

/**
 * immutable record received via http POST and PUT containing a salary record for an employee and month = period
 */
public record SalaryRecord(
        @NotNull @Pattern(regexp = EMAIL_REGEX, message = "Not a valid corporate Email") String employee,
        @NotNull @Pattern(regexp = PERIOD_REGEX, message = "Wrong date!") String period,
        @Min(value = 0, message = "Salary must be non negative!") long salary) {
}