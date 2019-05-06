package org.sea9.android.piremote.data

import android.annotation.SuppressLint
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
			private const val COL_HOST = "url"
			private const val COL_ADDRESS = "ipAddr"
			private const val COL_LOGIN = "login"
			private const val COL_PRIV = "priv"
			private const val COL_INIT = "iv"
			private const val IDX_HOST = "idxHost"

			const val SQL_CREATE =
					"create table $TABLE (" +
							"$PKEY integer primary key autoincrement," +
							"$COL_HOST text not null COLLATE NOCASE," +
							"$COL_ADDRESS integer not null," +
							"$COL_LOGIN text not null," +
							"$COL_PRIV text," +
							"$COL_INIT text," +
							"$COMMON_MODF integer)"
			const val SQL_CREATE_IDX = "create unique index $IDX_HOST on $TABLE ($COL_HOST)"
			const val SQL_DROP = "drop table if exists $TABLE"
			const val SQL_DROP_IDX = "drop index if exists $IDX_HOST"
			private const val WHERE_URL = "$COL_HOST = ?"
			val COLUMNS = arrayOf(PKEY, COL_HOST, COL_ADDRESS, COL_LOGIN, COL_PRIV, COL_INIT, COMMON_MODF)

			fun list(helper: DbHelper): List<HostRecord> {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, null, null, null, null, COL_HOST)

				val result = mutableListOf<HostRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							result.add(
								HostRecord(
									getString(getColumnIndexOrThrow(COL_HOST)),
									getInt(getColumnIndexOrThrow(COL_ADDRESS)),
									getString(getColumnIndexOrThrow(COL_LOGIN))
								)
							)
						}
					}
				}
				return result
			}

			fun get(helper: DbHelper, url: String): HostRecord? {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, WHERE_URL, arrayOf(url), null, null, null)

				cursor.use {
					with(it) {
						while (moveToNext()) {
							return HostRecord(
								url,
								getInt(getColumnIndexOrThrow(COL_ADDRESS)),
								getString(getColumnIndexOrThrow(COL_LOGIN))
							)
						}
					}
				}
				return null
			}

			fun getKey(helper: DbHelper, url: String): ByteArray? {
				val cursor = helper.readableDatabase
					.query(TABLE, COLUMNS, WHERE_URL, arrayOf(url), null, null, null)

				var result: ByteArray? = null
				cursor.use {
					with(it) {
						while (moveToNext()) {
							val priv = getString(getColumnIndexOrThrow(COL_PRIV)).toCharArray()
							val init = getString(getColumnIndexOrThrow(COL_INIT)).toCharArray()
							result = KryptoUtils.decrypt(priv, init, url)
						}
					}
				}
				return result
			}

			fun add(helper: DbHelper, url: String, ipa: Int, usr: String): Long {
				val newRow = ContentValues().apply {
					put(COL_HOST, url)
					put(COL_ADDRESS, ipa)
					put(COL_LOGIN, usr)
					putNull(COL_PRIV)
					putNull(COL_INIT)
					put(COMMON_MODF, Date().time)
				}
				return helper.writableDatabase.insertOrThrow(TABLE, null, newRow)
			}

			fun modify(helper: DbHelper, url: String, ipa: Int?, usr: String?): Int {
				var count = 0
				val newRow = ContentValues().apply {
					if (ipa != null) {
						put(COL_ADDRESS, ipa)
						count ++
					}
					if (usr != null) {
						put(COL_LOGIN, usr)
						count ++
					}
					putNull(COL_PRIV)
					putNull(COL_INIT)
					put(COMMON_MODF, Date().time)
				}

				return if (count > 0)
					helper.writableDatabase.update(TABLE, newRow, WHERE_URL, arrayOf(url))
				else
					0
			}

			fun remove(helper: DbHelper, url: String): Int {
				return helper.writableDatabase.delete(TABLE, WHERE_URL, arrayOf(url))
			}

			fun registerHost(helper: DbHelper, url: String, key: ByteArray): Int {
				return KryptoUtils.encrypt(key, url)?.let {
					val newRow = ContentValues().apply {
						put(COL_PRIV, it.first.joinToString(EMPTY))
						put(COL_INIT, it.second.joinToString(EMPTY))
						put(COMMON_MODF, Date().time)
					}
					helper.writableDatabase.update(TABLE, newRow, WHERE_URL, arrayOf(url))
				} ?: -1
			}

			@SuppressLint("Recycle")
			fun registeredList(helper: DbHelper, url: String?): List<HostRecord> {
				val cursor = if (url == null) {
					helper.readableDatabase.query(
						TABLE,
						COLUMNS,
						"$COL_PRIV is not null and $COL_PRIV <> ?",
						arrayOf(EMPTY),
						null,
						null,
						COL_HOST
					)
				} else {
					helper.readableDatabase.query(
						TABLE,
						COLUMNS,
						"$COL_PRIV is not null and $COL_PRIV <> ? and $WHERE_URL",
						arrayOf(EMPTY, url),
						null,
						null,
						COL_HOST
					)
				}

				val result = mutableListOf<HostRecord>()
				cursor.use {
					with(it) {
						while (moveToNext()) {
							result.add(
								HostRecord(
									getString(getColumnIndexOrThrow(COL_HOST)),
									getInt(getColumnIndexOrThrow(COL_ADDRESS)),
									getString(getColumnIndexOrThrow(COL_LOGIN))
								)
							)
						}
					}
				}
				return result
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

			fun remove(helper: DbHelper): Int {
				return helper.writableDatabase.delete(TABLE, null, null)
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