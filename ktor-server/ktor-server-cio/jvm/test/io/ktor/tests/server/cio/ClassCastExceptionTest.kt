/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.cio

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.testing.suites.*
import io.ktor.server.util.*
import io.ktor.server.websocket.*
import kotlinx.coroutines.*
import java.util.concurrent.*
import kotlin.text.get
import kotlin.time.*
import kotlin.test.*

class ClassCastExceptionTest : EngineTestBase<CIOApplicationEngine, CIOApplicationEngine.Configuration>(io.ktor.server.cio.CIO) {
    init {
        enableSsl = false
    }

    /**
     * Regression test for KTOR-349
     */
    @Test
    @NoHttp2
    @OptIn(ExperimentalTime::class)
    fun testClassCastException(): Unit = runBlocking {
        val exceptionHandler = CoroutineExceptionHandler { _, cause ->
            cancel("Uncaught failure", cause)
        }
        val server = embeddedServer(
            io.ktor.server.cio.CIO,
            port = port,
            parentCoroutineContext = coroutineContext + exceptionHandler
        ) {
            install(WebSockets)

            routing {
                get("/hang") {
                    suspendCancellableCoroutine {}
                }
            }
        }

        server.start()
        try {
            delay(1000L)

            launch {
                HttpClient(CIO).use { client ->
                    try {
                        client.get { url(port = port, path = "/hang") }.body<String>()
                    } catch (e: Throwable) {
                    }
                }
            }

            delay(1000L)
        } finally {
            server.stop(1L, 1L, TimeUnit.SECONDS)
        }
    }
}
