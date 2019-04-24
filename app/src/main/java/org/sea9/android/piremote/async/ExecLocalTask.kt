package org.sea9.android.piremote.async

import android.os.AsyncTask
import android.util.Log
import java.io.BufferedReader
import java.lang.RuntimeException

open class ExecLocalTask: AsyncTask<String, Void, String>() {
	companion object {
		const val TAG = "pi.exec_local"
	}

	override fun doInBackground(vararg params: String): String {
		if (params.isEmpty()) throw RuntimeException("Invalid execute-command task")

		var proc: Process? = null
		try {
			proc = ProcessBuilder().command(*params).start()
			return proc.inputStream.bufferedReader().use(BufferedReader::readText)
		} catch (e: Exception) {
			Log.w(TAG, e.message, e)
			throw RuntimeException(e)
		} finally {
			proc?.destroy()
		}
	}
}