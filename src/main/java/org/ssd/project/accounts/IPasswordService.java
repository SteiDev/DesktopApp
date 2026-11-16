package org.ssd.project.accounts;

public interface IPasswordService {
    String hash(String rawPassword);

    boolean verify(String rawPassword, String storedHash);
}
