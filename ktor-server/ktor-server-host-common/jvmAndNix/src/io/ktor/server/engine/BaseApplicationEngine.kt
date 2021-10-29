/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.internal.*
import io.ktor.server.engine.internal.currentTimeMillisBridge
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import io.ktor.util.pipeline.*
import kotlinx.coroutines.*

/**
 * Base class for implementing [ApplicationEngine]
 *
 * It creates default engine pipeline, provides [application] property and installs default transformations
 * on respond and receive
 *
 * @param environment instance of [ApplicationEngineEnvironment] for this engine
 * @param pipeline pipeline to use with this engine
 */
public abstract class BaseApplicationEngine(
    public final override val environment: ApplicationEngineEnvironment,
    public val pipeline: EnginePipeline = defaultEnginePipeline(environment)
) : ApplicationEngine {

    /**
     * Configuration for the [BaseApplicationEngine]
     */
    public open class Configuration : ApplicationEngine.Configuration()

    protected val resolvedConnectors: CompletableDeferred<List<EngineConnectorConfig>> = CompletableDeferred()

    init {
        var isFirstLoading = true
        var initializedStartAt = currentTimeMillisBridge()
        BaseApplicationResponse.setupSendPipeline(pipeline.sendPipeline)
        environment.monitor.subscribe(ApplicationStarting) {
            if (!isFirstLoading) {
                initializedStartAt = currentTimeMillisBridge()
            }
            it.receivePipeline.merge(pipeline.receivePipeline)
            it.sendPipeline.merge(pipeline.sendPipeline)
            it.receivePipeline.installDefaultTransformations()
            it.sendPipeline.installDefaultTransformations()
            it.installDefaultInterceptors()
        }
        environment.monitor.subscribe(ApplicationStarted) {
            val finishedAt = currentTimeMillisBridge()
            val elapsedTimeInSeconds = (finishedAt - initializedStartAt) / 1_000.0
            if (isFirstLoading) {
                environment.log.info("Application started in $elapsedTimeInSeconds seconds.")
                isFirstLoading = false
            } else {
                environment.log.info("Application auto-reloaded in $elapsedTimeInSeconds seconds.")
            }
        }

        CoroutineScope(environment.application.coroutineContext).launch {
            resolvedConnectors.await().forEach {
                environment.log.info(
                    "Responding at ${it.type.name.lowercase(Locale.getDefault())}://${it.host}:${it.port}"
                )
            }
        }
    }

    @OptIn(InternalAPI::class)
    private fun Application.installDefaultInterceptors() {
        intercept(ApplicationCallPipeline.Setup) {
            call.response.pipeline.intercept(ApplicationSendPipeline.Before) {
                call.attributes.put(SendPipelineExecutedAttributeKey, Unit)
            }
        }
        intercept(ApplicationCallPipeline.Fallback) {
            val isResponded = call.attributes.getOrNull(SendPipelineExecutedAttributeKey) != null
            if (isResponded) {
                return@intercept
            }
            val status = call.response.status()
                ?: call.attributes.getOrNull(RoutingFailureStatusCode)
                ?: HttpStatusCode.NotFound
            call.respond(status)
        }

        intercept(ApplicationCallPipeline.Call) {
            verifyHostHeader()
        }
    }

    override suspend fun resolvedConnectors(): List<EngineConnectorConfig> {
        return resolvedConnectors.await()
    }
}

private val SendPipelineExecutedAttributeKey = AttributeKey<Unit>("SendPipelineExecutedAttributeKey")

private suspend fun PipelineContext<Unit, ApplicationCall>.verifyHostHeader() {
    val hostHeaders = call.request.headers.getAll(HttpHeaders.Host) ?: return
    if (hostHeaders.size > 1) {
        call.respond(HttpStatusCode.BadRequest)
        finish()
    }
}
