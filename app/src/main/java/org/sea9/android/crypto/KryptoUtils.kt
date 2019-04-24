package org.sea9.android.crypto

import android.util.Base64
import android.util.Log
import java.io.*
import java.security.*
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import javax.crypto.*
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec

class KryptoUtils {
	companion object {
		private const val TAG = "sea9.krypto"
		private const val DEFAULT_CHARSET = "UTF-8"
		private const val DEFAULT_PBE_ALGORITHM = "PBEWITHSHA-256AND192BITAES-CBC-BC"
//		private const val DEFAULT_HASH_ALGORITHM = "SHA-256"
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

//		fun hash(input: ByteArray): ByteArray? {
//			try {
//				val digest = MessageDigest.getInstance(DEFAULT_HASH_ALGORITHM)
//				digest.reset()
//				return digest.digest(input)
//			} catch (e: NoSuchAlgorithmException) {
//				Log.w(TAG, e.message)
//				return null
//			}
//		}

		fun generateSalt(): ByteArray {
			val buffer = ByteArray(DEFAULT_SALT_LENGTH)
			SecureRandom().nextBytes(buffer)
			return buffer
		}

		fun encode(input: ByteArray): ByteArray {
			return Base64.encode(input, Base64.NO_WRAP)
		}

		fun decode(input: ByteArray): ByteArray {
			return Base64.decode(input, Base64.NO_WRAP)
		}

		fun convert(input: CharArray): ByteArray? {
			var writer: OutputStreamWriter? = null
			return try {
				val output = ByteArrayOutputStream()
				writer = OutputStreamWriter(output, DEFAULT_CHARSET)
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

		fun convert(input: ByteArray): CharArray? {
			var reader: InputStreamReader? = null
			val buffer = CharArray(input.size)
			return try {
				reader = InputStreamReader(ByteArrayInputStream(input), DEFAULT_CHARSET)
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
	}
}