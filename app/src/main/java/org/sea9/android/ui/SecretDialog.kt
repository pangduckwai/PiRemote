package org.sea9.android.ui

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.sea9.android.piremote.R

class SecretDialog : DialogFragment() {
	companion object {
		const val TAG = "sea9.secret_dialog"
		const val MSG = "sea9.message"

		fun getInstance(message: String?, bundle: Bundle?) : SecretDialog {
			val instance = SecretDialog()
			instance.isCancelable = false

			val args = bundle ?: Bundle()
			message?.let {
				args.putString(MSG, it)
			}
			instance.arguments = args

			return instance
		}
	}

	private lateinit var textSecret: EditText
	private lateinit var buttonSubmit: Button

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val layout = inflater.inflate(R.layout.secret_dialog, container, false)

		textSecret = layout.findViewById(R.id.secret)
		textSecret.setOnEditorActionListener { _, actionId, _ ->
			when (actionId) {
				EditorInfo.IME_ACTION_DONE -> {
					submit()
				}
			}
			false
		}

		buttonSubmit = layout.findViewById(R.id.submit)
		buttonSubmit.setOnClickListener {
			submit()
		}

		(layout.findViewById(R.id.message) as TextView).apply {
			text = arguments?.getString(MSG)
		}

		dialog.setOnKeyListener { _, keyCode, event ->
			if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
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

	private fun submit() {
		val len = textSecret.text.length
		callback?.register(
			CharArray(len).apply {
				textSecret.text.getChars(0, len, this, 0)
			},
			arguments
		)
		dismiss()
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun register(secret: CharArray, bundle: Bundle?)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of SecretDialog.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}