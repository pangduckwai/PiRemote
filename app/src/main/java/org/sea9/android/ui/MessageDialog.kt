package org.sea9.android.ui

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.KeyEvent
import org.sea9.android.piremote.R

class MessageDialog : DialogFragment() {
	companion object {
		const val TAG = "sea9.message_dialog"
		const val REF = "sea9.reference"
		const val FLG = "sea9.flags"
		const val MSG = "sea9.message"
		const val NEU = "sea9.neutral"
		const val POS = "sea9.positive"
		const val NEG = "sea9woc.negative"

		fun getInstance(reference: Int, bundle: Bundle?, buttons: Int, title: String?, message: String, neutral: String?, positive: String?, negative: String?) : MessageDialog {
			val instance = MessageDialog()
			instance.isCancelable = false

			val args = bundle ?: Bundle()
			var flag = 0
			args.putInt(REF, reference)
			args.putString(MSG, message)
			title?.let {
				args.putString(TAG, it)
			}
			neutral?.let {
				args.putString(NEU, it)
				flag += 1
			}
			positive?.let {
				args.putString(POS, it)
				flag += 2
			}
			negative?.let {
				args.putString(NEG, it)
				flag += 4
			}

			args.putInt(FLG, if (buttons > 0) buttons else if (flag == 0) 1 else flag)
			instance.arguments = args
			return instance
		}
		fun getInstance(reference: Int, message: String, bundle: Bundle?) : MessageDialog {
			return getInstance(reference, bundle, 1, null, message, null, null, null)
		}
		fun getOkayCancelDialog(reference: Int, message: String, bundle: Bundle?) : MessageDialog {
			return getInstance(reference, bundle, 6, null, message, null, null, null)
		}
	}

	override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
		val builder = AlertDialog.Builder(activity)

		val reference = arguments?.getInt(REF)
		builder.setMessage(arguments?.getString(MSG))
		arguments?.getString(TAG)?.let {
			builder.setTitle(it)
		}
		val neutral = arguments?.getString(NEU)?.let {
			it
		} ?: context?.getString(R.string.label_okay)
		val positive = arguments?.getString(POS)?.let {
			it
		} ?: context?.getString(R.string.label_okay)
		val negative = arguments?.getString(NEG)?.let {
			it
		} ?: context?.getString(R.string.label_cancel)

		var hasNeutral = false
		var hasNegative = false
		val flag = arguments?.getInt(FLG)
		flag?.let {
			if ((it and 1) > 0) {
				hasNeutral = true
				builder.setNeutralButton(neutral) { dialog, id -> callback?.neutral(dialog, id, reference!!, arguments) }
			}
			if ((it and 2) > 0) {
				builder.setPositiveButton(positive) { dialog, id -> callback?.positive(dialog, id, reference!!, arguments) }
			}
			if ((it and 4) > 0) {
				hasNegative = true
				builder.setNegativeButton(negative) { dialog, id -> callback?.negative(dialog, id, reference!!, arguments) }
			}
		}

		val ret = builder.create()
		ret.setOnKeyListener { _, keyCode, event ->
			if ((keyCode == KeyEvent.KEYCODE_BACK) && (event.action == KeyEvent.ACTION_UP)) {
				callback?.let {
					if (hasNegative)
						it.negative(null, -1, reference!!, arguments)
					else if (hasNeutral)
						it.neutral(null, -1, reference!!, arguments)
				}
				dismiss()
				true
			} else {
				false
			}
		}
		return ret
	}

	/*========================================
	 * Callback interface to the MainActivity
	 */
	interface Callback {
		fun neutral(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?)
		fun positive(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?)
		fun negative(dialog: DialogInterface?, which: Int, reference: Int, bundle: Bundle?)
	}
	private var callback: Callback? = null

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		try {
			callback = context as Callback
		} catch (e: ClassCastException) {
			throw ClassCastException("$context missing implementation of MessageDialog.Callback")
		}
	}

	override fun onDetach() {
		super.onDetach()
		callback = null
	}
}