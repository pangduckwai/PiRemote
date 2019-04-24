package org.sea9.android.piremote

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.support.v7.app.AppCompatActivity
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlinx.android.synthetic.main.app_main.*
import org.sea9.android.core.ChangesTracker
import org.sea9.android.piremote.async.InitConnTask
import org.sea9.android.piremote.conf.AsyncResponse
import org.sea9.android.piremote.conf.ConfigDialog
import org.sea9.android.piremote.data.DbContract
import org.sea9.android.piremote.data.HostRecord
import org.sea9.android.ui.AboutDialog
import org.sea9.android.ui.MessageDialog

class MainActivity : AppCompatActivity(), MainContext.Callback, ConfigDialog.Callback, MessageDialog.Callback {
	companion object {
		const val TAG = "pi.main"
		const val KEY_HOST = "pi.host"
		const val KEY_QUICK = "pi.quick"
		const val EMPTY = ""
		const val MSG_DIALOG_NOTIFY = 0
		const val MSG_NO_WIFI = 1
	}

	private lateinit var retainedContext: MainContext
	private lateinit var spinnerHost: Spinner
	private lateinit var textAddrHost: TextView
	private lateinit var textAddrSelf: TextView
	private lateinit var textPath: TextView
	private lateinit var textCommand: EditText
	private lateinit var textMeasures: TextView
	private lateinit var textResponse: TextView
	private lateinit var progressBar: ProgressBar
	private lateinit var vibrator: Vibrator
	private lateinit var ibSett: ImageButton
	private lateinit var ibUndo: ImageButton
	private lateinit var ibEntr: ImageButton
	private lateinit var ibRfsh: ImageButton
	private lateinit var ibDown: ImageButton
	private lateinit var ibLeft: ImageButton
	private lateinit var ibRght: ImageButton
	private lateinit var ibUprr: ImageButton
	private lateinit var ibList: ImageButton

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		Log.d(TAG, "onCreate()")
		setContentView(R.layout.app_main)
		setSupportActionBar(toolbar)
		retainedContext = MainContext.getInstance(
			supportFragmentManager,
			"${getString(R.string.app_name)} v${packageManager.getPackageInfo(packageName, 0).versionName} - ${getString(R.string.app_copyright)}\n"
		)

		vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

		textCommand = findViewById(R.id.command)
		textCommand.setOnEditorActionListener { view, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_DONE -> {
					action(view, 2, view.text.toString())
					(view as EditText).setText(EMPTY)
				}
			}
			false
		}
		textCommand.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				retainedContext.commands.commandChanged = true
			}
		})
		findViewById<ImageButton>(R.id.command_clear).setOnClickListener {
			retainedContext.commands.currentCommand = null
			textCommand.setText(EMPTY)
			textCommand.requestFocus()
		}

		spinnerHost = findViewById(R.id.host)
		spinnerHost.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
			override fun onNothingSelected(parent: AdapterView<*>?) {}
			override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
				if (progressBar.visibility == View.INVISIBLE) { // is not busy
					retainedContext.hostAdaptor2.getItem(position)?.let {
						Log.w(TAG, "Spinner Item Selected!!!!!")
						hostSelected(it.host, it.address)
					}
				}
			}
		}

		textAddrHost = findViewById(R.id.address)
		textAddrSelf = findViewById(R.id.self)
		textPath = findViewById(R.id.path)
		textMeasures = findViewById(R.id.hardware)
		textResponse = findViewById(R.id.response)
		progressBar = findViewById(R.id.progressbar)

		ibSett = findViewById(R.id.action_settings)
		ibSett.setOnClickListener {
			ConfigDialog.getInstance().show(supportFragmentManager, ConfigDialog.TAG)
		}
		ibUndo = findViewById(R.id.action_undo)
		ibUndo.setOnClickListener {
			action(textCommand, 1)
		}
		ibEntr = findViewById(R.id.action_enter)
		ibEntr.setOnClickListener {
			action(it, 2, textCommand.text.toString())
		}
		ibRfsh = findViewById(R.id.action_refresh)
		ibRfsh.setOnClickListener {
			action(it, 3)
		}

		ibLeft = findViewById(R.id.action_left)
		ibLeft.setOnClickListener { action(it, 4) }
		ibRght = findViewById(R.id.action_right)
		ibRght.setOnClickListener { action(it, 5) }

		ibDown = findViewById(R.id.action_down)
		ibDown.setOnClickListener { action(it, 6) }
		ibUprr = findViewById(R.id.action_up)
		ibUprr.setOnClickListener { action(it, 7) }

		ibList = findViewById(R.id.action_list)
		ibList.setOnClickListener {action(it, 8) }

		fab.setOnClickListener { action(it, 0) } // Power off
	}

	override fun onPause() {
		super.onPause()
		isBusy(true)
	}

	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		// Inflate the menu; this adds items to the action bar if it is present.
		menuInflater.inflate(R.menu.menu_main, menu)
		return true
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return when (item.itemId) {
			R.id.action_about -> {
				AboutDialog.getInstance().show(supportFragmentManager, AboutDialog.TAG)
				true
			}
			else -> super.onOptionsItemSelected(item)
		}
	}

	/*=================
	 * Utility methods
	 */
	private fun action(view: View, actionId: Int, vararg commands: String) {
		if (retainedContext.commands.action(view, actionId, *commands))
			vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
		view.clearFocus()
		(getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
			.hideSoftInputFromWindow(view.windowToken, 0)
	}

	/*=====================================================
	 * @see org.sea9.android.piremote.MainContext.Callback
	 */
	override fun doNotify(message: String?) {
		doNotify(MSG_DIALOG_NOTIFY, message, false)
	}
	override fun doNotify(reference: Int, message: String?, stay: Boolean) {
		if (stay || ((message != null) && (message.length >= 70))) {
			when (reference) {
				MSG_NO_WIFI -> MessageDialog.getInstance(MSG_NO_WIFI, message!!, null).show(supportFragmentManager, MessageDialog.TAG)
				else -> MessageDialog.getInstance(MSG_DIALOG_NOTIFY, message!!, null).show(supportFragmentManager, MessageDialog.TAG)
			}
		} else {
			val obj = Toast.makeText(this, message, Toast.LENGTH_LONG )
			obj.setGravity(Gravity.TOP, 0, 0)
			obj.show()
		}
	}

	override fun onReady() {
		spinnerHost.adapter = retainedContext.hostAdaptor2
	}

	override fun refreshUi() {
		retainedContext.currentHost?.let {
			select(it.host)
			textAddrHost.text = NetworkUtils.convert(it.address).hostAddress
		}

		textAddrSelf.text = retainedContext.address?.hostAddress ?: EMPTY

		textCommand.setText(retainedContext.commands.currentCommand ?: EMPTY)

		textPath.text = retainedContext.navigator.currentPath ?: EMPTY

		textMeasures.text = retainedContext.hardwareStatus ?: getString(R.string.message_noinfo)

		retainedContext.consoleMessage.let {
			textResponse.text = retainedContext.consoleMessage
			textResponse.layout?.let {
				val first = it.getLineForVertical(textResponse.scrollY)
				val count = it.getLineForVertical(textResponse.scrollY + textResponse.height) - first
				if (it.lineCount >= count) {
					val txt = textResponse.text.substring(it.getLineEnd(it.lineCount - count - 2))
					textResponse.text = txt
					retainedContext.scrollConsole(txt)
				}
			}
		}
	}

	override fun isBusy(busy: Boolean) {
		if (busy) {
			progressBar.visibility = View.VISIBLE
			enable(ibSett, false)
			enable(ibUndo, false)
			enable(ibEntr, false)
			enable(ibRfsh, false)
			enable(ibDown, false)
			enable(ibLeft, false)
			enable(ibRght, false)
			enable(ibUprr, false)
			enable(ibList, false)
			enable(fab, false)
		} else {
			progressBar.visibility = View.INVISIBLE
			enable(ibSett, true)
			enable(ibUndo, true)
			enable(ibEntr, true)
			enable(ibRfsh, true)
			enable(ibDown, true)
			enable(ibLeft, true)
			enable(ibRght, true)
			enable(ibUprr, true)
			enable(ibList, true)
			enable(fab, true)
		}
	}

	private fun enable(button: ImageButton, isEnabled: Boolean) {
		button.isEnabled = isEnabled
		val drawable = button.drawable.mutate()
		if (isEnabled) {
			drawable.colorFilter = null
			button.setImageDrawable(drawable)
		} else {
			drawable.setColorFilter(Color.GRAY, PorterDuff.Mode.SRC_IN)
			button.setImageDrawable(drawable)
		}
	}

	private fun select(host: String) {
		for (index in 0..retainedContext.hostAdaptor2.count) {
			if (retainedContext.hostAdaptor2.getItem(index)?.host == host) {
				spinnerHost.setSelection(index)
				break
			}
		}
	}

	/*========================================
	 * @see org.sea9.android.ui.MessageDialog
	 */
	override fun neutral(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {
		when(reference) {
			MSG_NO_WIFI -> finish()
			else -> dialog?.dismiss()
		}
	}
	override fun positive(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {}
	override fun negative(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?) {}

	/*===========================================================
	 * @see org.sea9.android.piremote.conf.ConfigDialog.Callback
	 */
	override fun selectHost(host: String?): Pair<Int, CharArray>? {
		return (host ?: retainedContext.currentHost?.host)?.let {
			val address = DbContract.Host.get(retainedContext.dbHelper!!, it)
			val login = DbContract.Host.get(retainedContext.dbHelper!!, retainedContext.getKey(), it, true)
			if ((address != null) && (address != 0) && (login != null) && login.isNotEmpty()) {
				Pair(address, login)
			} else
				null
		} ?: run {
			null
		}
	}

	@SuppressLint("SetTextI18n")
	override fun saveSettings(host: String, addr: Int?, login: String?, password: CharArray?) {
		val ret = try {
			(DbContract.Host.add(retainedContext.dbHelper!!, retainedContext.getKey(), host, addr!!, login!!, password!!) >= 0)
		} catch (e: Exception) {
			(DbContract.Host.modify(retainedContext.dbHelper!!, retainedContext.getKey(), host, addr, login, password) == 1)
		}

		if (ret) {
			val address = addr ?: DbContract.Host.get(retainedContext.dbHelper!!, host)
			if (address != null) {
				retainedContext.populate()
				hostSelected(host, address)
			} else {
				doNotify(MSG_DIALOG_NOTIFY, "ERROR! failed to retrieve host record of '$host'", true) //should not reach here...
			}
		} else {
			doNotify(MSG_DIALOG_NOTIFY, getString(R.string.message_savefail), true)
		}
	}

	override fun hostSelected(host: String, addr: Int) {
		retainedContext.onHostSelected(host, addr)
		retainedContext.initializer.connect(false)
	}

	override fun getChangesTracker(): ChangesTracker {
		return retainedContext.changesTracker
	}

	override fun getInitializer(): InitConnTask {
		return retainedContext.initializer
	}

	override fun getCurrent(): HostRecord? {
		return retainedContext.currentHost
	}

	override fun getAdapter(): ArrayAdapter<HostRecord> {
		return retainedContext.hostAdaptor1
	}

	override fun doLookup(url: String?, response: AsyncResponse) {
		if (!url.isNullOrBlank()) {
			retainedContext.lookupIpAddress(url, response)
		}
	}

	override fun readConfig(): Bundle? {
		return retainedContext.readConfig()
	}

	override fun updateConfig(config: Bundle?) {
		if (config != null) {
			retainedContext.updateConfig(config)
		}
	}
}
