package kashyap.`in`.cameraapplication.common

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import java.lang.ref.WeakReference

class SharedPrefUtils(private val context: Context) {

    companion object : SingletonHolder<SharedPrefUtils, Context>(::SharedPrefUtils)

    fun put(key: String, value: Any) {
        val contextWeakReference = WeakReference(context)
        if (contextWeakReference.get() != null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(contextWeakReference.get())
            val editor = prefs.edit()
            when (value) {
                is Int -> editor.putInt(key, value)
                is String -> editor.putString(key, value.toString())
                is Boolean -> editor.putBoolean(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Double -> editor.putLong(key, java.lang.Double.doubleToRawLongBits(value))
            }
            editor.apply()
        }
    }

    fun get(key: String, defaultValue: Any): Any? {
        val contextWeakReference = WeakReference(context)
        if (contextWeakReference.get() != null) {
            val sharedPrefs =
                PreferenceManager.getDefaultSharedPreferences(contextWeakReference.get())
            try {
                when (defaultValue) {
                    is String -> return sharedPrefs.getString(key, defaultValue.toString())
                    is Int -> return sharedPrefs.getInt(key, defaultValue)
                    is Boolean -> return sharedPrefs.getBoolean(key, defaultValue)
                    is Long -> return sharedPrefs.getLong(key, defaultValue)
                    is Float -> return sharedPrefs.getFloat(key, defaultValue)
                    is Double -> return java.lang.Double.longBitsToDouble(
                        sharedPrefs.getLong(
                            key,
                            java.lang.Double.doubleToLongBits(defaultValue)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("Exception: ", "" + e.message)
                return defaultValue
            }

        }
        return defaultValue
    }

    fun remove(key: String) {
        val contextWeakReference = WeakReference(context)
        if (contextWeakReference.get() != null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(contextWeakReference.get())
            val editor = prefs.edit()
            editor.remove(key)
            editor.apply()
        }
    }

    fun hasKey(key: String): Boolean {
        val contextWeakReference = WeakReference(context)
        if (contextWeakReference.get() != null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(contextWeakReference.get())
            return prefs.contains(key)
        }
        return false
    }
}