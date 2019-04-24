package org.sea9.android.ui

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.*
import android.widget.TextView
import org.sea9.android.piremote.R

class AboutDialog : DialogFragment() {
	companion object {
		const val TAG = "sea9.about"

		fun getInstance() : AboutDialog {
			val instance = AboutDialog()
			instance.isCancelable = false
			return instance
		}
	}

	private var version: String? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
		val view = inflater.inflate(R.layout.about_dialog, container, false)

		val txtVer = view.findViewById<TextView>(R.id.version)
		version?.let {
			txtVer.text = getString(R.string.app_version, it)
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
		return view
	}

	override fun onAttach(context: Context?) {
		super.onAttach(context)
		context?.let {
			version = it.packageManager.getPackageInfo(it.packageName, 0).versionName
		}
	}
}