package org.sea9.android.piremote.data

data class HostRecord(
	var host: String,
	var address: Int,
	var login: String,
	var registered: Boolean
) {
	override fun toString(): String {
		return host
	}
}