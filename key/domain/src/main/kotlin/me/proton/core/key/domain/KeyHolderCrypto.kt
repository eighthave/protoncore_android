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

package me.proton.core.key.domain

import me.proton.core.crypto.common.context.CryptoContext
import me.proton.core.crypto.common.pgp.DecryptedData
import me.proton.core.crypto.common.pgp.DecryptedText
import me.proton.core.crypto.common.pgp.EncryptedMessage
import me.proton.core.crypto.common.pgp.Signature
import me.proton.core.crypto.common.pgp.decryptAndVerifyDataOrNull
import me.proton.core.crypto.common.pgp.decryptAndVerifyTextOrNull
import me.proton.core.crypto.common.pgp.exception.CryptoException
import me.proton.core.key.domain.entity.key.PrivateKeyRing
import me.proton.core.key.domain.entity.key.PublicKey
import me.proton.core.key.domain.entity.key.PublicKeyRing
import me.proton.core.key.domain.entity.keyholder.KeyHolder
import me.proton.core.key.domain.entity.keyholder.KeyHolderContext

/**
 * Executes the given [block] function for a [KeyHolder] on a [KeyHolderContext] and then close any associated
 * key resources whether an exception is thrown or not.
 *
 * @param context [CryptoContext] providing any needed dependencies for Crypto functions.
 * @param block a function allowing usage of [KeyHolderContext] extension functions.
 * @return the result of [block] function invoked on this [KeyHolder].
 */
fun <R> KeyHolder.useKeys(context: CryptoContext, block: KeyHolderContext.() -> R): R {
    val privateKeys = keys.map { key -> key.privateKey }
    val publicKeys = keys.map { key ->
        val publicKey = context.pgpCrypto.getPublicKey(key.privateKey.key)
        PublicKey(publicKey, key.privateKey.isPrimary)
    }
    val keyHolderContext = KeyHolderContext(
        context = context,
        privateKeyRing = PrivateKeyRing(context, privateKeys),
        publicKeyRing = PublicKeyRing(publicKeys)
    )
    return keyHolderContext.use { block(it) }
}

/**
 * Decrypt [message] as [String] using [PrivateKeyRing].
 *
 * Note: String canonicalization/standardization is applied.
 *
 * @throws [CryptoException] if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptTextOrNull]
 * @see [KeyHolderContext.encryptText]
 */
fun KeyHolderContext.decryptText(message: EncryptedMessage): String =
    privateKeyRing.decryptText(message)

/**
 * Decrypt [message] as [ByteArray] using [PrivateKeyRing].
 *
 * @throws [CryptoException] if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptDataOrNull]
 * @see [KeyHolderContext.encryptData]
 */
fun KeyHolderContext.decryptData(message: EncryptedMessage): ByteArray =
    privateKeyRing.decryptData(message)

/**
 * Decrypt [message] as [String] using [PrivateKeyRing].
 *
 * Note: String canonicalization/standardization is applied.
 *
 * @return [String], or `null` if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptText]
 */
fun KeyHolderContext.decryptTextOrNull(message: EncryptedMessage): String? =
    privateKeyRing.decryptTextOrNull(message)

/**
 * Decrypt [message] as [ByteArray] using [PrivateKeyRing].
 *
 * @return [ByteArray], or `null` if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptData]
 */
fun KeyHolderContext.decryptDataOrNull(message: EncryptedMessage): ByteArray? =
    privateKeyRing.decryptDataOrNull(message)

/**
 * Sign [text] using [PrivateKeyRing].
 *
 * @throws [CryptoException] if [text] cannot be signed.
 *
 * @see [KeyHolderContext.verifyText]
 */
fun KeyHolderContext.signText(text: String): Signature =
    privateKeyRing.signText(text)

/**
 * Sign [data] using [PrivateKeyRing].
 *
 * @throws [CryptoException] if [data] cannot be signed.
 *
 * @see [KeyHolderContext.verifyData]
 */
fun KeyHolderContext.signData(data: ByteArray): Signature =
    privateKeyRing.signData(data)

/**
 * Verify [signature] of [text] is correctly signed using [PublicKeyRing].
 *
 * @param validAtUtc UTC time for [signature] validation, or 0 to ignore time.
 *
 * @return true if at least one [PublicKey] verify [signature].
 *
 * @see [KeyHolderContext.signText]
 */
fun KeyHolderContext.verifyText(text: String, signature: Signature, validAtUtc: Long = 0): Boolean =
    publicKeyRing.verifyText(context, text, signature, validAtUtc)

/**
 * Verify [signature] of [data] is correctly signed using [PublicKeyRing].
 *
 * @param validAtUtc UTC time for [signature] validation, or 0 to ignore time.
 *
 * @return true if at least one [PublicKey] verify [signature].
 *
 * @see [KeyHolderContext.signData]
 */
fun KeyHolderContext.verifyData(data: ByteArray, signature: Signature, validAtUtc: Long = 0): Boolean =
    publicKeyRing.verifyData(context, data, signature, validAtUtc)

/**
 * Encrypt [text] using [PublicKeyRing].
 *
 * @throws [CryptoException] if [text] cannot be encrypted.
 *
 * @see [KeyHolderContext.decryptText]
 */
fun KeyHolderContext.encryptText(text: String): EncryptedMessage =
    publicKeyRing.encryptText(context, text)

/**
 * Encrypt [data] using [PublicKeyRing].
 *
 * @throws [CryptoException] if [data] cannot be encrypted.
 *
 * @see [KeyHolderContext.decryptData]
 */
fun KeyHolderContext.encryptData(data: ByteArray): EncryptedMessage =
    publicKeyRing.encryptData(context, data)

/**
 * Encrypt [text] using [PublicKeyRing] and sign using [PrivateKeyRing] in an embedded [EncryptedMessage].
 *
 * @throws [CryptoException] if [text] cannot be encrypted or signed.
 *
 * @see [KeyHolderContext.decryptAndVerifyText].
 */
fun KeyHolderContext.encryptAndSignText(text: String): EncryptedMessage =
    context.pgpCrypto.encryptAndSignText(
        text,
        publicKeyRing.primaryKey.key,
        privateKeyRing.unlockedPrimaryKey.unlockedKey.value
    )

/**
 * Encrypt [data] using [PublicKeyRing] and sign using [PrivateKeyRing] in an embedded [EncryptedMessage].
 *
 * @throws [CryptoException] if [data] cannot be encrypted or signed.
 *
 * @see [KeyHolderContext.decryptAndVerifyData].
 */
fun KeyHolderContext.encryptAndSignData(data: ByteArray): EncryptedMessage =
    context.pgpCrypto.encryptAndSignData(
        data,
        publicKeyRing.primaryKey.key,
        privateKeyRing.unlockedPrimaryKey.unlockedKey.value
    )

/**
 * Decrypt [message] as [String] using [PrivateKeyRing] and verify using [PublicKeyRing].
 *
 * Note: String canonicalization/standardization is applied.
 *
 * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
 *
 * @throws [CryptoException] if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.encryptAndSignText]
 */
fun KeyHolderContext.decryptAndVerifyText(message: EncryptedMessage, validAtUtc: Long = 0): DecryptedText =
    context.pgpCrypto.decryptAndVerifyText(
        message,
        publicKeyRing.keys.map { it.key },
        privateKeyRing.unlockedKeys.map { it.unlockedKey.value },
        validAtUtc
    )

/**
 * Decrypt [message] as [ByteArray] using [PrivateKeyRing] and verify using [PublicKeyRing].
 *
 * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
 *
 * @throws [CryptoException] if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.encryptAndSignData]
 */
fun KeyHolderContext.decryptAndVerifyData(message: EncryptedMessage, validAtUtc: Long = 0): DecryptedData =
    context.pgpCrypto.decryptAndVerifyData(
        message,
        publicKeyRing.keys.map { it.key },
        privateKeyRing.unlockedKeys.map { it.unlockedKey.value },
        validAtUtc
    )

/**
 * Decrypt [message] as [String] using [PrivateKeyRing] and verify using [PublicKeyRing].
 *
 * Note: String canonicalization/standardization is applied.
 *
 * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
 *
 * @return [DecryptedText], or `null` if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptAndVerifyText]
 */
fun KeyHolderContext.decryptAndVerifyTextOrNull(message: EncryptedMessage, validAtUtc: Long = 0): DecryptedText? =
    context.pgpCrypto.decryptAndVerifyTextOrNull(
        message,
        publicKeyRing.keys.map { it.key },
        privateKeyRing.unlockedKeys.map { it.unlockedKey.value },
        validAtUtc
    )

/**
 * Decrypt [message] as [ByteArray] using [PrivateKeyRing] and verify using [PublicKeyRing].
 *
 * @param validAtUtc UTC time for embedded signature validation, or 0 to ignore time.
 *
 * @return [DecryptedData], or `null` if [message] cannot be decrypted.
 *
 * @see [KeyHolderContext.decryptAndVerifyData]
 */
fun KeyHolderContext.decryptAndVerifyDataOrNull(message: EncryptedMessage, validAtUtc: Long = 0): DecryptedData? =
    context.pgpCrypto.decryptAndVerifyDataOrNull(
        message,
        publicKeyRing.keys.map { it.key },
        privateKeyRing.unlockedKeys.map { it.unlockedKey.value },
        validAtUtc
    )