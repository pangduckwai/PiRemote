package org.sea9.android.piremote

import android.content.Context
import android.database.DatabaseUtils
import android.database.SQLException
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.util.Log

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*
import org.junit.BeforeClass
import org.sea9.android.core.ChangesTracker
import org.sea9.android.core.DbHelper
import org.sea9.android.core.RetainedContext
import org.sea9.android.piremote.data.DbContract

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class DbInstrumentedTest {
	companion object {
		private lateinit var context: Context
		private lateinit var helper: DbHelper
		private var rowCount = 0

		@BeforeClass
		@JvmStatic
		fun prepare() {
			context = InstrumentationRegistry.getTargetContext()
			helper = DbHelper(object : RetainedContext {
				override var dbHelper: DbHelper?
					get() = null
					set(value) {}

				override fun getContext(): Context? {
					return context
				}
				override fun onDbReady() {
					Log.w("woc.itest", "DB test connection ready")
				}
				override var changesTracker: ChangesTracker = ChangesTracker()

//				override fun setDbHelper(helper: DbHelper) {}
//				override fun getDbHelper(): DbHelper? {
//					return null
//				}
			}, DbContract.DB_NAME, DbContract.DB_VERSION, DbContract.SQL_CREATE, DbContract.SQL_DROP)
			helper.writableDatabase.rawQuery("select count(*) from Host", null, null)

			val hosts = DbContract.Host.list(helper)
			if (hosts.isNotEmpty()) {
				Log.w("pi.itest", "Cleaning up database...")
				helper.writableDatabase.beginTransactionNonExclusive()
				try {
					helper.writableDatabase.execSQL("delete from Host")
					helper.writableDatabase.setTransactionSuccessful()
				} finally {
					helper.writableDatabase.endTransaction()
				}
			}

			DbContract.Host.add(helper, "localhost", 0, "paul", "passw0rd".toByteArray())
			DbContract.Host.add(helper, "127.0.0.1", 1, "john", "qwerasdf".toByteArray())
			DbContract.Host.add(helper, "192.168.1.1", 2, "pete", "1qaz2wsx".toByteArray())
			DbContract.Host.add(helper, "192.168.256.254", 3, "bill", "12345678".toByteArray())
			rowCount = DatabaseUtils.queryNumEntries(helper.readableDatabase, "Host").toInt()
		}
	}

	@Test
	fun useAppContext() {
		// Context of the app under test.
		val appContext = InstrumentationRegistry.getTargetContext()
		assertEquals("org.sea9.android.piremote", appContext.packageName)
	}

	@Test
	fun testListRaw() {
		val cursor = helper.readableDatabase
			.query(DbContract.Host.TABLE, DbContract.Host.COLUMNS, null, null, null, null, "url")

		var found = false
		cursor.use {
			with(it) {
				while (moveToNext()) {
					val url = getString(getColumnIndexOrThrow("url"))
					val usr = getString(getColumnIndexOrThrow("user"))
					val pwd = getString(getColumnIndexOrThrow("cred"))
					Log.w("pi.itest_raw", "$url | $usr | $pwd")
					if (url == "localhost") found = true
				}
			}
		}
		assertTrue(found)
	}

	@Test
	fun testList() {
		val list = DbContract.Host.list(helper)
		var found = false
		list.forEach {
			Log.w("pi.itest_list", it.host)
			if (it.host == "localhost") found = true
		}
		assertTrue(found)
	}

	@Test
	fun testGet() {
		val list = DbContract.Host.list(helper)
		var found = false
		list.forEach {
			val p = DbContract.Host.getKey(helper, it.host)
			val q = DbContract.Host.getKey(helper, it.host)
			Log.w("pi.itest_get", "$it - ${p?.joinToString("")} / ${q?.joinToString("")}")
			if (it.host == "localhost") found = true
		}
		assertTrue(found)
	}

	@Test
	fun testModify() {
		val ret = DbContract.Host.modify(helper, "192.168.1.1", 999, "mary", "qwer1234".toByteArray())
		assertTrue(ret == 1)
	}

	@Test
	fun testRemove() {
		val ret = DbContract.Host.remove(helper, "127.0.0.1")
		val list = DbContract.Host.list(helper)
		Log.w("pi.itest_remove", "Records: ${list.size}")
		assertTrue((ret == 1) && (list.size == (rowCount - 1)))
	}

	@Test(expected = SQLException::class)
	fun testAdd() {
		DbContract.Host.add(helper, "localhost", 998, "eric", "passw0rd".toByteArray())
	}
}
