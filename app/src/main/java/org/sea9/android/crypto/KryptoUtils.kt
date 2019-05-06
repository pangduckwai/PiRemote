package org.sea9.android.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.io.*
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec

class KryptoUtils {
	companion object {
		private const val TAG = "sea9.krypto"
		private const val DEFAULT_CHARSET = "UTF-8"
		private const val DEFAULT_HASH_ALGORITHM = "SHA-256"

		/*=================================
		 * Password-Based Encryption (PBE)
		 */
		private const val DEFAULT_PBE_ALGORITHM = "PBEWITHSHA-256AND192BITAES-CBC-BC"
		private const val DEFAULT_ITERATION = 2048
		private const val DEFAULT_SALT_LENGTH = 512

		fun encrypt(message: CharArray, password: CharArray, salt: ByteArray): CharArray? {
			return convert(encode(doCipher(convert(message)!!, PBEKeySpec(password), PBEParameterSpec(salt, DEFAULT_ITERATION), true)))
		}

		fun decrypt(secret: CharArray, password: CharArray, salt: ByteArray): CharArray? {
			return convert(doCipher(decode(convert(secret)!!), PBEKeySpec(password), PBEParameterSpec(salt, DEFAULT_ITERATION), false))
		}

		private fun doCipher(message: ByteArray, keySpec: KeySpec, paramSpec: AlgorithmParameterSpec, isEncrypt: Boolean): ByteArray {
			try {
				val factory = SecretKeyFactory.getInstance(DEFAULT_PBE_ALGORITHM)
				val key = factory.generateSecret(keySpec)
				val cipher = Cipher.getInstance(key.algorithm)
				cipher.init(if (isEncrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, key, paramSpec)
				return cipher.doFinal(message)
			} catch (e: NoSuchAlgorithmException) {
				throw RuntimeException(e)
			} catch (e: InvalidKeySpecException) {
				throw RuntimeException(e)
			} catch (e: NoSuchPaddingException) {
				throw RuntimeException(e)
			} catch (e: InvalidKeyException) {
				throw RuntimeException(e)
			} catch (e: InvalidAlgorithmParameterException) {
				throw RuntimeException(e)
			} catch (e: IllegalBlockSizeException) {
				throw RuntimeException(e)
			} catch (e: BadPaddingException) {
				throw RuntimeException(e)
			}
		}

		fun generateSalt(): ByteArray {
			val buffer = ByteArray(DEFAULT_SALT_LENGTH)
			SecureRandom().nextBytes(buffer)
			return buffer
		}

		/*========================================================
		 * Symmetric encryption with key kept in Android KeyStore
		 */
		private const val TRANSFORMATION = "AES/GCM/NoPadding"
		private const val KEYSTORE_NAME = "AndroidKeyStore"
		private const val AUTHENTICATION_TIMEOUT = 300 //5 minutes

		fun generateKey(alias: String, requireAuth: Boolean): Boolean {
			val keystore = KeyStore.getInstance(KEYSTORE_NAME).also {
				it.load(null)
			}

			return if (!keystore.containsAlias(alias)) {
				KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_NAME).apply {
					init(
						KeyGenParameterSpec.Builder(
								alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
						).also { builder ->
							builder
								.setBlockModes(KeyProperties.BLOCK_MODE_GCM)
								.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)

							if (requireAuth) {
								builder
									.setUserAuthenticationRequired(true)
									.setUserAuthenticationValidityDurationSeconds(AUTHENTICATION_TIMEOUT)
							}
						}.build()
					)
					generateKey()
				}
				true
			} else
				false
		}

		fun encrypt(message: ByteArray, alias: String): Pair<CharArray, CharArray>? {
			val keystore = KeyStore.getInstance(KEYSTORE_NAME).also {
				it.load(null)
			}
			if (!keystore.containsAlias(alias)) generateKey(alias, false)

			val cipher = Cipher.getInstance(TRANSFORMATION).apply {
				init(Cipher.ENCRYPT_MODE, keystore.getKey(alias, null) as SecretKey)
			}

			val iv = cipher.parameters.getParameterSpec(GCMParameterSpec::class.java)

			val out = ByteArrayOutputStream()
			val cpo = CipherOutputStream(out, cipher)
			cpo.write(message)
			cpo.close()

			val ret1 = convert(encode(out.toByteArray()))
			val ret2 = convert(encode(iv.iv))
			return if ((ret1 != null) && (ret2 != null))
				Pair(ret1, ret2)
			else
				null
		}

		fun decrypt(secret: CharArray, iv: CharArray, alias: String): ByteArray? {
			val keystore = KeyStore.getInstance(KEYSTORE_NAME).also {
				it.load(null)
			}
			if (!keystore.containsAlias(alias)) return null

			val msg = convert(secret)?.let { decode(it) }
			val biv = convert(iv)?.let { decode(it) }
			if ((msg == null) || (biv == null)) return null

			val cipher = Cipher.getInstance(TRANSFORMATION).apply {
				init(
					Cipher.DECRYPT_MODE,
					keystore.getKey(alias, null) as SecretKey,
					GCMParameterSpec(128, biv)
				)
			}

			val out = ByteArrayOutputStream()
			val cpo = CipherOutputStream(out, cipher)
			cpo.write(msg)
			cpo.close()
			return out.toByteArray()
		}

		/*=================
		 * Utility methods
		 */
		fun hash(input: ByteArray): ByteArray? {
			return try {
				val digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM)
				digest.reset()
				digest.digest(input)
			} catch (e: NoSuchAlgorithmException) {
				Log.w(TAG, e.message)
				null
			}
		}

		/*
		 * Base64 encode a byte array.
		 */
		fun encode(input: ByteArray): ByteArray {
			return Base64.encode(input, Base64.NO_WRAP)
		}

		/*
		 * Base64 decode a byte array.
		 */
		fun decode(input: ByteArray): ByteArray {
			return Base64.decode(input, Base64.NO_WRAP)
		}

		/*
		 * Convert a char array into a byte array with the given charset.
		 */
		fun convert(input: CharArray, charset: String): ByteArray? {
			var writer: OutputStreamWriter? = null
			return try {
				val output = ByteArrayOutputStream()
				writer = OutputStreamWriter(output, charset)
				writer.write(input)
				writer.flush()
				output.toByteArray()
			} catch (e: UnsupportedEncodingException) {
				Log.w(TAG, e.message)
				null
			} catch (e: IOException) {
				Log.w(TAG, e.message)
				null
			} finally {
				writer?.close()
			}
		}
		fun convert(input: CharArray): ByteArray? {
			return convert(input, DEFAULT_CHARSET)
		}

		/*
		 * Convert a byte array into a char array with the given charset.
		 */
		fun convert(input: ByteArray, charset: String): CharArray? {
			var reader: InputStreamReader? = null
			val buffer = CharArray(input.size)
			return try {
				reader = InputStreamReader(ByteArrayInputStream(input), charset)
				if (reader.read(buffer) < 0) {
					Log.w(TAG, "End of stream reached")
					null
				} else {
					buffer
				}
			} catch (e: UnsupportedEncodingException) {
				Log.w(TAG, e.message)
				null
			} catch (e: IOException) {
				Log.w(TAG, e.message)
				null
			} finally {
				reader?.close()
			}
		}
		fun convert(input: ByteArray): CharArray? {
			return convert(input, DEFAULT_CHARSET)
		}
	}
}