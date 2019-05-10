package org.sea9.android.piremote.async

import android.os.AsyncTask
import android.os.Handler
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import com.jcraft.jsch.KeyPair
import org.sea9.android.crypto.KryptoUtils
import org.sea9.android.piremote.MainContext
import org.sea9.android.piremote.NetworkUtils
import org.sea9.android.piremote.R
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

			// 1. Create the .ssh/authorized_keys file if it not yet exists
			// 2. Check if the SSH key (identified by the host entry name) already exists
			// 3. If exists (denoted by the first '&&') use sed to remove the old key
			// 4. Remove the blank lines as well
			// 5. Use echo to write the key
			// 6. Finally check again if the SSH key exists
			val command =
				"mkdir -p /home/$login/.ssh; touch /home/$login/.ssh/authorized_keys; " +
				"cat /home/$login/.ssh/authorized_keys | grep -q $url " +
				"&& sed -i '/^ssh..*$url$/d' /home/$login/.ssh/authorized_keys; " +
				"sed -i '1{/^$/d;}' /home/$login/.ssh/authorized_keys; " +
				"echo \"$outb\" >> /home/$login/.ssh/authorized_keys; " +
				"cat /home/$login/.ssh/authorized_keys | grep -q $url " +
				"&& echo \"$TRUE\" || echo \"$FALSE\""

			AsyncRegisterTask(
				caller, this, url, NetworkUtils.convert(address).hostAddress, login, password, command, outv.toByteArray()
			).execute()
			Log.w(TAG, command)
		}
	}

	private class AsyncRegisterTask(
		val caller: MainContext,
		val jsch: JSch, val host: String, val address: String, val login: String,
		val password: CharArray, val command: String, val key: ByteArray
	): AsyncTask<Void, Void, String>() {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}

		override fun onPostExecute(result: String) {
			if (result.contains(TRUE)) {
				DbContract.Host.registerHost(caller.dbHelper!!, host, key).apply {
					if (this == 1) {
						caller.populate()
						caller.onHostSelected(host)
						Handler().postDelayed({
							caller.initializer.connect(false)
						}, 100)
						caller.writeConsole(caller.context?.getString(R.string.message_register))
					} else {
						Log.w(TAG, "Register failed $host $this")
						caller.writeConsole(caller.context?.getString(R.string.message_saveregfail))
						caller.callback?.refreshUi()
					}
				}
			} else {
				caller.writeConsole(caller.context?.getString(R.string.message_regfail))
				caller.callback?.refreshUi()
			}
			caller.callback?.isBusy(false)
		}

		override fun doInBackground(vararg params: Void?): String {
			try {
				var ret: String
				jsch.getSession(login, address, 22).also { session ->
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