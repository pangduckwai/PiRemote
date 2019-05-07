package org.sea9.android.piremote.async

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import org.sea9.android.piremote.*
import org.sea9.android.piremote.data.DbContract
import org.sea9.android.piremote.data.HostRecord
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.lang.RuntimeException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.util.*

open class ExecRemoteTask(private val caller: MainContext, private val command: String, private val sudo: Boolean):
		AsyncTask<HostRecord, Void, ExecRemoteTask.Response>() {
	companion object {
		const val TAG = "pi.exec_remote"
		const val SUDO = "sudo"
		const val EXPECTED = "sbin"
	}

	override fun doInBackground(vararg params: HostRecord): Response {
		if (params.size != 1) throw RuntimeException("Invalid execute-command task")

		if (!params[0].registered) {
			return Response(
				Status.UNREGISTERED,
				params[0].host,
				params[0].address,
				params[0].login,
				command,
				exception = null
			)
		}

		if (!NetworkUtils.getNetworkInfo(caller, false)) {
			return Response(
				Status.NO_WIFI,
				params[0].host,
				params[0].address,
				params[0].login,
				command,
				exception = null
			)
		}

		val sshKey = DbContract.Host.getKey(caller.dbHelper!!, params[0].host) ?: return Response(
			Status.NO_DB_RECORD,
			params[0].host,
			params[0].address,
			params[0].login,
			command,
			exception = null
		)

		val address = NetworkUtils.convert(params[0].address).hostAddress

		val isSudo = sudo || command.startsWith(SUDO)
		val cmd = if (sudo && !command.startsWith(SUDO))
			"$SUDO -S -p '' $command"
		else
			command

		try {
			var ret: String?
			var xcp: String?
			val jsch = JSch().also {
				it.addIdentity(params[0].host, sshKey, null, null)
			}
			jsch.getSession(params[0].login, address, 22).also { session ->
				session.setConfig(Properties().also { prop ->
					prop["StrictHostKeyChecking"] = "no"
				})
				session.connect(7000)

				(session.openChannel("exec") as ChannelExec).also { channel ->
					channel.setCommand(
						(if (caller.commands.patchPath)
							"export PATH=\$PATH:/$EXPECTED; $cmd"
						else
							cmd).also {
							Log.w(TAG, "Actual command: $it")
						}
					)

					val out = if (isSudo) channel.outputStream else null

					val err = ByteArrayOutputStream(1024)
					channel.setErrStream(err)

					ret = channel.inputStream.let {
						channel.connect()

						//TODO HERE - ask for password when SUDO
//						if (isSudo) {
//							out!!.write(KryptoUtils.convert(password + '\n'))
//							out.flush()
//						}

						it.bufferedReader().use(BufferedReader::readText)
					}
					xcp = ByteArrayInputStream(err.toByteArray()).bufferedReader().use(BufferedReader::readText)

					channel.disconnect()
				}
				session.disconnect()
			}
			return if (xcp.isNullOrBlank()) {
				Response(
					Status.OKAY,
					params[0].host,
					params[0].address,
					params[0].login,
					command,
					ret
				)
			} else {
				Response(
					Status.ERROR,
					params[0].host,
					params[0].address,
					params[0].login,
					command,
					"$ret\n$xcp"
				)
			}
		} catch (e: JSchException) {
			Log.w(TAG, e.message)
			return Response(
				params[0].host,
				params[0].address,
				params[0].login,
				command,
				e
			)
		} catch (e: Exception) {
			throw RuntimeException(e)
		}
	}

	enum class Status(val code: Int) {
		OKAY(0),
		ERROR(-1),
		CAUSED_BY(-2),
		EXCEPTION(-3),
		NO_ROUTE(-4),
		AUTH_FAIL(-5),
		CONNECTION_FAILED(-6),
		TIMED_OUT(-7),
		NO_WIFI(-8),
		NO_DB_RECORD(-9),
		UNREGISTERED(-10)
	}

	data class Response (
		val status: Status,
		val hostName: String,
		val address: Int,
		val login: String,
		val command: String?,
		val message: String?
	) {
		companion object {
			fun findStatus(e: Exception): Status {
				return when {
					(e.cause != null) -> {
						when {
							(e.cause is ConnectException) -> Status.CONNECTION_FAILED
							(e.cause is NoRouteToHostException) -> Status.NO_ROUTE //error = e.cause!!.message
							else -> Status.CAUSED_BY //e.cause!!.message
						}
					}
					(e.message?.startsWith("timeout") == true) -> Status.TIMED_OUT
					(e.message == "Auth fail") -> Status.AUTH_FAIL
					else -> {
						Status.EXCEPTION //error = e.message
					}
				}
			}

			fun errorMessage(context: Context?, status: Status, e: Exception?): String {
				return when (status) {
					Status.CONNECTION_FAILED,
					Status.TIMED_OUT -> context?.getString(R.string.message_timeout) ?: "Connection timed out"
					Status.AUTH_FAIL -> context?.getString(R.string.message_authfail) ?: "Authentication failed"
					Status.NO_WIFI -> context?.getString(R.string.message_nowifi) ?: "Wifi network not ready…"
					Status.NO_DB_RECORD -> context?.getString(R.string.message_record) ?: "Host record not found"
					else -> {
						e?.cause?.let {
							e.cause!!.message
						} ?: run {
							e?.message ?: context?.getString(R.string.message_nohost) ?: "No host connected"
						}
					}
				}
			}
		}

		constructor(status: Status, hostName: String, address: Int, login: String, command: String?, exception: JSchException?):
				this(status, hostName, address, login, command,
					errorMessage(
						null,
						status,
						exception
					)
				)

		constructor(hostName: String, address: Int, login: String, command: String?, exception: JSchException):
				this(findStatus(exception), hostName, address, login, command, exception)
	}
}