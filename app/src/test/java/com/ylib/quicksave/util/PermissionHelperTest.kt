package com.ylib.quicksave.util

import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq

/**
 * 测试 PermissionHelper.hasNotificationPermission。
 *
 * SDK_INT 通过替换 PermissionHelper.sdkIntProvider（internal lambda）注入，
 * 无需反射修改 Build.VERSION.SDK_INT。
 * ContextCompat.checkSelfPermission 通过 Mockito.mockStatic 拦截。
 */
class PermissionHelperTest {

    private lateinit var contextCompatMock: MockedStatic<ContextCompat>
    private val mockContext: android.content.Context =
        Mockito.mock(android.content.Context::class.java)

    @Before
    fun setUp() {
        contextCompatMock = Mockito.mockStatic(ContextCompat::class.java)
    }

    @After
    fun tearDown() {
        contextCompatMock.close()
        // 还原默认 provider，避免影响其他测试
        PermissionHelper.sdkIntProvider = { android.os.Build.VERSION.SDK_INT }
    }

    // -----------------------------------------------------------------------
    // Tests
    // -----------------------------------------------------------------------

    /**
     * Android 13（API 33，TIRAMISU）以下无需运行时通知权限，直接返回 true。
     * ContextCompat.checkSelfPermission 不应被调用。
     */
    @Test
    fun hasNotificationPermission_belowApi33_returnsTrue() {
        // 注入 API 32（Android 12L）
        PermissionHelper.sdkIntProvider = { 32 }

        val result = PermissionHelper.hasNotificationPermission(mockContext)

        assertTrue("API 32 应直接返回 true，无需检查权限", result)
        contextCompatMock.verifyNoInteractions()
    }

    /**
     * Android 13（API 33）且 POST_NOTIFICATIONS 已授予，应返回 true。
     */
    @Test
    fun hasNotificationPermission_api33_permissionGranted_returnsTrue() {
        // 注入 API 33（TIRAMISU）
        PermissionHelper.sdkIntProvider = { 33 }

        // 让 ContextCompat.checkSelfPermission 返回 PERMISSION_GRANTED
        contextCompatMock.`when`<Int> {
            ContextCompat.checkSelfPermission(
                any(),
                eq(android.Manifest.permission.POST_NOTIFICATIONS)
            )
        }.thenReturn(PackageManager.PERMISSION_GRANTED)

        val result = PermissionHelper.hasNotificationPermission(mockContext)

        assertTrue("API 33 且权限已授予时应返回 true", result)
    }
}
