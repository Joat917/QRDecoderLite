package com.joat917.qrdecoderlite.ui.screen

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.joat917.qrdecoderlite.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 主界面的设备端测试：只验证静态呈现与按钮可用性。
 *
 * 三个入口点击后都会拉起系统界面（相册选择器、文件选择器、相机），
 * 那些属于系统行为，不在这里测试，由手工检查单覆盖。
 */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainScreen_showsLogoAndFourEntries() {
        composeRule.onNodeWithText("QRDecode").assertIsDisplayed()
        composeRule.onNodeWithText("从相册").assertIsDisplayed()
        composeRule.onNodeWithText("从文件").assertIsDisplayed()
        composeRule.onNodeWithText("从相机").assertIsDisplayed()
        composeRule.onNodeWithText("从历史").assertIsDisplayed()
    }

    @Test
    fun mainScreen_cameraEnabled_historyDisabled() {
        composeRule.onNodeWithText("从相机").assertIsEnabled()
        composeRule.onNodeWithText("从历史").assertIsNotEnabled()
    }
}
