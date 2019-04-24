package org.sea9.android.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

interface RetainedContext {
	fun getContext(): Context?

	var changesTracker: ChangesTracker

	var dbHelper: DbHelper?

	fun onDbReady()

	@kotlin.Suppress("DEPRECATION")
	@android.annotation.SuppressLint("PackageManagerGetSignatures")
	fun getKey(): CharArray {
		var buffer = CharArray(0)
		getContext()?.let {
			val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
				it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo.apkContentsSigners
			} else {
				it.packageManager.getPackageInfo(it.packageName, PackageManager.GET_SIGNATURES).signatures
			}
			signatures.forEach {s ->
				buffer += s.toChars()
			}
		}
		return buffer
	}
}