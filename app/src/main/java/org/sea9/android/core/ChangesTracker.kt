package org.sea9.android.core

import android.view.View

class ChangesTracker {
	companion object {
		const val TAG = "sea9.changes"
	}

	private val controls = mutableMapOf<View, Boolean>()

	fun register(view: View): View? {
		return if (!controls.containsKey(view)) {
			controls[view] = false
			view
		} else
			null
	}

	fun isChanged(): Boolean {
		controls.entries.forEach {
			if (it.value) return true
		}
		return false
	}

	fun isChanged(view: View): Boolean {
		return controls[view] ?: false
	}

	fun change(view: View): View? {
		return if (controls.containsKey(view)) {
			controls[view] = true
			view
		} else
			null
	}

	fun clear() {
		controls.keys.forEach {
			controls[it] = false
		}
	}

	fun clear(view: View): View? {
		return if (controls.containsKey(view)) {
			controls[view] = false
			view
		} else
			null
	}
}