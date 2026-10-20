package com.music.bitchord

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun onlyUpdaterHasNetworkPermissionWhileMusicStaysBlocked() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val permissions = context.packageManager.getPackageInfo(
            context.packageName, android.content.pm.PackageManager.GET_PERMISSIONS,
        ).requestedPermissions.orEmpty()
        assertTrue(permissions.contains(android.Manifest.permission.INTERNET))
        assertTrue(permissions.contains(android.Manifest.permission.REQUEST_INSTALL_PACKAGES))
        assertFalse(com.music.bitchord.data.Http.networkRequestsEnabled)
        assertFalse(permissions.contains(android.Manifest.permission.ACCESS_NETWORK_STATE))
    }

    @Test
    fun strayAsyncNetworkRequestFailsWithoutCrashing() {
        assertFalse(com.music.bitchord.data.Http.networkRequestsEnabled)
        val completed = java.util.concurrent.CountDownLatch(1)
        val failure = java.util.concurrent.atomic.AtomicReference<java.io.IOException?>()
        val request = okhttp3.Request.Builder().url("https://example.invalid/").build()
        com.music.bitchord.data.Http.client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                failure.set(e)
                completed.countDown()
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.close()
                completed.countDown()
            }
        })
        assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
        assertNotNull(failure.get())
        assertTrue(failure.get()!!.message.orEmpty().contains("disabled"))
    }

    @Test
    fun useAppContext() {
        // The manifest application ID follows the selected build variant.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }
}