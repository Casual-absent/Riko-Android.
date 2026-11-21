package com.casualabsent.riko;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts the provider API key with a non-exportable Android Keystore AES key.
 * Only the ciphertext and GCM IV are stored in app-private preferences.
 */
public final class SecureCredentialStore {
    private static final String PREFS_NAME = "riko_secure_credentials";
    private static final String KEY_CIPHERTEXT = "api_key_ciphertext";
    private static final String KEY_IV = "api_key_iv";
    private static final String KEY_ALIAS = "com.casualabsent.riko.API_KEY";
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SharedPreferences preferences;

    public SecureCredentialStore(Context context) {
        preferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean hasSecret() {
        return preferences.contains(KEY_CIPHERTEXT) && preferences.contains(KEY_IV);
    }

    public synchronized void saveSecret(String secret)
            throws GeneralSecurityException, IOException {
        if (secret == null || secret.trim().isEmpty()) {
            throw new IllegalArgumentException("API key cannot be empty.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] ciphertext = cipher.doFinal(secret.trim().getBytes(StandardCharsets.UTF_8));

        preferences.edit()
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .putString(KEY_CIPHERTEXT,
                        Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .apply();
    }

    public synchronized String getSecret()
            throws GeneralSecurityException, IOException {
        if (!hasSecret()) {
            return "";
        }

        byte[] iv = Base64.decode(preferences.getString(KEY_IV, ""), Base64.NO_WRAP);
        byte[] ciphertext = Base64.decode(
                preferences.getString(KEY_CIPHERTEXT, ""), Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }

    public synchronized void clearSecret() {
        preferences.edit()
                .remove(KEY_IV)
                .remove(KEY_CIPHERTEXT)
                .apply();
    }

    private SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        KeyGenParameterSpec specification = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build();

        generator.init(specification);
        return generator.generateKey();
    }
}
