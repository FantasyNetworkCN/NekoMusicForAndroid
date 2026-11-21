package com.neko.music.data.lan

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** 网络受限或切换网络时按间隔重建发现会话，取消操作必须立即向上传递。 */
internal suspend fun retryLanNetworkOperation(
    retryDelayMillis: Long,
    onFailure: (Exception) -> Unit,
    operation: suspend () -> Unit,
) {
    require(retryDelayMillis >= 0)
    while (true) {
        currentCoroutineContext().ensureActive()
        try {
            operation()
            return
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            onFailure(error)
        } catch (error: SecurityException) {
            currentCoroutineContext().ensureActive()
            onFailure(error)
        }
        delay(retryDelayMillis)
    }
}
