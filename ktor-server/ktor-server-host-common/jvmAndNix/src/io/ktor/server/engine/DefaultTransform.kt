/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.util.cio.toByteArray
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.Any
import kotlin.ByteArray
import kotlin.IllegalStateException
import kotlin.Long
import kotlin.String
import kotlin.arrayOf
import kotlin.check
import kotlin.let
import kotlin.text.*

private val ReusableTypes = arrayOf(ByteArray::class, String::class, Parameters::class)

/**
 * Default send transformation
 */
public fun ApplicationSendPipeline.installDefaultTransformations() {
    intercept(ApplicationSendPipeline.Render) { value ->
        val transformed = transformDefaultContent(value)
        if (transformed != null) proceedWith(transformed)
    }
}

/**
 * Default receive transformation
 */
public fun ApplicationReceivePipeline.installDefaultTransformations() {
    intercept(ApplicationReceivePipeline.Transform) { query ->
        val channel = query.value as? ByteReadChannel ?: return@intercept

        val transformed: Any? = when (query.typeInfo.type) {
            ByteReadChannel::class -> channel
            ByteArray::class -> channel.toByteArray()
            String::class -> channel.readText(
                charset = withContentType(call) { call.request.contentCharset() }
                    ?: Charsets.ISO_8859_1
            )
            else -> defaultPlatformTransformations(query)
        }
        if (transformed != null) {
            proceedWith(ApplicationReceiveRequest(query.typeInfo, transformed, query.typeInfo.type in ReusableTypes))
        }
    }
}

internal expect suspend fun PipelineContext<ApplicationReceiveRequest, ApplicationCall>.defaultPlatformTransformations(
    query: ApplicationReceiveRequest
): Any?

internal inline fun <R> withContentType(call: ApplicationCall, block: () -> R): R = try {
    block()
} catch (parseFailure: BadContentTypeFormatException) {
    throw BadRequestException(
        "Illegal Content-Type header format: ${call.request.headers[HttpHeaders.ContentType]}",
        parseFailure
    )
}

internal suspend fun ByteReadChannel.readText(
    charset: Charset
): String {
    val content = readRemaining(Long.MAX_VALUE)
    if (content.isEmpty) {
        return ""
    }

    return try {
        if (charset == Charsets.UTF_8 || charset == Charsets.ISO_8859_1) {
            content.readText()
        } else {
            content.readTextWithCustomCharset(charset)
        }

    } finally {
        content.release()
    }
}

internal expect fun ByteReadPacket.readTextWithCustomCharset(charset: Charset): String
