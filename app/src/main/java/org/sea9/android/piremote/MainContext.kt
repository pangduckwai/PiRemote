package org.sea9.android.piremote

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.util.Log
import android.widget.ArrayAdapter
import org.sea9.android.core.DbHelper
import org.sea9.android.core.ChangesTracker
import org.sea9.android.core.RetainedContext
import org.sea9.android.piremote.async.RunCmdTask
import org.sea9.android.piremote.async.InitConnTask
import org.sea9.android.piremote.async.NavDirTask
import org.sea9.android.piremote.conf.AsyncResponse
import org.sea9.android.piremote.data.DbContract
import org.sea9.android.piremote.data.HostRecord
import java.net.InetAddress

class MainContext: Fragment(), RetainedContext {
	companion object {
		const val TAG = "pi.retained"

		fun getInstance(sfm: FragmentManager, init: String): MainContext {
			var instance = sfm.findFragmentByTag(TAG) as MainContext?
			if (instance == null) {
				instance = MainContext()
				instance.consoleMessage = init
				sfm.beginTransaction().add(instance, TAG).commit()
			}
			return instance
		}
	}

	/*==================
	 * Database related
	 */
	override var dbHelper: DbHelper? = null

	private fun isDbReady(): Boolean {
		return ((dbHelper != null) && dbHelper!!.ready)
	}

	//@see org.sea9.android.core.DbHelper.Caller
	override fun onDbReady() {
		Log.d(TAG, "db ready")
		activity?.runOnUiThread {
			populateConfig()
			populate()
			callback?.onReady()
			if (!quickStartEnabled)
				refresh()
			else {
				populateCurrentHost()
				Handler().postDelayed({
					callback?.isBusy(false)
					Log.w(TAG, "Current: ${currentHost?.host}")
				}, 200)
			}
		}
	}

	/*=================
	 * Network related
	 */
	fun lookupIpAddress(url: String, response: AsyncResponse) {
		NetworkUtils.lookupIpAddress(url, response)
	}

	/*=======================
	 * Config dialog related
	 */
	override var changesTracker: ChangesTracker = ChangesTracker()

	private var quickStartEnabled: Boolean = true

	private fun populateConfig() {
		context?.let {
			it.getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)?.let { pref ->
				quickStartEnabled = pref.getBoolean(MainActivity.KEY_QUICK, true)
			}
		}
	}
	fun readConfig(): Bundle {
		val bundle = Bundle()
		bundle.putBoolean(MainActivity.KEY_QUICK, quickStartEnabled)
		return bundle
	}
	fun updateConfig(config: Bundle) {
		val quickStart = if (config.containsKey(MainActivity.KEY_QUICK))
			config.getBoolean(MainActivity.KEY_QUICK)
		else
			null

		if (quickStart != null) {
			context?.getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)?.let { prop ->
				with(prop.edit()) {
					putBoolean(MainActivity.KEY_QUICK, quickStart)
					apply()
				}
			}
		}
		quickStartEnabled = quickStart ?: true
	}

	/*=================
	 * Main UI related
	 */
	// Need 2 adapter because of stupid google's stupid array adapter.......
	lateinit var hostAdaptor1: ArrayAdapter<HostRecord> //For the AutoCompleteText
		private set
	lateinit var hostAdaptor2: ArrayAdapter<HostRecord> //For the Spinner
		private set

	var currentHost: HostRecord? = null
		private set

	private fun populateCurrentHost() {
		context?.let {
			it.getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)?.let { pref ->
				pref.getString(MainActivity.KEY_HOST, null)?.let { value ->
					currentHost = DbContract.Host.get(dbHelper!!, value)
					callback?.refreshUi()
				}
			}
		}
	}

	var gateway: InetAddress? = null
	var address: InetAddress? = null
	var netmask: Int = -1

	var hardwareStatus: String? = null

	lateinit var consoleMessage: String
		private set
	fun writeConsole(text: String?) {
		if (text != null) {
			consoleMessage += "\n$text"
		}
	}
	fun scrollConsole(text: String?) {
		if (text != null) consoleMessage = text
	}

	/*==================
	 * Background tasks
	 */
	lateinit var commands: RunCmdTask
		private set

	lateinit var navigator: NavDirTask
		private set

	lateinit var initializer: InitConnTask
		private set

	/*=================
	 * Utility methods
	 */
	fun onHostSelected(host: String, address: Int, login: String) {
		context?.getSharedPreferences(MainActivity.TAG, Context.MODE_PRIVATE)?.let { prop ->
			with(prop.edit()) {
				putString(MainActivity.KEY_HOST, host) // Remember current selected host
				apply()
			}
		}
		currentHost = HostRecord(host, address, login)
		callback?.refreshUi()
	}

	fun populate() {
		val list = DbContract.Host.list(dbHelper!!)
		hostAdaptor1.clear()
		hostAdaptor2.clear()
		hostAdaptor1.addAll(list)
		hostAdaptor2.addAll(list)

		commands.populate()
	}

	fun refresh() {
		if (currentHost == null) {
			populateCurrentHost()
			initializer.init()
		} else {
			initializer.connect(true)
		}
	}

	/*=====================================================
	 * Lifecycle method of android.support.v4.app.Fragment
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate() ${context?.packageName}")
		retainInstance = true

		context?.let {
			hostAdaptor1 = ArrayAdapter(it, android.R.layout.simple_list_item_1)
			hostAdaptor2 = ArrayAdapter(it, android.R.layout.simple_spinner_item)
			hostAdaptor2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
		}
		initializer = InitConnTask(this)
		commands = RunCmdTask(this)
		navigator = NavDirTask(this)

		if (!NetworkUtils.getNetworkInfo(this, true)) {
			callback?.doNotify(MainActivity.MSG_NO_WIFI, getString(R.string.message_nowifi), true)
		}
	}

	override fun onResume() {
		super.onResume()

		if (!isDbReady()) {
			Log.d(TAG, "onResume() - initializing DB")
			DbHelper.initialize(this, DbContract.DB_NAME, DbContract.DB_VERSION, DbContract.SQL_INIT, DbContract.SQL_CREATE, DbContract.SQL_DROP)
		} else {
			Log.d(TAG, "onResume() - resumed")
			callback?.isBusy(true)
			populateConfig()
			populate()
			callback?.onReady()
			callback?.refreshUi()
			Handler().postDelayed({
				callback?.isBusy(false)
			}, 200)
		}
	}

	override fun onDestroy() {
		Log.d(TAG, "onDestroy")
		if (dbHelper != null) {
			dbHelper!!.close()
			dbHelper = null
		}
		super.onDestroy()
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun doNotify(message: String?)
		fun doNotify(reference: Int, message: String?, stay: Boolean)
		fun onReady()
		fun refreshUi()
		fun isBusy(busy: Boolean)
	}
	var callback: Callback? = null
		private set

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of MainContext.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}