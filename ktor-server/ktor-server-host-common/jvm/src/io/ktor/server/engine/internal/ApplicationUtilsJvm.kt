/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine.internal

import io.ktor.server.application.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*

internal actual fun availableProcessorsBridge(): Int = Runtime.getRuntime().availableProcessors()

internal actual val Dispatchers.IOBridge: CoroutineDispatcher get() = IO

internal actual fun Throwable.initCauseBridge(cause: Throwable) {
    initCause(cause)
}

internal actual fun printError(message: Any?) {
    System.err.print(message)
}

internal actual fun configureShutdownUrl(environment: ApplicationEnvironment, pipeline: EnginePipeline) {
    environment.config.propertyOrNull("ktor.deployment.shutdown.url")?.getString()?.let { url ->
        pipeline.install(ShutDownUrl.EnginePlugin) {
            shutDownUrl = url
        }
    }
}
