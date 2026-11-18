package com.musicclock.alarm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ManifestPrivacyTest {
    @Test
    fun appHasNoInternetPermissionAndDisablesBackup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val permissions = info.requestedPermissions.orEmpty().toSet()

        assertThat(permissions).doesNotContain(android.Manifest.permission.INTERNET)
        val applicationInfo = requireNotNull(info.applicationInfo)
        assertThat(applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }
}

