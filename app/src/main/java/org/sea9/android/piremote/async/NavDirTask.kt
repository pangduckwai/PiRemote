package org.sea9.android.piremote.async

import android.os.AsyncTask
import org.sea9.android.piremote.data.HostRecord
import org.sea9.android.piremote.MainContext

class NavDirTask(private val caller: MainContext) {
	companion object {
		const val TAG = "pi.nav"
		const val COMMAND_DIR = "ls -ap | grep /"
		const val NEWLINE = "\n"
		const val DELIMIT = "-=-=-"
	}

	var currentPath: String? = null

	private val dirList = mutableListOf<String>()

	private var position = -1
	fun next(): String? {
		return if (dirList.size > 0) {
			position = if (position < (dirList.size - 1)) (position + 1) else 0
			dirList[position]
		} else
			null
	}
	fun previous(): String? {
		return if (dirList.size > 0) {
			position = if (position > 0) (position - 1) else (dirList.size - 1)
			dirList[position]
		} else
			null
	}

	private fun updateDirList(result: String) {
		dirList.clear()
		position = -1
		result.lines().let {
			if (it.isNotEmpty()) {
				currentPath = it[0]
			}

			dirList.addAll(it.filter { dir ->
				(dir.isNotEmpty()) && (dir != "./") && (dir.endsWith("/"))
			}.map { item ->
				val x = item.substring(0, item.length - 1)
				if (x.contains(' '))
					"\"$x\""
				else
					x
			})

			caller.writeConsole(dirList.joinToString("/$NEWLINE") + "/$NEWLINE")
			caller.callback?.refreshUi()
		}
	}

	fun navigate(host: HostRecord?, cd: String?) {
		AsyncNavTask(
			caller,
			if ((currentPath != null) && (cd != null) && (cd.startsWith(RunCmdTask.COMMAND_CD)))
				"cd \"$currentPath\"; $cd; pwd; $COMMAND_DIR"
			else
				"pwd; $COMMAND_DIR",
			false
		).executeOnExecutor(
			AsyncTask.THREAD_POOL_EXECUTOR,
			host ?: caller.currentHost
		)
	}
	private class AsyncNavTask(private val caller: MainContext, cd: String, sudo: Boolean):
			ExecRemoteTask(caller, cd, sudo) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if ((result.status == Status.OKAY) && (!result.message.isNullOrBlank())) {
				caller.navigator.updateDirList(result.message)
			}
			caller.callback?.isBusy(false)
		}
	}

	fun execUpdateDir(command: String) {
		if (currentPath != null) {
			AsyncAddRemoveTask(caller, command, false).execute(caller.currentHost)
		}
	}
	private class AsyncAddRemoveTask(private val caller: MainContext, command: String, sudo: Boolean):
		ExecRemoteTask(caller,
			"cd \"${caller.navigator.currentPath}\"; $command; pwd; $COMMAND_DIR"
			, sudo) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if ((result.status == Status.OKAY) && (!result.message.isNullOrBlank())) {
				caller.navigator.updateDirList(result.message)
			}
			caller.callback?.isBusy(false)
		}
	}

	fun list(host: HostRecord?) {
		if (currentPath != null) {
			AsyncListTask(caller, "ls -ap $currentPath", false)
				.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, host ?: caller.currentHost)
		} else {
			AsyncListTask(caller, "pwd; $COMMAND_DIR; echo \"$DELIMIT\"; ls -ap", true)
				.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, host ?: caller.currentHost)
		}
	}
	private class AsyncListTask(private val caller: MainContext, cmd: String, private val proc: Boolean):
			ExecRemoteTask(caller, cmd, false) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if (!result.message.isNullOrBlank()) {
				if (result.status == Status.OKAY) {
					val msg = if (proc) {
						val msgs = result.message.split(DELIMIT)
						if (msgs.size == 2) {
							caller.navigator.updateDirList(msgs[0].trim())
							msgs[1].trim()
						} else
							null
					} else {
						result.message
					}

					msg?.lines()?.let {
						val list = it.filter { item ->
							!item.endsWith("./") //skip . and ..
						}
						caller.writeConsole(list.joinToString(NEWLINE) + NEWLINE)
					}
				} else {
					caller.writeConsole(result.message)
				}
			} else {
				caller.writeConsole("Unknown error listing directory")
			}

			caller.callback?.refreshUi()
			caller.callback?.isBusy(false)
		}
	}
}