package ua.beengoo.logdo2.api.security;

public interface EncryptionProvider {
    byte[] encrypt(byte[] plain);
    byte[] decrypt(byte[] blob);
}
