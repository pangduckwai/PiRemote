package org.sea9.android.piremote.data

import android.content.ContentValues
import android.provider.BaseColumns
import org.sea9.android.crypto.KryptoUtils
import org.sea9.android.core.DbHelper
import java.util.*

object DbContract {
	const val DB_NAME = "Pi.db_contract"
	const val DB_VERSION = 1
	const val PKEY = BaseColumns._ID
	const val COMMON_MODF = "modified"
	const val EMPTY = ""

	class Host : BaseColumns {
		companion object {
			const val TABLE = "Host"
			private const val COL_URL = "url"
			private const val COL_IPA = "ipAddr"
			private const val COL_USR = "user"
			private const val COL_SUS = "susr"
			private const val COL_PWD = "cred"
			private const val COL_SPW = "scrd"
			private const val IDX_HOST = "idxHost"

			const val SQL_CREATE =
					"create table $TABLE (" +
							"$PKEY integer primary key autoincrement," +
							"$COL_URL text not null COLLATE NOCASE," +
							"$COL_IPA integer not null," +
							"$COL_USR text not null," +
							"$COL_SUS text not null," +
							"$COL_PWD text not null," +
							"$COL_SPW text not null," +
							"$COMMON_MODF integer)"
			const val SQL_CREATE_IDX = "create unique index $IDX_HOST on $TABLE ($COL_URL)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_HOST"
			private const val WHERE_URL = "$COL_URL = ?"
			val COLUMNS = arrayOf(PKEY, COL_URL, COL_IPA, COL_USR, COL_SUS, COL_PWD, COL_SPW, COMMON_MODF)

			fun list(helper: DbHelper): List<HostRecord> {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, null, null, null, null, COL_URL)

				val result = mutableListOf<HostRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							result.add(
								HostRecord(
									getString(getColumnIndexOrThrow(COL_URL)),
									getInt(getColumnIndexOrThrow(COL_IPA))
								)
							)
						}
					}
				}
				return result
			}

			fun get(helper: DbHelper, url: String): Int? {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, WHERE_URL, arrayOf(url), null, null, null)

				cursor.use {
					with(it) {
						while (moveToNext()) {
							return getInt(getColumnIndexOrThrow(COL_IPA))
						}
					}
				}
				return null
			}

			fun get(helper: DbHelper, key: CharArray, url: String, isLogin: Boolean): CharArray? {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, WHERE_URL, arrayOf(url), null, null, null)

				var result: CharArray? = null
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val slt = getString(getColumnIndexOrThrow(if (isLogin) COL_SUS else COL_SPW))
							result = KryptoUtils.decrypt(
								getString(getColumnIndexOrThrow(if (isLogin) COL_USR else COL_PWD)).toCharArray(),
								key, KryptoUtils.decode(KryptoUtils.convert(slt.toCharArray())!!)
							)
						}
					}
				}
				return result
			}

			fun add(helper: DbHelper, key: CharArray, url: String, ipa: Int, usr: String, pwd: CharArray): Long {
				val susr = KryptoUtils.generateSalt()
				val cusr = KryptoUtils.encrypt(usr.toCharArray(), key, susr)
				val spwd = KryptoUtils.generateSalt()
				val cpwd = KryptoUtils.encrypt(pwd, key, spwd)

				val newRow = ContentValues().apply {
					put(COL_URL, url)
					put(COL_IPA, ipa)
					put(COL_USR, cusr?.joinToString(EMPTY))
					put(COL_SUS, KryptoUtils.convert(KryptoUtils.encode(susr))?.joinToString(EMPTY))
					put(COL_PWD, cpwd?.joinToString(EMPTY))
					put(COL_SPW, KryptoUtils.convert(KryptoUtils.encode(spwd))?.joinToString(EMPTY))
					put(COMMON_MODF, Date().time)
				}

				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun modify(helper: DbHelper, key: CharArray, url: String, ipa: Int?, usr: String?, pwd: CharArray?): Int {
				val newRow = ContentValues().apply {
					if (ipa != null) put(COL_IPA, ipa)
					if (usr != null) {
						val susr = KryptoUtils.generateSalt()
						put(COL_USR, KryptoUtils.encrypt(usr.toCharArray(), key, susr)?.joinToString(EMPTY))
						put(COL_SUS, KryptoUtils.convert(KryptoUtils.encode(susr))?.joinToString(EMPTY))
					}
					if (pwd != null) {
						val spwd = KryptoUtils.generateSalt()
						put(COL_PWD, KryptoUtils.encrypt(pwd, key, spwd)?.joinToString(EMPTY))
						put(COL_SPW, KryptoUtils.convert(KryptoUtils.encode(spwd))?.joinToString(EMPTY))
					}
					put(COMMON_MODF, Date().time)
				}

				return helper.writableDatabase.update(TABLE, newRow, WHERE_URL, arrayOf(url))
			}

			fun remove(helper: DbHelper, url: String): Int {
				return helper.writableDatabase.delete(TABLE, WHERE_URL, arrayOf(url))
			}
		}
	}

	class History : BaseColumns {
		companion object {
			private const val TABLE = "CommandHistory"
			private const val COL_CMD = "command"
			private const val IDX_CMD = "idxCommand"

			const val SQL_CREATE =
				"create table $TABLE (" +
						"$PKEY integer primary key autoincrement," +
						"$COL_CMD text not null COLLATE NOCASE," +
						"$COMMON_MODF integer)"
			const val SQL_CREATE_IDX = "create unique index $IDX_CMD on $TABLE ($COL_CMD)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_CMD"
			private const val WHERE_URL = "$COL_CMD = ?"
			private val COLUMNS = arrayOf(PKEY, COL_CMD, COMMON_MODF)

			fun list(helper: DbHelper): List<String> {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, null, null, null, null, "$COMMON_MODF desc")

				val result = mutableListOf<String>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							result.add(getString(getColumnIndexOrThrow(COL_CMD)))
						}
					}
				}
				return result
			}

			fun add(helper: DbHelper, command: String): Long {
				val newRow = ContentValues().apply {
					put(COL_CMD, command)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun modify(helper: DbHelper, command: String): Int {
				val newRow = ContentValues().apply {
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.update(TABLE, newRow, WHERE_URL, arrayOf(command))
			}

			fun remove(helper: DbHelper, command: String): Int {
				return helper.writableDatabase.delete(TABLE, WHERE_URL, arrayOf(command))
			}
		}
	}

	val SQL_CREATE = arrayOf(
		DbContract.Host.SQL_CREATE,
		DbContract.Host.SQL_CREATE_IDX,
		DbContract.History.SQL_CREATE,
		DbContract.History.SQL_CREATE_IDX
	)

	val SQL_DROP = arrayOf(
		DbContract.History.SQL_DROP_IDX,
		DbContract.History.SQL_DROP,
		DbContract.Host.SQL_DROP_IDX,
		DbContract.Host.SQL_DROP
	)

	const val SQL_INIT = "PRAGMA foreign_keys=OFF" //for DB needing foreign key use "PRAGMA foreign_keys=ON"
}