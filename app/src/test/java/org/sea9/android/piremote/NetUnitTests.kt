package org.sea9.android.piremote

import org.junit.Assert
import org.junit.Test

class NetUnitTests {
	companion object {
		const val DELIMITER = "."
		const val NEIGHBOR_PATTERN = "[012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9] "
		const val IP_ADDR_PATTERN = "^[012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9][.][012]?[0-9]?[0-9]$"
	}

	@Test
	fun test_sign() {
		val a = 192
		val b = a.toByte()
		val c = b.toInt() + 256
		System.out.println(">>>>>>>>>>>>> $a $b $c")
		Assert.assertTrue(a == c)
	}

	@Test
	fun test_compact() {
		val ip1 = intArrayOf(192, 168, 7, 114)
		val ip2 = compact(ip1)
		System.out.println("********* $ip2")
		Assert.assertTrue(ip2 != 0)
	}

	@Test
	fun testRegex_Ip() {
		val pattern = IP_ADDR_PATTERN.toRegex()
		val result1 = (
			(pattern matches "192.168.137.1") &&
			(pattern matches "12.168.17.144") &&
			(pattern matches "192.168.137.299")
		)
		val result2 = (
			(pattern matches "192.168.137.300")
		)
		Assert.assertTrue(result1 && !result2)
	}

	@Test
	fun testRegex_Neighbor() {
		val regex = NEIGHBOR_PATTERN.toRegex()
		val result = regex.findAll(
			"192.168.137.1 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"10.168.17.144 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"192.0.45.133 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"192.168.137.299 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"192.168.137.300 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"0.0.0.0 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"1.2.3.4 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"255.255.255.255 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n" +
				"192.168.07.09 dev wlan0 lladdr a2:af:bd:f0:c6:20 REACHABLE\n"
		).map { match ->
			match.value.trim().split(DELIMITER).map { value ->
				value.toInt()
			}
		}.filter {
			(it[0] < 256) && (it[1] < 256) && (it[2] < 256) && (it[3] < 256)
		}

		result.forEach {
			it.forEach { value ->
				System.out.print(" $value")
			}
			System.out.println()
		}
	}

	@Test
	fun test_IpConvert() {
		val orig1 = "192.168.14.137"
		val rslt1 = toString(expand(compact(parse(orig1))))
		System.out.println("$$$ $rslt1")
		val orig2 = "10.0.134.143"
		val rslt2 = toString(expand(compact(parse(orig2))))
		System.out.println("$$$ $rslt2")
		val orig3 = "10.0.0.3"
		val rslt3 = toString(expand(compact(parse(orig3))))
		System.out.println("$$$ $rslt3")
		Assert.assertTrue((orig1 == rslt1) && (orig2 == rslt2) && (orig3 == rslt3))
	}

	@Test
	fun test_netmask() {
		val test = 0x01 shl 30
		System.out.println("Shift 30: $test")

		val ip01 = compact(intArrayOf(192, 168, 7, 7))
		val ip02 = compact(intArrayOf(192, 168, 7, 125))
		val ip03 = compact(intArrayOf(192, 168, 7, 135))
		val ip04 = compact(intArrayOf(192, 168, 7, 251))
		val mask = buildNetmask(32, 25)
		System.out.println(String.format("Netmask: %x", mask))
		System.out.println(String.format("IPAddr1: %x - %x", ip01, ip01 and mask))
		System.out.println(String.format("IPAddr2: %x - %x", ip02, ip02 and mask))
		System.out.println(String.format("IPAddr3: %x - %x", ip03, ip03 and mask))
		System.out.println(String.format("IPAddr4: %x - %x", ip04, ip04 and mask))
		Assert.assertTrue(
			((ip01 and mask) == (ip02 and mask)) &&
			((ip03 and mask) == (ip04 and mask)) &&
			((ip01 and mask) != (ip03 and mask))
		)
	}

	private fun buildNetmask(size: Int, prefix: Int): Int {
		var result = 0
		repeat(prefix) {
			result += 1 shl ((size - 1) - it)
		}
		return result
	}

	private fun isIpAddress(input: String): Boolean {
		return IP_ADDR_PATTERN.toRegex() matches input
	}

	private fun toString(input: IntArray): String {
		return String.format("%d.%d.%d.%d", input[0], input[1], input[2], input[3])
	}

	private fun expand(input: Int): IntArray {
		return intArrayOf(
			(input shr 24) and 0xff,
			(input shr 16) and 0xff,
			(input shr 8) and 0xff,
			input and 0xff
		)
	}

	private fun compact(input: IntArray): Int {
		return if (input.size == 4) {
			(input[0] shl 24) + (input[1] shl 16) + (input[2] shl 8) + input[3]
		} else
			-1
	}

	private fun parse(input: String): IntArray {
		return if (isIpAddress(input)) {
			input.split(DELIMITER).map {
				it.toInt()
			}.toIntArray()
		} else
			IntArray(0)
	}
}