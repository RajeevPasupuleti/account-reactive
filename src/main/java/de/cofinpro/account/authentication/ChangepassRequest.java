package de.cofinpro.account.authentication;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotEmpty;

/**
 * single property object transporting a new password for /api/auth/changepass endpoint
 */
public record ChangepassRequest(@NotEmpty @JsonProperty("new_password") String newPassword) {
}
