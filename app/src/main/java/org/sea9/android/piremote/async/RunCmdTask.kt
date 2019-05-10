package org.sea9.android.piremote.async

import android.util.Log
import android.view.View
import android.widget.EditText
import org.sea9.android.piremote.data.DbContract
import org.sea9.android.piremote.data.HostRecord
import org.sea9.android.piremote.MainContext
import org.sea9.android.piremote.R

class RunCmdTask(private val caller: MainContext) {
	companion object {
		const val TAG = "pi.cmd"
		const val SUDO = "sudo"
		const val COMMAND_CD = "cd "
		const val COMMAND_MK = "mkdir "
		const val COMMAND_RM = "rm"
		const val COMMAND_MV = "mv"
		const val COMMAND_SHUTDOWN = "shutdown -h now"
	}

	var patchPath: Boolean = false

	var commandChanged: Boolean = false

	var currentCommand: String? = null

	private var lastCommand: String? = null

	private val commandList = mutableListOf<String>()
	fun populate() {
		val list = DbContract.History.list(caller.dbHelper!!)
		commandList.clear()
		commandList.addAll(list)
		position = -1
	}

	private var position = -1
	private fun next(): String? {
		return if ((commandList.size > 0) && (position < (commandList.size - 1))) {
			commandList[++position]
		} else
			null
	}
	private fun previous(): String? {
		return if ((commandList.size > 0) && (position > 0)) {
			commandList[--position]
		} else
			null
	}

	//TODO filter command with the allowed command list
	private fun execute(host: HostRecord, command: String, sudo: Boolean) {
		val isSudo = sudo || command.startsWith(SUDO)
		val cmd = if (isSudo) {
			if (command.startsWith(SUDO))
				"$SUDO -S -p 'SUDO Password' ${command.substring(SUDO.length)}"
			else
				"$SUDO -S -p 'SUDO Password' $command"
		} else
			command

		when {
			(command.startsWith(COMMAND_CD)) -> {
				caller.navigator.navigate(host, cmd)
			}
			(command.startsWith(COMMAND_MK) ||
			 command.startsWith(COMMAND_RM) ||
			 command.startsWith(COMMAND_MV)) -> {
				caller.navigator.execUpdateDir(cmd)
			}
			else -> {
				lastCommand = command //Remember the last command regardless if it is a valid command
				AsyncExecuteTask(caller, cmd, isSudo).execute(host)
			}
		}
	}
	private class AsyncExecuteTask(private val caller: MainContext, private val command: String, sudo: Boolean):
			ExecRemoteTask(caller,
				if (caller.navigator.currentPath != null)
					"cd \"${caller.navigator.currentPath}\"; $command"
				else
					command
				, sudo) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if (result.status == Status.OKAY) {
				//Update last command list after successful execution (unlike the lastCommand above)
				val ret = try {
					(DbContract.History.add(caller.dbHelper!!, command) >= 0)
				} catch (e: Exception) {
					(DbContract.History.modify(caller.dbHelper!!, command) == 1)
				}
				if (ret) {
					caller.commands.populate()
				}
			} else {
				Log.w(TAG, "Executing command $command result in error status ${result.status.code}")
			}

			if (!result.message.isNullOrBlank()) {
				caller.writeConsole(result.message)
			}

			caller.callback?.refreshUi()
			caller.callback?.isBusy(false)
			caller.commands.commandChanged = false
		}
	}

	private class AsyncPowerTask(private val caller: MainContext):
			ExecRemoteTask(caller,
				COMMAND_SHUTDOWN, true) {
		override fun onPreExecute() {
			caller.callback?.isBusy(true)
		}
		override fun onPostExecute(result: Response) {
			if (result.status == Status.OKAY) {
				if (!result.message.isNullOrBlank()) caller.writeConsole(result.message)
				caller.writeConsole(caller.getString(R.string.message_power, result.hostName))
			} else {
				caller.writeConsole("Unknown error powering off the host")
			}
			caller.callback?.refreshUi()
			caller.callback?.isBusy(false)
		}
	}

	fun action(view: View, actionId: Int, vararg commands: String): Boolean {
		var vib = false
		if ((caller.currentHost != null) && caller.currentHost!!.host.isNotBlank()) {
			when (actionId) {
				0 -> { //FAB
					caller.callback?.doNotify(caller.getString(R.string.message_power, caller.currentHost))
					AsyncPowerTask(caller).execute(caller.currentHost)
					vib = true
				}
				1 -> { //Undo
					if (commandChanged && !lastCommand.isNullOrBlank()) {
						(view as EditText).setText(lastCommand)
						commandChanged = false
						vib = true
					}
				}
				2 -> { //Enter
					if (commands.isNotEmpty() && commands[0].isNotBlank()) {
						execute(caller.currentHost!!, commands[0], false)
						currentCommand = null
						vib = true
					}
				}
				3 -> { //Refresh
					caller.navigator.currentPath = null
					caller.hardwareStatus = null
					caller.populate()
					caller.refresh()
					vib = true
				}
				4 -> { //Left
					caller.navigator.previous()?.let {
						currentCommand = "cd $it"
						caller.callback?.refreshUi()
						vib = true
					}
				}
				5 -> { //Right
					caller.navigator.next()?.let {
						currentCommand = "cd $it"
						caller.callback?.refreshUi()
						vib = true
					}
				}
				6 -> { //Down
					previous()?.let {
						currentCommand = it
						caller.callback?.refreshUi()
						commandChanged = false
						vib = true
					}
				}
				7 -> { //Up
					next()?.let {
						currentCommand = it
						caller.callback?.refreshUi()
						commandChanged = false
						vib = true
					}
				}
				8 -> { //List
					caller.navigator.list(caller.currentHost!!)
					vib = true
				}
				else -> {
					caller.callback?.doNotify(caller.getString(R.string.message_unknown))
				}
			}
		}
		return vib
	}
}