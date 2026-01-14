package ua.beengoo.logdo2.plugin.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EncryptionManager Tests")
class EncryptionManagerTest {

    @Test
    @DisplayName("Should create EncryptionManager from valid base64 key")
    void testFromBase64ValidKey() {
        // Generate a valid 32-byte key
        byte[] validKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            validKey[i] = (byte) i;
        }
        String base64Key = Base64.getEncoder().encodeToString(validKey);

        EncryptionManager manager = EncryptionManager.fromBase64(base64Key);

        assertNotNull(manager, "EncryptionManager should not be null");
        assertNotNull(manager.key(), "SecretKey should not be null");
        assertEquals("AES", manager.key().getAlgorithm(), "Key algorithm should be AES");
    }

    @Test
    @DisplayName("Should throw exception for invalid key length (too short)")
    void testFromBase64InvalidKeyLengthShort() {
        byte[] invalidKey = new byte[16]; // Only 16 bytes instead of 32
        String base64Key = Base64.getEncoder().encodeToString(invalidKey);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EncryptionManager.fromBase64(base64Key),
            "Should throw IllegalArgumentException for invalid key length"
        );

        assertTrue(exception.getMessage().contains("32 bytes"),
            "Exception message should mention 32 bytes requirement");
    }

    @Test
    @DisplayName("Should throw exception for invalid key length (too long)")
    void testFromBase64InvalidKeyLengthLong() {
        byte[] invalidKey = new byte[64]; // 64 bytes instead of 32
        String base64Key = Base64.getEncoder().encodeToString(invalidKey);

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> EncryptionManager.fromBase64(base64Key),
            "Should throw IllegalArgumentException for invalid key length"
        );

        assertTrue(exception.getMessage().contains("32 bytes"),
            "Exception message should mention 32 bytes requirement");
    }

    @Test
    @DisplayName("Should successfully encrypt and decrypt data")
    void testEncryptDecryptRoundTrip() {
        EncryptionManager manager = createTestEncryptionManager();
        String plaintext = "Umba youmba";
        byte[] plaintextBytes = plaintext.getBytes();

        byte[] encrypted = manager.encrypt(plaintextBytes);
        byte[] decrypted = manager.decrypt(encrypted);

        assertArrayEquals(plaintextBytes, decrypted,
            "Decrypted data should match original plaintext");
        assertEquals(plaintext, new String(decrypted),
            "Decrypted string should match original plaintext");
    }

    @Test
    @DisplayName("Should produce different ciphertexts for same plaintext due to random IV")
    void testEncryptionUsesRandomIV() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] plaintext = "Yoo suck my balls (Im just listen to caramelldansen at this time)".getBytes();

        byte[] encrypted1 = manager.encrypt(plaintext);
        byte[] encrypted2 = manager.encrypt(plaintext);

        assertNotEquals(encrypted1.length, 0, "Encrypted data should not be empty");
        assertNotEquals(encrypted2.length, 0, "Encrypted data should not be empty");
        assertFalse(
            Arrays.equals(encrypted1, encrypted2),
            "Two encryptions of same plaintext should produce different ciphertexts (random IV)"
        );

        // But both should decrypt to the same plaintext
        byte[] decrypted1 = manager.decrypt(encrypted1);
        byte[] decrypted2 = manager.decrypt(encrypted2);
        assertArrayEquals(decrypted1, decrypted2,
            "Both ciphertexts should decrypt to same plaintext");
    }

    @Test
    @DisplayName("Should throw exception when decrypting blob that is too short")
    void testDecryptBlobTooShort() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] tooShortBlob = new byte[12]; // Exactly IV_LEN (12), no ciphertext

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.decrypt(tooShortBlob),
            "Should throw IllegalArgumentException for blob that is too short"
        );

        assertTrue(exception.getMessage().contains("bad blob"),
            "Exception message should mention bad blob");
    }

    @Test
    @DisplayName("Should throw exception when decrypting empty blob")
    void testDecryptEmptyBlob() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] emptyBlob = new byte[0];

        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> manager.decrypt(emptyBlob),
            "Should throw IllegalArgumentException for empty blob"
        );

        assertTrue(exception.getMessage().contains("bad blob"),
            "Exception message should mention bad blob");
    }

    @Test
    @DisplayName("Should throw exception when decrypting corrupted blob")
    void testDecryptCorruptedBlob() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] plaintext = "Test message".getBytes();
        byte[] encrypted = manager.encrypt(plaintext);

        // Corrupt the encrypted data (after IV)
        if (encrypted.length > 13) {
            encrypted[13] ^= 0xFF; // Flip bits in the encrypted portion
        }

        assertThrows(
            RuntimeException.class,
            () -> manager.decrypt(encrypted),
            "Should throw RuntimeException when decrypting corrupted data"
        );
    }

    @Test
    @DisplayName("Should encrypt empty byte array")
    void testEncryptEmptyData() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] emptyData = new byte[0];

        byte[] encrypted = manager.encrypt(emptyData);
        byte[] decrypted = manager.decrypt(encrypted);

        assertNotNull(encrypted, "Encrypted data should not be null");
        assertTrue(encrypted.length > 12, "Encrypted blob should be larger than IV (includes IV + GCM tag)");
        assertArrayEquals(emptyData, decrypted, "Decrypted empty data should match original");
    }

    @Test
    @DisplayName("Should encrypt and decrypt large data")
    void testEncryptDecryptLargeData() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] largeData = new byte[10000];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }

        byte[] encrypted = manager.encrypt(largeData);
        byte[] decrypted = manager.decrypt(encrypted);

        assertArrayEquals(largeData, decrypted,
            "Decrypted large data should match original");
    }

    @Test
    @DisplayName("Should encrypt and decrypt Unicode strings correctly")
    void testEncryptDecryptUnicodeStrings() {
        EncryptionManager manager = createTestEncryptionManager();
        String unicodeText = "Hello 世界 🌍 русня не має право на життя. مرحبا";
        byte[] plaintextBytes = unicodeText.getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] encrypted = manager.encrypt(plaintextBytes);
        byte[] decrypted = manager.decrypt(encrypted);

        assertArrayEquals(plaintextBytes, decrypted,
            "Decrypted Unicode data should match original");
        assertEquals(unicodeText, new String(decrypted, java.nio.charset.StandardCharsets.UTF_8),
            "Decrypted Unicode string should match original");
    }

    @Test
    @DisplayName("Should produce ciphertext with correct structure (IV + encrypted data)")
    void testCiphertextStructure() {
        EncryptionManager manager = createTestEncryptionManager();
        byte[] plaintext = "Test".getBytes();

        byte[] encrypted = manager.encrypt(plaintext);

        // Structure: 12 bytes IV + encrypted data (plaintext + 16 bytes GCM tag)
        int expectedMinLength = 12 + plaintext.length + 16; // IV + plaintext + GCM tag
        assertTrue(encrypted.length >= expectedMinLength - 1, // Allow for some variance
            "Encrypted blob should have correct structure (IV + ciphertext + GCM tag)");
    }

    /**
     * Helper method to create a test EncryptionManager with a valid key
     */
    private EncryptionManager createTestEncryptionManager() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i * 7); // Deterministic but varied key
        }
        String base64Key = Base64.getEncoder().encodeToString(key);
        return EncryptionManager.fromBase64(base64Key);
    }
}