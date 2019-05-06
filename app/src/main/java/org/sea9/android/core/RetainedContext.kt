package org.sea9.android.core

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.sea9.android.piremote.BuildConfig
import java.security.KeyPairGenerator
import java.security.KeyStore

interface RetainedContext {
	fun getContext(): Context?

	var changesTracker: ChangesTracker

	var dbHelper: DbHelper?

	fun onDbReady()
}