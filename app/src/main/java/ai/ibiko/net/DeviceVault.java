package ai.ibiko.net;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Keeps the device secret encrypted with an AES key that lives in the Android
 * Keystore (it never leaves secure hardware). Only ciphertext is in prefs.
 */
public final class DeviceVault {
    private static final String KEY = "ibiko_device_key";
    private static final String PREF = "device_secret_enc";

    private final SharedPreferences prefs;

    public DeviceVault(SharedPreferences prefs) {
        this.prefs = prefs;
    }

    public synchronized String secret() {
        String stored = prefs.getString(PREF, null);
        if (stored == null) {
            return null;
        }
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(all, 0, iv, 0, 12);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (javax.crypto.AEADBadTagException | java.security.UnrecoverableKeyException
                | android.security.keystore.KeyPermanentlyInvalidatedException | ArrayIndexOutOfBoundsException
                | IllegalArgumentException broken) {
            // the key is gone (e.g. after a restore) or the data is damaged: register again
            prefs.edit().remove(PREF).apply();
            return null;
        } catch (Exception passing) {
            // the Keystore is busy or not ready yet (early after boot): keep the secret and try again later;
            // deleting it made the phone register again and lose what the server kept for its old id
            return null;
        }
    }

    public synchronized void store(String secret) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] ct = c.doFinal(secret.getBytes(StandardCharsets.UTF_8));
            byte[] all = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, all, 0, iv.length);
            System.arraycopy(ct, 0, all, iv.length, ct.length);
            prefs.edit().putString(PREF, Base64.encodeToString(all, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            throw new IllegalStateException("cannot protect device secret", e);
        }
    }

    public synchronized void clear() {
        prefs.edit().remove(PREF).apply();
    }

    /** One key for the app: two vaults creating it at once made the second overwrite the first's key. */
    private static synchronized SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (ks.containsAlias(KEY)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        gen.init(new KeyGenParameterSpec.Builder(KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
