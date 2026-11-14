package com.bova.app

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Date
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class BovaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
    }
}

/** Menyimpan error fatal ke berkas lokal supaya bisa dibaca/dibagikan dari Pengaturan. */
object CrashLogger {
    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                File(app.filesDir, "crash.log").writeText("${Date()}\nThread: ${t.name}\n${Log.getStackTraceString(e)}")
            } catch (_: Exception) {}
            previous?.uncaughtException(t, e)
        }
    }
    fun read(ctx: Context): String? = try {
        File(ctx.filesDir, "crash.log").takeIf { it.exists() }?.readText()?.take(6000)
    } catch (_: Exception) { null }
    fun clear(ctx: Context) { try { File(ctx.filesDir, "crash.log").delete() } catch (_: Exception) {} }
}

/** Status kunci aplikasi (dibaca Compose). */
object LockState {
    var locked by mutableStateOf(false)
    var lastBackground = 0L
    private var suppress = false
    /** Panggil sebelum membuka aktivitas luar (galeri, izin, berkas) agar tidak dianggap meninggalkan aplikasi. */
    fun external() { suppress = true }
    fun consumeSuppress(): Boolean { val s = suppress; suppress = false; return s }
}

object Crypto {
    private const val KS = "AndroidKeyStore"
    private const val ALIAS = "bova_secret_key"
    private val rnd = SecureRandom()

    fun randomBytes(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KS).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KS)
        kg.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build()
        )
        return kg.generateKey()
    }

    /** Enkripsi dengan kunci Android Keystore (tidak bisa diekspor dari perangkat). */
    fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(b64: String): String? = try {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    } catch (_: Exception) { null }

    private fun pbkdf(password: CharArray, salt: ByteArray, iter: Int, bits: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(PBEKeySpec(password, salt, iter, bits)).encoded

    /** Enkripsi teks backup dengan kata sandi (PBKDF2 + AES-GCM). */
    fun encryptWithPassword(text: String, password: String): String {
        val salt = randomBytes(16); val iv = randomBytes(12); val iter = 120_000
        val key = SecretKeySpec(pbkdf(password.toCharArray(), salt, iter, 256), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val data = c.doFinal(text.toByteArray(Charsets.UTF_8))
        return JSONObject().put("app", "BOVA-ENC").put("v", 1).put("iter", iter)
            .put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
            .put("data", Base64.encodeToString(data, Base64.NO_WRAP)).toString()
    }

    fun isEncryptedBackup(text: String): Boolean = try { JSONObject(text).optString("app") == "BOVA-ENC" } catch (_: Exception) { false }

    fun decryptWithPassword(text: String, password: String): String? = try {
        val o = JSONObject(text)
        val salt = Base64.decode(o.getString("salt"), Base64.NO_WRAP)
        val iv = Base64.decode(o.getString("iv"), Base64.NO_WRAP)
        val data = Base64.decode(o.getString("data"), Base64.NO_WRAP)
        val key = SecretKeySpec(pbkdf(password.toCharArray(), salt, o.optInt("iter", 120_000), 256), "AES")
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        String(c.doFinal(data), Charsets.UTF_8)
    } catch (_: Exception) { null }

    fun hashPin(pin: String, salt: ByteArray): String =
        Base64.encodeToString(pbkdf(pin.toCharArray(), salt, 60_000, 256), Base64.NO_WRAP)

    fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(), b.toByteArray())
}

object Biometrics {
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun available(ctx: Context): Boolean =
        BiometricManager.from(ctx).canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val executor = ContextCompat.getMainExecutor(activity)
        val cb = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onError(errString.toString()) }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka BOVA")
            .setSubtitle("Verifikasi identitasmu")
            .setAllowedAuthenticators(AUTH)
            .build()
        try { BiometricPrompt(activity, executor, cb).authenticate(info) } catch (e: Exception) { onError(e.message ?: "Gagal membuka verifikasi") }
    }
}

/** Mode Jangan Ganggu selama sesi fokus (butuh izin akses kebijakan notifikasi dari pengguna). */
object Dnd {
    private fun nm(ctx: Context) = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    fun hasAccess(ctx: Context): Boolean = nm(ctx).isNotificationPolicyAccessGranted

    fun enable(ctx: Context, s: AppSettings) {
        if (!s.focusDnd || !hasAccess(ctx)) return
        val m = nm(ctx)
        if (s.dndPrevious < 0) s.dndPrevious = m.currentInterruptionFilter
        try { m.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY) } catch (_: SecurityException) {}
    }

    fun restore(ctx: Context, s: AppSettings) {
        val prev = s.dndPrevious
        if (prev >= 0 && hasAccess(ctx)) {
            try { nm(ctx).setInterruptionFilter(prev) } catch (_: SecurityException) {}
        }
        s.dndPrevious = -1
    }
}
