package com.joat917.qrdecoderlite

import android.graphics.PointF
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mockito

/**
 * JUnit Rule：将主协程调度器替换为 [TestDispatcher]，
 * 使 `viewModelScope.launch` 在 JVM 单元测试中可同步执行。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }
    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/**
 * 在 JVM 单元测试中创建 Android 框架类实例的工具。
 *
 * `android.net.Uri` 是抽象类，在 mockable jar 中无法直接实例化；
 * `android.graphics.PointF` 的构造器可能抛出 Stub! RuntimeException。
 * 本工厂统一用 Mockito 创建实例。
 */
object TestUriFactory {

    /** 创建一个测试用 [Uri] mock。 */
    fun create(): Uri = Mockito.mock(Uri::class.java)

    /** 创建测试用 [PointF]，优先直接构造，失败时回退到 Mockito + 反射设值。 */
    fun pointF(x: Float, y: Float): PointF =
        try {
            PointF(x, y)
        } catch (_: Exception) {
            Mockito.mock(PointF::class.java).also { p ->
                p::class.java.getField("x").setFloat(p, x)
                p::class.java.getField("y").setFloat(p, y)
            }
        }
}
