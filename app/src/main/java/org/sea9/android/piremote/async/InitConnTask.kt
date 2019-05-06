package org.sea9.android.piremote.async

import android.os.AsyncTask
import android.util.Log
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import org.sea9.android.piremote.*
import org.sea9.android.piremote.data.DbContract
import org.sea9.android.piremote.data.HostRecord
import java.lang.RuntimeException
import java.net.InetAddress
import java.util.*

class InitConnTask(private val caller: MainContext) {
	companion object {
		const val TAG = "pi.main_init"
		const val PLURAL = "s"
		const val EMPTY = ""
		const val IPv4 = 32
		const val COMMAND_MEASUREMENT =
			"/opt/vc/bin/vcgencmd measure_temp &&" +
					"/opt/vc/bin/vcgencmd measure_volts core &&" +
					"/opt/vc/bin/vcgencmd get_mem arm &&" +
					"/opt/vc/bin/vcgencmd get_mem gpu"
		val COMMAND_NEIGHBOR = arrayOf("ip", "neighbor")
		const val COMMAND_PWD = "pwd; echo \$PATH"
	}

	fun init() {
		AsyncNeighborTask1(caller)
			.execute(*COMMAND_NEIGHBOR)
	}
	private class AsyncNeighborTask1(private val caller: MainContext): ExecLocalTask() {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: String?) {
			if (result != null) {
				AsyncNeighborTask2(caller).execute(result)
			} else {
				Log.w(TAG, "No response from the local command task")
				caller.initializer.connect(true)
			}
		}
	}
	private class AsyncNeighborTask2(private val caller: MainContext): AsyncTask<String, String, IntArray>() {
		override fun onPostExecute(result: IntArray) {
			caller.initializer.connect(true)
		}
		override fun doInBackground(vararg params: String): IntArray {
			if (params.isEmpty()) {
				Log.w(TAG, "No response from the local command task")
				return IntArray(0)
			}
			val regex = NetworkUtils.NEIGHBOR_PATTERN.toRegex()
			val local = params[0].let {
				regex.findAll(it).map { match ->
					match.value.trim().split(NetworkUtils.DELIMITER).map { value ->
						value.toInt()
					}
				}.filter { item ->
					(item[0] < 256) && (item[1] < 256) && (item[2] < 256) && (item[3] < 256) //Skip values > 255
				}.map { item ->
					InetAddress.getByAddress(item.toIntArray().map { v ->
						v.toByte()
					}.toByteArray())
				}
			}.toList()

			publishProgress(caller.context?.getString(R.string.message_netinit1))

			local.apply {
				forEach {
					if (it == caller.gateway)
						publishProgress("${it.hostAddress} (gateway)")
					else
						publishProgress(it.hostAddress)
				}
			}

			val count = local.size
			publishProgress(
				caller.context?.getString(
					R.string.message_netinit2,
					count,
					if (count > 1) PLURAL else EMPTY
				)
			)

			return local.map {
				NetworkUtils.convert(it)
			}.toIntArray()
		}
		override fun onProgressUpdate(vararg values: String) {
			if (values.isNotEmpty()) {
				caller.writeConsole(values[0])
				caller.callback?.refreshUi()
			}
		}
	}

	fun connect(search: Boolean) {
		if (caller.currentHost != null)
			AsyncConnectTask(caller, search).execute(caller.currentHost)
		else {
			caller.initializer.search()
		}
	}
	private class AsyncConnectTask(private val caller: MainContext, private val search: Boolean):
			ExecRemoteTask(caller,
				COMMAND_PWD, false) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if ((result.status == Status.OKAY) && !result.message.isNullOrBlank()) {
				result.message.lines().let {
					when {
						(it.size >= 2) -> {
							if (!it[1].contains(EXPECTED)) {
								caller.commands.patchPath = true
							}
							caller.navigator.currentPath = it[0]
							caller.writeConsole(caller.context?.getString(R.string.message_current, result.hostName))
						}
						(it.isNotEmpty()) -> {
							caller.writeConsole(result.message)
						}
						else -> caller.writeConsole("Unknown error") //Should not reach here...
					}
					caller.callback?.refreshUi()
				}

				val rec = HostRecord(result.hostName, result.address, result.login)
				caller.navigator.navigate(rec, null)
				AsyncMeasureTask(caller, false)
					.executeOnExecutor(THREAD_POOL_EXECUTOR, rec)
			} else if (search) {
				caller.writeConsole(caller.getString(R.string.message_error, result.hostName, result.message))
				caller.callback?.refreshUi()
				caller.initializer.search()
			} else {
				caller.writeConsole(caller.getString(R.string.message_connect, result.hostName))
				caller.callback?.refreshUi()
				caller.callback?.isBusy(false)
			}
		}
	}

	fun search() {
		if (caller.address != null) {
			val netmask = NetworkUtils.buildNetmask(
				IPv4,
				caller.netmask
			)
			val masked = NetworkUtils.convert(caller.address!!) and netmask
			DbContract.Host.list(caller.dbHelper!!).filter {
				((it.address and netmask) == masked) && (it.host != caller.currentHost?.host)
			}.apply {
				AsyncSearchTask(caller).execute(*this.toTypedArray())
			}
		}
	}
	private class AsyncSearchTask(private val caller: MainContext): AsyncTask<HostRecord, String, HostRecord>() {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: HostRecord?) {
			if (result != null) {
				caller.onHostSelected(result.host, result.address, result.login)
				caller.writeConsole(caller.context?.getString(R.string.message_stored, result.host))
				caller.navigator.navigate(result, null)
				AsyncMeasureTask(caller, false)
					.executeOnExecutor(THREAD_POOL_EXECUTOR, result)
			} else {
				// Using the current host here because even the current host cannot be connected, there is no better
				// choice of hostname and IP to display on the main page
				// Also skipping check network status here (thus the flag is 0x07 instead of 0x17) because this is
				// just checked in AsyncConnectTask
				caller.navigator.currentPath = null
				caller.hardwareStatus = null
				caller.writeConsole(caller.getString(R.string.message_nohost))
				caller.callback?.refreshUi()
				caller.callback?.isBusy(false)
			}
		}
		override fun doInBackground(vararg params: HostRecord): HostRecord? {
			params.apply {
				forEach {
					try {
						publishProgress(caller.getString(R.string.message_trying, it.host))
						JSch().getSession("test", NetworkUtils.convert(it.address).hostAddress, 22).also { session ->
							session.setConfig(Properties().also { prop ->
								prop["StrictHostKeyChecking"] = "no"
							})
							session.connect(7000)
						}
					} catch (e: JSchException) {
						if (ExecRemoteTask.Response.findStatus(e) == ExecRemoteTask.Status.AUTH_FAIL) {
							return it // 'Auth fail' means this host is online
						} else
							Log.w(TAG, e.message)
					} catch (e: Exception) {
						throw RuntimeException(e)
					}
				}
				if (isEmpty()) {
					publishProgress(caller.getString(R.string.message_nottry))
				}
			}
			return null
		}
		override fun onProgressUpdate(vararg values: String) {
			if (values.isNotEmpty()) {
				caller.writeConsole(values[0])
				caller.callback?.refreshUi()
			}
		}
	}

	private class AsyncMeasureTask(private val caller: MainContext, sudo: Boolean):
			ExecRemoteTask(caller,
				COMMAND_MEASUREMENT, sudo) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if ((result.status == Status.OKAY) && !result.message.isNullOrBlank())
				caller.hardwareStatus = result.message.trim()
			else
				caller.hardwareStatus = caller.context?.getString(R.string.message_noinfo)
			caller.callback?.refreshUi()
			caller.callback?.isBusy(false)
		}
	}
}