package ua.beengoo.logdo2.plugin.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public record EncryptionManager(SecretKey key) {
    private static final String ALG = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;
    private static final SecureRandom RND = new SecureRandom();

    public static EncryptionManager fromBase64(String base64) {
        byte[] raw = Base64.getDecoder().decode(base64);
        if (raw.length != 32) throw new IllegalArgumentException("tokenEncryptionKeyBase64 must be 32 bytes");
        return new EncryptionManager(new SecretKeySpec(raw, "AES"));
    }

    public byte[] encrypt(byte[] plain) {
        byte[] iv = new byte[IV_LEN];
        RND.nextBytes(iv);
        try {
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] enc = c.doFinal(plain);
            byte[] out = new byte[IV_LEN + enc.length];
            System.arraycopy(iv, 0, out, 0, IV_LEN);
            System.arraycopy(enc, 0, out, IV_LEN, enc.length);
            return out;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] decrypt(byte[] blob) {
        if (blob.length <= IV_LEN) throw new IllegalArgumentException("bad blob");
        byte[] iv = new byte[IV_LEN];
        System.arraycopy(blob, 0, iv, 0, IV_LEN);
        byte[] enc = new byte[blob.length - IV_LEN];
        System.arraycopy(blob, IV_LEN, enc, 0, enc.length);
        try {
            Cipher c = Cipher.getInstance(ALG);
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(enc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
