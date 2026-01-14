package ua.beengoo.logdo2.api.spi.providers;

public interface EncryptionProvider {
    byte[] encrypt(byte[] plain);
    byte[] decrypt(byte[] blob);
}
