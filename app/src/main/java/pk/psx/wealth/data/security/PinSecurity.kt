package pk.psx.wealth.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PinSecurity @Inject constructor() {
    fun createVerifier(pin: String): String {
        validate(pin)
        return sign(pin, key(create = true))
    }

    fun verify(pin: String, expected: String): Boolean = runCatching {
        validate(pin)
        MessageDigest.isEqual(sign(pin, key(create = false)).encodeToByteArray(), expected.encodeToByteArray())
    }.getOrDefault(false)

    fun clearKey() {
        store().apply { load(null); if (containsAlias(KEY_ALIAS)) deleteEntry(KEY_ALIAS) }
    }

    private fun key(create: Boolean): SecretKey {
        val store = store().apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        require(create) { "The device security key is unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setDigests(KeyProperties.DIGEST_SHA256).build())
            generateKey()
        }
    }

    private fun sign(pin: String, key: SecretKey): String = Mac.getInstance("HmacSHA256").run {
        init(key)
        Base64.encodeToString(doFinal(pin.encodeToByteArray()), Base64.NO_WRAP)
    }

    private fun validate(pin: String) {
        require(pin.matches(Regex("[0-9]{4,8}"))) { "PIN must contain 4 to 8 digits" }
    }

    private fun store() = KeyStore.getInstance("AndroidKeyStore")

    private companion object { const val KEY_ALIAS = "psx_wealth_pin_hmac_v1" }
}
