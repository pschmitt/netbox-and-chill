package dev.pschmitt.nyetbox.data.backup

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCryptoTest {
    private val payload = "settings-only payload".toByteArray()

    @Test
    fun unencryptedBackupsRoundTrip() {
        val encoded = SettingsBackupCrypto.encode(payload, null)

        assertFalse(SettingsBackupCrypto.isEncrypted(encoded))
        assertArrayEquals(payload, SettingsBackupCrypto.decode(encoded, null))
    }

    @Test
    fun passwordProtectedBackupsRoundTrip() {
        val encoded = SettingsBackupCrypto.encode(payload, "correct horse battery staple")

        assertTrue(SettingsBackupCrypto.isEncrypted(encoded))
        assertArrayEquals(
            payload,
            SettingsBackupCrypto.decode(encoded, "correct horse battery staple"),
        )
    }

    @Test(expected = SettingsBackupPasswordRequiredException::class)
    fun encryptedBackupRequiresPassword() {
        SettingsBackupCrypto.decode(SettingsBackupCrypto.encode(payload, "secret"), null)
    }

    @Test(expected = SettingsBackupWrongPasswordException::class)
    fun encryptedBackupRejectsWrongPassword() {
        SettingsBackupCrypto.decode(SettingsBackupCrypto.encode(payload, "secret"), "wrong")
    }
}
