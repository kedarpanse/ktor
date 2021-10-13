/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*

internal actual suspend fun PipelineContext<ApplicationReceiveRequest, ApplicationCall>.defaultPlatformTransformations(
    query: ApplicationReceiveRequest
): Any? = null

internal actual fun ByteReadPacket.readTextWithCustomCharset(charset: Charset): String =
    error("Charset $charset is not supported")
