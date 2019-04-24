package org.sea9.android.piremote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.AsyncTask
import android.util.Log
import org.sea9.android.piremote.conf.AsyncResponse
import java.net.Inet4Address
import java.net.InetAddress
import java.net.MalformedURLException
import java.net.URL

class NetworkUtils {
	companion object {
		const val TAG = "pi.network"
		const val DELIMITER = "."
		const val NEIGHBOR_PATTERN = "[012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9] "
		private const val IP_ADDR_PATTERN = "^[012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9]$"

		fun isIpAddress(input: String): Boolean {
			return IP_ADDR_PATTERN.toRegex() matches input
		}

		private fun convert(input: IntArray): ByteArray {
			return input.map {
				it.toByte()
			}.toByteArray()
		}

		private fun convert(input: ByteArray): IntArray {
			return input.map {
				it + (if (it < 0) 256 else 0)
			}.toIntArray()
		}

		fun convert(input: InetAddress): Int {
			return compact(convert(input.address))
		}

		fun convert(input: Int): InetAddress {
			return InetAddress.getByAddress(convert(expand(input)))
		}

		private fun expand(input: Int): IntArray {
			return intArrayOf(
				(input shr 24) and 0xff,
				(input shr 16) and 0xff,
				(input shr 8) and 0xff,
				input and 0xff
			)
		}

		fun compact(input: IntArray): Int {
			return if (input.size == 4) {
				(input[0] shl 24) + (input[1] shl 16) + (input[2] shl 8) + input[3]
			} else
				-1
		}

		fun parse(input: String): IntArray {
			return if (isIpAddress(input)) {
				input.split(DELIMITER).map {
					it.toInt()
				}.toIntArray()
			} else
				IntArray(0)
		}

		fun buildNetmask(size: Int, prefix: Int): Int {
			var result = 0
			repeat(prefix) {
				result += 1 shl ((size - 1) - it)
			}
			return result
		}

		/**
		 * Get network information.
		 */
		fun getNetworkInfo(caller: MainContext, update: Boolean): Boolean {
			val manager = caller.context?.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
			val capabilities = manager?.getNetworkCapabilities(manager.activeNetwork)
			val properties = manager?.getLinkProperties(manager.activeNetwork)
			if ((capabilities != null) && (properties != null) && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
				properties.routes.filter {
					(it.gateway is Inet4Address) && it.isDefaultRoute
				}.apply {
					caller.gateway = get(0).gateway
				}

				properties.linkAddresses.filter {
					it.address is Inet4Address
				}.apply {
					caller.netmask = get(0).prefixLength
					caller.address = get(0).address
					if (update) {
						caller.callback?.refreshUi()
					}
					return true
				}
			} else if (update) {
				caller.netmask = -1
				caller.address = null
			}
			return false
		}

		/**
		 * URL lookup
		 */
		fun lookupIpAddress(url: String, response: AsyncResponse) {
			AsyncIpLookupTask(response).execute(url)
		}
	}

	private class AsyncIpLookupTask(private val response: AsyncResponse): AsyncTask<String, Void, String>() {
		companion object {
			const val PREFIX = "no protocol"
		}
		override fun onPostExecute(result: String?) {
			response.onResponse(result)
		}
		override fun doInBackground(vararg params: String): String? {
			return if (params.size == 1) {
				try {
					InetAddress.getByName(URL(params[0]).host).hostAddress
				} catch (e: MalformedURLException) {
					if (e.message?.startsWith(PREFIX) == true) {
						try {
							InetAddress.getByName(URL("http://${params[0]}").host).hostAddress
						} catch (ex: MalformedURLException) {
							Log.w(TAG, e.message)
							null
						}
					} else {
						Log.w(TAG, e.message)
						null
					}
				}
			} else
				null
		}
	}
}