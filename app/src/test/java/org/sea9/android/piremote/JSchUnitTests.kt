package org.sea9.android.piremote

import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchException
import org.junit.Test

import org.junit.Assert.*
import org.sea9.android.piremote.data.HostRecord
import java.io.*
import java.lang.RuntimeException
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class JSchUnitTests {
	@Test
	fun testResult() {
		val rst = "aaa\nbbb\nccc\n-=-=-\n111\n222\n333\n444"
		val lst = rst.split("-=-=-")
		System.out.println("'${lst[1].trim()}'")
		assertTrue(true)
	}

	@Test
	fun testExec_pwd() {
		val cmd = "pwd"
		val response = execute(
			"192.168.56.31",
			"paul",
			charArrayOf('a','b','c','d','1','2','3','4','5'),
			cmd, false)
//		val response = execute(
//			"192.168.14.20",
//			"pi",
//			charArrayOf('a','b','c','d','1','2','3','4','5'),
//			cmd, false)
		System.out.println("$cmd\n${response.message}")
		assertTrue(true)
	}

	@Test
	fun testExec_lsa() {
		val cmd = "ls -al"
		val response = execute(
			"192.168.56.31",
			"pi",
			charArrayOf('a','b','c','d','1','2','3','4','5'),
			cmd, false)
		System.out.println("$cmd\n${response.message}")
		assertTrue(true)
	}

	private fun execute(host: String, login: String, password: CharArray, command: String, sudo: Boolean): Response {
		try {
			var ret: String
			JSch().getSession(login, host, 22).also { session ->
				session.setPassword(convert(password))
				session.setConfig(Properties().also { prop ->
					prop["StrictHostKeyChecking"] = "no"
				})
				session.connect(30000)

				(session.openChannel("exec") as ChannelExec).also { channel ->
					channel.setCommand(
						if (sudo && !command.startsWith("sudo"))
							"sudo -S -p '' $command"
						else
							command
					)

					val out = if (sudo || command.startsWith("sudo")) channel.outputStream else null

					ret = channel.inputStream.let {
						channel.connect()

						if (sudo || command.startsWith("sudo")) {
							out!!.write(convert(password))
							out.flush()
						}

						it.bufferedReader().use(BufferedReader::readText)
					}
					channel.disconnect()
				}
				session.disconnect()
			}
			return Response(HostRecord(host, 0), command, ret)
		} catch (e: JSchException) {
			return Response(HostRecord(host, 0), command, e.message)
		} catch (e: Exception) {
			throw RuntimeException(e)
		}
	}

	data class Response(
		val host: HostRecord,
		val command: String?,
		val message: String?
	)

	private fun convert(input: CharArray): ByteArray? {
		var writer: OutputStreamWriter? = null
		return try {
			val output = ByteArrayOutputStream()
			writer = OutputStreamWriter(output, "UTF-8")
			writer.write(input)
			writer.flush()
			output.toByteArray()
		} catch (e: UnsupportedEncodingException) {
			throw RuntimeException(e)
		} catch (e: IOException) {
			throw RuntimeException(e)
		} finally {
			writer?.close()
		}
	}
}
