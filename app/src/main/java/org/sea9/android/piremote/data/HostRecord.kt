package org.sea9.android.piremote.data

data class HostRecord(
	var host: String,
	var address: Int
) {
	override fun toString(): String {
		return host
	}
}