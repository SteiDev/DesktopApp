package org.ssd.project.accounts;

public record User(
        long id,
        String username,
        String email,
        String rawPassword
) {}