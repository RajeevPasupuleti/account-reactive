package de.cofinpro.account.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import static de.cofinpro.account.configuration.AuthenticationConfiguration.EMAIL_REGEX;

/**
 * Immutable request type for the PUT /api/admin/user/access endpoint with validation annotations
 */
public record LockUserToggleRequest(@NotNull
                                    @Pattern(regexp = EMAIL_REGEX, message = "Not a valid corporate Email")
                                    String user,
                                    @NotNull
                                    @Pattern(regexp = "(?i)(un)*lock", message = "operation must be 'lock' or 'unlock'")
                                    String operation) {
}
