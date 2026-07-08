package com.joat917.qrdecoderlite

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit Rule：将主协程调度器替换为 [TestDispatcher]，
 * 使 `viewModelScope.launch` 在 JVM 单元测试中可同步执行。
 */
class MainDispatcherRule @OptIn(ExperimentalCoroutinesApi::class) constructor(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * 在 JVM 单元测试中创建 [Uri] 实例的工具。
 *
 * `android.net.Uri` 在 android.jar 中是 stub 类，`Uri.parse()`
 * 返回 null。通过反射绕过构造函数限制，返回一个非 null 的
 * Uri 实例，使 ViewModel 的 `onImageSelected(uri)` 可触发扫描流程。
 */
object TestUriFactory {

    private val uriConstructor by lazy {
        Uri::class.java.getDeclaredConstructor().also { it.isAccessible = true }
    }

    /** 创建一个可用的测试用 URI 实例。 */
    fun create(): Uri = uriConstructor.newInstance()
}
