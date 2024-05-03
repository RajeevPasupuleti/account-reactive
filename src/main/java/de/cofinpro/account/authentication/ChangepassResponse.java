package de.cofinpro.account.authentication;

/**
 * immutable response object to inform a user on a successful password change - for /api/auth/changepass endpoint
 */
public record ChangepassResponse(String email, String status) {
}
