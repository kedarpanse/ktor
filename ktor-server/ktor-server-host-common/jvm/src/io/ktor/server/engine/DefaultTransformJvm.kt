/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.jvm.javaio.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import java.io.*

internal actual suspend fun PipelineContext<ApplicationReceiveRequest, ApplicationCall>.defaultPlatformTransformations(
    query: ApplicationReceiveRequest
) : Any? {
    val channel = query.value as? ByteReadChannel ?: return null

    return when (query.typeInfo.type) {
        InputStream::class -> receiveGuardedInputStream(channel)
        MultiPartData::class -> multiPartData(channel)
        Parameters::class -> {
            val contentType = withContentType(call) { call.request.contentType() }
            when {
                contentType.match(ContentType.Application.FormUrlEncoded) -> {
                    val string = channel.readText(charset = call.request.contentCharset() ?: Charsets.ISO_8859_1)
                    parseQueryString(string)
                }
                contentType.match(ContentType.MultiPart.FormData) -> {
                    Parameters.build {
                        multiPartData(channel).forEachPart { part ->
                            if (part is PartData.FormItem) {
                                part.name?.let { partName ->
                                    append(partName, part.value)
                                }
                            }

                            part.dispose()
                        }
                    }
                }
                else -> null // Respond UnsupportedMediaType? but what if someone else later would like to do it?
            }
        }
        else -> null
    }
}

@OptIn(InternalAPI::class)
private fun PipelineContext<*, ApplicationCall>.multiPartData(rc: ByteReadChannel): MultiPartData {
    val contentType = call.request.header(HttpHeaders.ContentType)
        ?: throw IllegalStateException("Content-Type header is required for multipart processing")

    val contentLength = call.request.header(HttpHeaders.ContentLength)?.toLong()
    return CIOMultipartDataBase(
        coroutineContext + Dispatchers.Unconfined,
        rc,
        contentType,
        contentLength
    )
}

internal actual fun ByteReadPacket.readTextWithCustomCharset(charset: Charset): String =
    inputStream().reader(charset).readText()

private fun receiveGuardedInputStream(channel: ByteReadChannel): InputStream {
    checkSafeParking()
    return channel.toInputStream()
}

private fun checkSafeParking() {
    check(safeToRunInPlace()) {
        "Acquiring blocking primitives on this dispatcher is not allowed. " +
            "Consider using async channel or " +
            "doing withContext(Dispatchers.IO) { call.receive<InputStream>().use { ... } } instead."
    }
}
