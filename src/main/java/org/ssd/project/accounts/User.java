package org.ssd.project.accounts;

public record User(long id, String username, String email, String passwordHash) {
    @Override
    public String toString() {
        return "User[id=" + id + ", username=" + username + ", email=" + email + "]";
    }
}
