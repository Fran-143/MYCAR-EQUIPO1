package com.mycar.app.features.autenticacion.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES-256/GCM con clave no exportable de AndroidKeyStore.
 * Un único sobre atómico incluye UUID y credenciales. noBackupFilesDir excluye
 * tanto backup en nube como traslado. Cada servidor tiene su propio espacio.
 * Nunca se degrada a almacenamiento en texto plano si Keystore falla.
 */
class KeystoreCredentialStore(context: Context, server: String) : CredentialStore {
    private val namespace = MessageDigest.getInstance("SHA-256")
        .digest(server.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    private val alias = "mycar.auth.$namespace"
    private val file = AtomicFile(File(context.noBackupFilesDir, "auth-$namespace.bin"))
    private val gson = Gson()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }

    override fun load(): CredentialVault? {
        val bytes = try { file.readFully() } catch (_: java.io.FileNotFoundException) { return null }
        val envelope = ByteBuffer.wrap(bytes)
        require(envelope.remaining() >= 4 + 12 + 16 && envelope.int == 1)
        val iv = ByteArray(12).also { envelope.get(it) }
        val encrypted = ByteArray(envelope.remaining()).also { envelope.get(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            updateAAD(namespace.toByteArray(Charsets.UTF_8))
        }
        val plain = cipher.doFinal(encrypted)
        return try {
            gson.fromJson(String(plain, Charsets.UTF_8), CredentialVault::class.java).validate()
        } finally { plain.fill(0) }
    }

    override fun save(vault: CredentialVault) {
        vault.validate()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key())
            updateAAD(namespace.toByteArray(Charsets.UTF_8))
        }
        val plain = gson.toJson(vault).toByteArray(Charsets.UTF_8)
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        val bytes = ByteBuffer.allocate(4 + cipher.iv.size + encrypted.size)
            .putInt(1).put(cipher.iv).put(encrypted).array()
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }
}
