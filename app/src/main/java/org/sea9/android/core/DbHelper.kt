package org.sea9.android.core

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.AsyncTask
import android.util.Log

class DbHelper(
		private val caller: RetainedContext,
		dbName: String, dbVersion: Int,
		private val sqlCreate: Array<String>,
		private val sqlDrop: Array<String>
): SQLiteOpenHelper(caller.getContext(), dbName, null, dbVersion) {
	companion object {
		const val TAG = "sea9.db_helper"

		fun initialize(
				caller: RetainedContext,
				dbName: String,
				dbVersion: Int,
				sqlInit: String,
				sqlCreate: Array<String>,
				sqlDrop: Array<String>) {
			InitDbTask(caller, dbName, dbVersion, sqlInit, sqlCreate, sqlDrop).execute()
		}
	}

	override fun onCreate(db: SQLiteDatabase) {
		sqlCreate.forEach {
			db.execSQL(it)
		}
		Log.i(TAG, "Database ${db.path} version ${db.version} created")
	}

	override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
		Log.i(TAG, "Upgrading database from version $oldVersion to $newVersion, which will destroy all old data")
		sqlDrop.forEach {
			db.execSQL(it)
		}
		onCreate(db)
	}

	fun deleteDatabase() {
		val db = writableDatabase
		val dbName = databaseName
		sqlDrop.forEach {
			db.execSQL(it)
		}
		caller.getContext()?.deleteDatabase(databaseName)
		Log.i(TAG, "Database $dbName deleted")
	}

	var ready: Boolean = false
		private set

	override fun close() {
		super.close()
		ready = false
	}

	override fun onOpen(db: SQLiteDatabase?) {
		super.onOpen(db)
		ready = true
		caller.onDbReady()
	}

	/*===================
	 * Initiate database
	 */
	private class InitDbTask (
			private val caller: RetainedContext,
			private val dbName: String,
			private val dbVersion: Int,
			private val sqlInit: String,
			private val sqlCreate: Array<String>,
			private val sqlDrop: Array<String>
	): AsyncTask<Void, Void, Void>() {
		override fun doInBackground(vararg params: Void?): Void? {
			val helper = DbHelper(caller, dbName, dbVersion, sqlCreate, sqlDrop)
			caller.dbHelper = helper
			helper.writableDatabase.execSQL(sqlInit)
			return null
		}
	}
}