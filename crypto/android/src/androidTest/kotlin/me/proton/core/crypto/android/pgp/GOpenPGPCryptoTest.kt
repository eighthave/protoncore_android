/*
 * Copyright (c) 2020 Proton Technologies AG
 * This file is part of Proton Technologies AG and ProtonCore.
 *
 * ProtonCore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ProtonCore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with ProtonCore.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.proton.core.crypto.android.pgp

import android.util.Base64
import com.proton.gopenpgp.crypto.Crypto
import me.proton.core.crypto.common.pgp.VerificationStatus
import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.crypto.common.simple.use
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class GOpenPGPCryptoTest {

    private val crypto = GOpenPGPCrypto()

    @Test
    fun getPublicKeyFromPrivateKey() {
        // GIVEN
        val privateKey = TestKey.privateKey
        val publicKey = TestKey.privateKeyPublicKey

        // WHEN
        val publicKeyFromPrivateKey = crypto.getPublicKey(privateKey)

        val expectedPublicKey = Crypto.newKeyFromArmored(publicKey)
        val actualPublicKey = Crypto.newKeyFromArmored(publicKeyFromPrivateKey)

        // THEN
        assertEquals(
            expected = expectedPublicKey.fingerprint,
            actual = actualPublicKey.fingerprint
        )
        assertFalse(actualPublicKey.isPrivate)
        assertFails { actualPublicKey.isLocked }
    }

    @Test
    fun getFingerprintFromPrivateKey() {
        // GIVEN
        val privateKey = TestKey.privateKey

        // WHEN
        val fingerprintFromPrivateKey = crypto.getFingerprint(privateKey)

        // THEN
        assertEquals(
            expected = TestKey.privateKeyFingerprint,
            actual = fingerprintFromPrivateKey
        )
    }

    @Test
    fun getPassphraseFromPasswordAndSalt() {
        // GIVEN
        val password = "password".toByteArray()
        val encodedSalt = Base64.encodeToString(ByteArray(16), Base64.DEFAULT)

        val expectedPassphrase = "7zqaLmaKtn.i7IjPfuPGY2Ah/mNM6Sy".toByteArray()

        // WHEN
        crypto.getPassphrase(password, encodedSalt).use { generatedPassphrase ->
            // THEN
            assertTrue(generatedPassphrase.array.contentEquals(expectedPassphrase))
        }
    }

    @Test
    fun unlockPrivateKeyWithPassphrase() {
        // GIVEN
        val privateKey = TestKey.privateKey
        val passphrase = TestKey.privateKeyPassphrase

        // WHEN
        crypto.unlock(privateKey, passphrase).use { unlockedKey ->
            // THEN
            assertTrue(Crypto.newKey(unlockedKey.value).isUnlocked)
        }
    }

    @Test(expected = CryptoException::class)
    fun unlockPrivateKeyWithWrongPassphrase() {
        // GIVEN
        val privateKey = TestKey.privateKey
        val passphrase = TestKey.privateKey2Passphrase

        // WHEN
        crypto.unlock(privateKey, passphrase).use { unlockedKey ->
            // THEN
            assertTrue(Crypto.newKey(unlockedKey.value).isUnlocked)
        }
    }

    @Test
    fun lockUnlockedWithPassphrase() {
        // GIVEN
        val passphrase = TestKey.privateKeyPassphrase

        crypto.unlock(TestKey.privateKey, passphrase).use { unlockedKey ->
            // WHEN
            val locked = crypto.lock(unlockedKey.value, passphrase)

            // THEN
            val lockedKey = Crypto.newKeyFromArmored(locked)
            assertTrue(lockedKey.isLocked)

            // Try to use it (unlock, encrypt, sign, decrypt, verify).
            crypto.unlock(locked, passphrase).use { lockedUnlocked ->
                val publicKey = crypto.getPublicKey(TestKey.privateKey)

                val message = "message\r\nnewline"
                val encryptedOriginal = crypto.encryptAndSignText(message, publicKey, unlockedKey.value)
                val decryptedText = crypto.decryptAndVerifyText(encryptedOriginal, listOf(publicKey), listOf(lockedUnlocked.value), 0)

                assertEquals(
                    expected = VerificationStatus.Success,
                    actual = decryptedText.status
                )
                assertEquals(
                    expected = message.trimIndent(),
                    actual = decryptedText.text
                )
            }
        }
    }

    @Test
    fun encryptSignDecryptVerifyAString() {
        // GIVEN
        val message = "message\nnewline"

        val publicKey = crypto.getPublicKey(TestKey.privateKey)

        crypto.unlock(TestKey.privateKey, TestKey.privateKeyPassphrase).use { unlocked ->
            // WHEN
            val encrypted = crypto.encryptText(message, publicKey)
            val signature = crypto.signText(message, unlocked.value)

            // THEN
            val decryptedText = crypto.decryptText(encrypted, unlocked.value)
            val isVerified = crypto.verifyText(decryptedText, signature, publicKey, 0)
            assertTrue(isVerified)

            assertEquals(
                expected = message,
                actual = decryptedText
            )
        }
    }

    @Test
    fun encryptSignDecryptVerifyAByteArray() {
        // GIVEN
        val message = "message\r\nnewline"
        val data = message.toByteArray()

        val publicKey = crypto.getPublicKey(TestKey.privateKey)

        crypto.unlock(TestKey.privateKey, TestKey.privateKeyPassphrase).use { unlocked ->
            // WHEN
            val encrypted = crypto.encryptData(data, publicKey)
            val signature = crypto.signData(data, unlocked.value)

            // THEN
            val decryptData = crypto.decryptData(encrypted, unlocked.value)
            val isVerified = crypto.verifyData(decryptData, signature, publicKey, 0)
            assertTrue(isVerified)

            decryptData.use {
                assertTrue(data.contentEquals(it.array))
            }
        }
    }

    @Test
    fun decryptEncryptedMessageWithPrivateKey() {
        // GIVEN
        val encrypted =
            """
            -----BEGIN PGP MESSAGE-----
            Version: ProtonMail

            wcBMA5kajsUECZmgAQgAgJuGP/0+pUPu24mWeviRQ79s6fKKsKh6y1aBXwJM
            eQ8mSaLvHNSaCa8s9yozs9gWo2/Uf8Lpmqb70SMh2npwI5hyOFqXsrMEoEHn
            KTf86kSHnGZEtwrScXnekJjO1rfYynnAYuppTfpUc2E/uGZg6RChlwPbBZMw
            tOk8n6iL6u0+Ren9fxAmmMTw66vc5PDejmfAgzbdxeD7qV8wzqmipgiErk/w
            dPEzI5QGtGXUwsDfJeSGEdCslN1kHtZRj2B3tg6Ms7Ea/VIb3Kq6uyn2hQhS
            MlWwjzauF5mryV4Kbi1RP6yTykbPnRz6ia22HwbWzOVJ2Nu534RqNYA/99Bd
            G9JcAXjM6al21XdX0ZQww2R0Of3VzFVwQX+RSG1SWGq11u2nu5iXBVUJDa5x
            MS2SksqmW3Bh7Tbz2zlrCNZxH8USiAxXt/3xjwNlXgCg4b8sKNHNN4+Wa6S8
            HNwbYAc=
            =9RxF
            -----END PGP MESSAGE-----
            """.trimIndent()

        val expected = "Test PGP/MIME Message\n\n\n"

        crypto.unlock(TestKey.privateKey, TestKey.privateKeyPassphrase).use { unlockedKey ->
            // WHEN
            val decryptedText = crypto.decryptText(encrypted, unlockedKey.value)

            // THEN
            assertEquals(
                expected = expected,
                actual = decryptedText
            )
        }
    }

    @Test(expected = CryptoException::class)
    fun decryptEncryptedMessageWithWrongKey() {
        // GIVEN
        val encrypted =
            """
            -----BEGIN PGP MESSAGE-----
            Version: ProtonMail

            wcBMA5kajsUECZmgAQgAgJuGP/0+pUPu24mWeviRQ79s6fKKsKh6y1aBXwJM
            eQ8mSaLvHNSaCa8s9yozs9gWo2/Uf8Lpmqb70SMh2npwI5hyOFqXsrMEoEHn
            KTf86kSHnGZEtwrScXnekJjO1rfYynnAYuppTfpUc2E/uGZg6RChlwPbBZMw
            tOk8n6iL6u0+Ren9fxAmmMTw66vc5PDejmfAgzbdxeD7qV8wzqmipgiErk/w
            dPEzI5QGtGXUwsDfJeSGEdCslN1kHtZRj2B3tg6Ms7Ea/VIb3Kq6uyn2hQhS
            MlWwjzauF5mryV4Kbi1RP6yTykbPnRz6ia22HwbWzOVJ2Nu534RqNYA/99Bd
            G9JcAXjM6al21XdX0ZQww2R0Of3VzFVwQX+RSG1SWGq11u2nu5iXBVUJDa5x
            MS2SksqmW3Bh7Tbz2zlrCNZxH8USiAxXt/3xjwNlXgCg4b8sKNHNN4+Wa6S8
            HNwbYAc=
            =9RxF
            -----END PGP MESSAGE-----
            """.trimIndent()

        val expected = "Test PGP/MIME Message\n\n\n"

        // TestKey.privateKey2 is the wrong key (TestKey.privateKey is the right one).
        crypto.unlock(TestKey.privateKey2, TestKey.privateKey2Passphrase).use { unlockedKey ->
            // WHEN
            val decryptedText = crypto.decryptText(encrypted, unlockedKey.value)

            // THEN
            assertEquals(
                expected = expected,
                actual = decryptedText
            )
        }
    }

    @Test
    fun decryptEncryptedMessageWithPrivateKey2() {
        // GIVEN
        val encrypted =
            """
            -----BEGIN PGP MESSAGE-----
            Version: ProtonMail

            wcBMA7M4YhTWmh7GAQgAxAdgbJWi7MKSMiMg5rOUu6Y6nFJK9pgU5MsrKYqO
            /hXkkpocWTs4BDL+AXmy86e0C52mwsKJj/cFZ88erFLGMrkG+sVkDFi3fZ7Q
            dqrqKrzbGg6NubQpCtwGv+KvtFcMfCUWD4jeH/saD4wW9ZAH3Ozu0s/VamIX
            62VDi+l6TrZIUwsC6Pnyy5O8O1BnOultCjUP4bYApSfQIBDENBVyMVT9pp1/
            ylfgUSZQCj2vWkbMtMH+SAgBgk+MMYVBTx+Pk1O9lhZdqXhjzEmi58AZMdq7
            /+CGjJwnySBFCLaHddYfzvVVQEAJngRRl7WA+CVkskMc94w1nwlVeuARuAiy
            /9LAaQHHE3Wb1en/rqK4IPK0qWaInpVualn6KeORmtnS3Kl2Xynt92Lcckoj
            37WEdjXDCIhl4JyrldelRmaxBisnW3te4vsukGh87E4jL8oDvIMwHN0bm7KH
            +kBnlxqrR6N5vZmcjFoU+n9XBYDkoPZ0MZCwCgMi2BbWrQv7zy/o3+35kgms
            6c3Mwb7nIP15ksysLz884tF6k5cVoLFISL7OMqem1uKM66BgOYJovvRR1Y+v
            70aQ/G2w7B44mzPBOlzOAzhDQDHtxNft1XT+LH2cjrExd0EzYE+8fpYpOHC0
            KfHrt6wx/sj/O+e1M9F19UGDIJMFRmlgRIUJCEmpiaZnWjmdajfUpOPd47ac
            GYmSnoyEOzjf1Lyy0M6w7BHPQgwaF7Ss94EAcsFcfw==
            =iJT4
            -----END PGP MESSAGE-----
            """.trimIndent()

        val expected = "Dear valued customer,\n\n" +
            "Thank you for subscribing to ProtonMail! Your support will enable us to scale up ProtonMail and continue our mission to bring easy-to-use, encrypted email to people everywhere.\n\n" +
            "Thank you,\n\n" +
            "The ProtonMail Team\n"

        crypto.unlock(TestKey.privateKey2, TestKey.privateKey2Passphrase).use { unlockedKey ->
            // WHEN
            val decryptedText = crypto.decryptText(encrypted, unlockedKey.value)

            // THEN
            assertEquals(
                expected = expected,
                actual = decryptedText
            )
        }
    }

    @Test
    fun encryptAndSignDecryptAndVerifyAString() {
        // GIVEN
        val message = "message\nnewline"

        val publicKey = crypto.getPublicKey(TestKey.privateKey)

        crypto.unlock(TestKey.privateKey, TestKey.privateKeyPassphrase).use { unlockedKey ->
            // WHEN
            val encryptedAndSigned = crypto.encryptAndSignText(message, publicKey, unlockedKey.value)

            // THEN
            val decryptedText = crypto.decryptAndVerifyText(encryptedAndSigned, listOf(publicKey), listOf(unlockedKey.value), 0)

            assertEquals(
                expected = VerificationStatus.Success,
                actual = decryptedText.status
            )
            assertEquals(
                expected = message,
                actual = decryptedText.text
            )
        }
    }

    @Test
    fun encryptAndSignDecryptAndVerifyAByteArray() {
        // GIVEN
        val message = "message\r\nnewline"
        val data = message.toByteArray()

        val publicKey = crypto.getPublicKey(TestKey.privateKey)

        crypto.unlock(TestKey.privateKey, TestKey.privateKeyPassphrase).use { unlockedKey ->
            // WHEN
            val encryptedAndSigned = crypto.encryptAndSignData(data, publicKey, unlockedKey.value)

            // THEN
            val decryptedData = crypto.decryptAndVerifyData(encryptedAndSigned, listOf(publicKey), listOf(unlockedKey.value), 0)

            assertEquals(
                expected = VerificationStatus.Success,
                actual = decryptedData.status
            )
            decryptedData.data.use {
                assertTrue(data.contentEquals(it.array))
            }
        }
    }
}