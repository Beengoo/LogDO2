package ua.beengoo.logdo2.plugin.util;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class TokenCryptoTest {
    private static String randomKeyB64() {
        byte[] raw = new byte[32];
        new SecureRandom().nextBytes(raw);
        return Base64.getEncoder().encodeToString(raw);
    }

    @Test
    void roundTripAndRandomIv() {
        var keyB64 = randomKeyB64();
        var crypto = TokenCrypto.fromBase64(keyB64);

        byte[] plain = "hello world".getBytes();
        byte[] c1 = crypto.encrypt(plain);
        byte[] c2 = crypto.encrypt(plain);

        assertFalse(Arrays.equals(c1, c2)); // different IVs
        assertArrayEquals(plain, crypto.decrypt(c1));
        assertArrayEquals(plain, crypto.decrypt(c2));
    }

    @Test
    void invalidKeyLengthThrows() {
        byte[] sixteen = new byte[16];
        String bad = Base64.getEncoder().encodeToString(sixteen);
        assertThrows(IllegalArgumentException.class, () -> TokenCrypto.fromBase64(bad));
    }

    @Test
    void tamperDetection() {
        var crypto = TokenCrypto.fromBase64(randomKeyB64());
        byte[] enc = crypto.encrypt("secret".getBytes());
        enc[enc.length - 1] ^= 0x01; // flip a bit
        assertThrows(RuntimeException.class, () -> crypto.decrypt(enc));
    }
}

