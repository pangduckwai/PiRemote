package org.sea9.android.piremote.async

import android.os.AsyncTask
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import org.sea9.android.crypto.KryptoUtils
import org.sea9.android.piremote.MainContext
import org.sea9.android.piremote.NetworkUtils
import org.sea9.android.piremote.data.DbContract
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.lang.RuntimeException
import java.util.*

class RegisterTask(private val caller: MainContext) {
	companion object {
		const val TAG = "pi.register"
		const val TRUE = "TRUE"
		const val FALSE = "FALSE"
	}

	fun register(url: String, address: Int, login: String, password: CharArray) {
		JSch().apply {
			val keypair = KeyPair.genKeyPair(this, KeyPair.RSA)

			val outv = ByteArrayOutputStream()
			keypair.writePrivateKey(outv, null)

			val outb = ByteArrayOutputStream()
			keypair.writePublicKey(outb, url)

			val command =
				"mkdir -p /home/$login/.ssh; touch /home/$login/.ssh/authorized_keys; " +
				"cat /home/$login/.ssh/authorized_keys | grep -q $url " +
				"|| echo \"$outb\" >> /home/$login/.ssh/authorized_keys; " +
				"cat /home/$login/.ssh/authorized_keys | grep -q $url " +
				"&& echo \"$TRUE\" || echo \"$FALSE\""

			AsyncRegisterTask(
				caller, this, NetworkUtils.convert(address).hostAddress, login, password, command, outv.toByteArray()
			).execute()
		}
	}

	private class AsyncRegisterTask(
		val caller: MainContext,
		val jsch: JSch, val host: String, val login: String,
		val password: CharArray, val command: String, val key: ByteArray
	): AsyncTask<Void, Void, String>() {
		override fun onPostExecute(result: String) {
			if (result == TRUE) {
				if (DbContract.Host.registerHost(caller.dbHelper!!, host, key) == 1)
					Log.w(TAG, "Successfully registered") //TODO
				else
					Log.w(TAG, "Registration failed")
			}
		}

		override fun doInBackground(vararg params: Void?): String {
			try {
				var ret: String
				jsch.getSession(login, host, 22).also { session ->
					session.setPassword(KryptoUtils.convert(password))
					session.setConfig(Properties().also { prop ->
						prop["StrictHostKeyChecking"] = "no"
					})
					session.connect(30000)

					(session.openChannel("exec") as ChannelExec).also { channel ->
						channel.setCommand(command)
						ret = channel.inputStream.let {
							channel.connect()
							it.bufferedReader().use(BufferedReader::readText)
						}
						channel.disconnect()
					}
					session.disconnect()
				}
				return ret
			} catch (e: JSchException) {
				return e.message!!
			} catch (e: Exception) {
				throw RuntimeException(e)
			}
		}
	}
}