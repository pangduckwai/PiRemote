package org.sea9.android.piremote.conf

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
import android.widget.*
import org.sea9.android.core.ChangesTracker
import org.sea9.android.piremote.MainActivity
import org.sea9.android.piremote.data.HostRecord
import org.sea9.android.piremote.async.InitConnTask
import org.sea9.android.piremote.NetworkUtils
import org.sea9.android.piremote.R

class ConfigDialog : DialogFragment(), AsyncResponse {
	companion object {
		const val TAG = "pi.settings"
		private const val EMPTY = ""

		fun getInstance() : ConfigDialog {
			val instance = ConfigDialog()
			instance.isCancelable = false
			return instance
		}
	}

	private lateinit var configLayout: View
	private lateinit var textHost: AutoCompleteTextView
	private lateinit var textAddr: EditText
	private lateinit var textLogin: EditText
	private lateinit var chkboxFast: CheckBox
	private lateinit var buttonSave: Button
	private lateinit var buttonRgst: Button

	override fun onResponse(response: String?) {
		textAddr.setText(response)
		textAddr.requestFocus()
		callback?.getChangesTracker()?.change(textAddr)
	}

	@SuppressLint("ClickableViewAccessibility")
	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val layout = inflater.inflate(R.layout.config_dialog, container, false)

		configLayout = layout.findViewById(R.id.config)

		textHost = layout.findViewById(R.id.host)
		callback?.getChangesTracker()?.register(textHost)
		textHost.setOnTouchListener { view, event ->
			// Show the drop down list whenever the view is touched
			when (event?.action) {
				MotionEvent.ACTION_DOWN -> {
					if (textHost.isFocusable) {
						textHost.showDropDown()
					}
				}
				MotionEvent.ACTION_UP -> view?.performClick()
			}
			false
		}
		textHost.setOnFocusChangeListener { view, hasFocus ->
			// Show the drop down list whenever the view gain focus
			if (hasFocus) {
				(context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
					.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
				view.postDelayed({
					textHost.showDropDown()
				}, 100)
			} else {
				selectHost(textHost.text.toString())
				view.clearFocus()
			}
		}
		textHost.onItemClickListener = AdapterView.OnItemClickListener { _, _, _, _ ->
			selectHost(textHost.text.toString())
		}
		layout.findViewById<ImageButton>(R.id.host_clear).setOnClickListener {
			textHost.setText(EMPTY)
			textHost.requestFocus()
		}
		layout.findViewById<ImageButton>(R.id.lookup).setOnClickListener {
			callback?.doLookup(textHost.text.toString(), this)
		}

		textAddr = layout.findViewById(R.id.address)
		callback?.getChangesTracker()?.register(textAddr)
		textAddr.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				if (!s.isNullOrBlank()) {
					buttonSave.isEnabled = true
					buttonRgst.isEnabled = false
					callback?.getChangesTracker()?.change(textAddr)
				}
			}
		})
		layout.findViewById<ImageButton>(R.id.address_clear).setOnClickListener {
			textAddr.setText(EMPTY)
			textAddr.requestFocus()
			callback?.getChangesTracker()?.clear(textAddr)
		}

		textLogin = layout.findViewById(R.id.login)
		callback?.getChangesTracker()?.register(textLogin)
		textLogin.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(s: Editable?) {
				if (!s.isNullOrBlank()) {
					buttonSave.isEnabled = true
					buttonRgst.isEnabled = false
					callback?.getChangesTracker()?.change(textLogin)
				}
			}
		})
		layout.findViewById<ImageButton>(R.id.login_clear).setOnClickListener {
			textLogin.setText(EMPTY)
			textLogin.requestFocus()
			callback?.getChangesTracker()?.clear(textLogin)
		}

//		textPassword = layout.findViewById(R.id.password)
//		callback?.getChangesTracker()?.register(textPassword)
//		textPassword.setOnEditorActionListener { view, actionId, _ ->
//			when (actionId) {
//				EditorInfo.IME_ACTION_DONE -> {
//					view?.clearFocus()
//					(context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
//						.hideSoftInputFromWindow(view.windowToken, 0)
//				}
//			}
//			false
//		}
//		textPassword.addTextChangedListener(object : TextWatcher {
//			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
//			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
//			override fun afterTextChanged(s: Editable?) {
//				if (!s.isNullOrBlank()) {
//					buttonSave.isEnabled = true
//					callback?.getChangesTracker()?.change(textPassword)
//				}
//			}
//		})
//		layout.findViewById<ImageButton>(R.id.password_clear).setOnClickListener {
//			textPassword.setText(EMPTY)
//			textPassword.requestFocus()
//			callback?.getChangesTracker()?.clear(textPassword)
//		}

		buttonSave = layout.findViewById(R.id.save)
		buttonSave.setOnClickListener {
			if (textHost.text.isEmpty() || textLogin.text.isEmpty()) {
				callback?.doNotify(getString(R.string.message_empty))
			} else if (!NetworkUtils.isIpAddress(textAddr.text.toString())) {
				callback?.doNotify(getString(R.string.message_ipaddr, textAddr.text.toString()))
			} else {
				val url = textHost.text.toString()
				val ipa = if (callback?.getChangesTracker()?.isChanged(textAddr) == true) {
					Log.d(TAG, "Address changed ${textAddr.text}")
					NetworkUtils.compact(
						NetworkUtils.parse(
							textAddr.text.toString()
						)
					)
				} else
					null
				val usr = if (callback?.getChangesTracker()?.isChanged(textLogin) == true) {
					Log.d(TAG, "Login changed")
					textLogin.text.toString()
				} else
					null

				if (callback?.saveSettings(url, ipa, usr) == true)
					dismiss()
				else {
					buttonSave.isEnabled = false
					buttonRgst.isEnabled = true
					callback?.getChangesTracker()?.clear()
					(context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
						.hideSoftInputFromWindow(textLogin.windowToken, 0)

					configLayout.requestFocus()
				}
			}
		}

		buttonRgst = layout.findViewById(R.id.register)
		buttonRgst.setOnClickListener {
			if (textHost.text.isEmpty() || textLogin.text.isEmpty()) {
				callback?.doNotify(getString(R.string.message_empty))
			} else if (!NetworkUtils.isIpAddress(textAddr.text.toString())) {
				callback?.doNotify(getString(R.string.message_ipaddr, textAddr.text.toString()))
			} else {
				val url = textHost.text.toString()
				val ipa = NetworkUtils.compact(
					NetworkUtils.parse(
						textAddr.text.toString()
					)
				)
				val usr = textLogin.text.toString()
				callback?.registerHost(url, ipa, usr)?.let {
					if (!it) {
						callback?.doNotify(getString(R.string.message_registered))
					} else {
						dismiss()
					}
				}
			}
		}

		chkboxFast = layout.findViewById(R.id.quick_start)
		chkboxFast.setOnCheckedChangeListener { _, isChecked ->
			val config = Bundle()
			config.putBoolean(MainActivity.KEY_QUICK, isChecked)
			callback?.updateConfig(config)
		}

		dialog.setOnKeyListener { _, keyCode, event ->
			if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
				if (callback?.getChangesTracker()?.isChanged(textHost) == true) {
					callback?.hostSelected(textHost.text.toString())
				}
				dismiss()
				true
			} else {
				false
			}
		}

		val win = dialog.window
		win?.requestFeature(Window.FEATURE_NO_TITLE)
		return layout
	}

	override fun onResume() {
		super.onResume()
		textHost.setAdapter(callback?.getAdapter())

		selectHost(callback?.getCurrent()?.host)
		setConfig(callback?.readConfig())
	}

	/*=================
	 * Utility methods
	 */
	private fun selectHost(host: String?) {
		callback?.selectHost(host)?.let {
			textHost.setText(host)
			textAddr.setText(NetworkUtils.convert(it.address).hostAddress)
			textLogin.setText(it.login)
			buttonSave.isEnabled = false
			buttonRgst.isEnabled = !it.registered
			callback?.getChangesTracker()?.clear()
			if (host != callback?.getCurrent()?.host) callback?.getChangesTracker()?.change(textHost)
			(context?.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
				.hideSoftInputFromWindow(textHost.windowToken, 0)
		} ?: run {
			if (!host.isNullOrBlank()) {
				callback?.doNotify(getString(R.string.message_new))
				textAddr.setText(EMPTY)
				textLogin.setText(EMPTY)
				buttonSave.isEnabled = true
				buttonRgst.isEnabled = false
				callback?.getChangesTracker()?.clear()
			}
		}
	}

	private fun setConfig(config: Bundle?) {
		chkboxFast.isChecked = config?.getBoolean(MainActivity.KEY_QUICK) ?: false
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun doNotify(message: String?)
		fun selectHost(host: String?): HostRecord?
		fun saveSettings(host: String, address: Int?, login: String?): Boolean
		fun registerHost(host: String, address: Int, login: String): Boolean
		fun hostSelected(host: String)
		fun getChangesTracker(): ChangesTracker
		fun getInitializer(): InitConnTask
		fun getCurrent(): HostRecord?
		fun getAdapter(): ArrayAdapter<HostRecord>
		fun doLookup(url: String?, response: AsyncResponse)
		fun readConfig(): Bundle?
		fun updateConfig(config: Bundle?)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of ConfigDialog.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}