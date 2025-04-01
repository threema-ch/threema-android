/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2023-2025 Threema GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package ch.threema.domain.protocol.connection

import kotlinx.coroutines.channels.Channel

/**
 * A processable stream of messages that can be consumed by a handler
 * or transformed by piping it through a [PipeProcessor].
 *
 * Consuming a message means that the handler processes the message and does not emit it downstream.
 * Transforming a message means that the handler _can_ process the message and _can_ map it to
 * another format. When transforming a message it can also mean that the message is just passed on to
 * the next layer without any further processing or mapping of the data.
 */
internal interface Pipe<T> {
    fun setHandler(handler: PipeHandler<T>)

    /**
     * Transform the stream of messages by a [PipeProcessor]. A message can either be consumed or transformed.
     * If a message is consumed, the message will not be emitted downstream [Pipe].
     * A transformed message will be emitted downstream [Pipe]
     *
     * @param processor The processor handling this pipe's messages
     * @return a pipe consisting of the transformed (not consumed) messages
     */
    fun <O> pipeThrough(processor: PipeProcessor<T, O>): Pipe<O>
}

/**
 * A handler for messages that are emitted by a pipe.
 */
internal fun interface PipeHandler<T> {
    fun handle(msg: T)
}

/**
 * The source of a [Pipe]. Used where a [Pipe] starts e.g. the output of the socket connection to a
 * server or the output of a task manager.
 */
internal interface PipeSource<T> {
    val source: Pipe<T>
}

/**
 * The sink of a [Pipe] denotes an object that can process the output of a pipe e.g. the input to a
 * connection to a server or the input to a task manager.
 */
internal interface PipeSink<T> {
    val sink: PipeHandler<T>
}

/**
 * An [InputPipe] is a [Pipe] that can be used to send messages into the [Pipe] with it's [send] method.
 */
internal open class InputPipe<T> : Pipe<T> {
    private var handler: PipeHandler<T>? = null

    override fun setHandler(handler: PipeHandler<T>) {
        this.handler = handler
    }

    fun send(msg: T) {
        handler?.handle(msg)
    }

    override fun <O> pipeThrough(processor: PipeProcessor<T, O>): Pipe<O> {
        return processor.process(this)
    }
}

internal class ProcessingPipe<I, O>(private val handler: (I) -> Unit) : InputPipe<O>(),
    PipeProcessor<I, O> {

    override fun process(readable: Pipe<I>): Pipe<O> {
        readable.setHandler(handler)
        return this
    }
}

/**
 * A [PipeProcessor] that transforms every message using the [mapper].
 */
internal class MappingPipe<I, O>(private val mapper: (I) -> O) : PipeProcessor<I, O> {
    private val pipe = InputPipe<O>()

    override fun process(readable: Pipe<I>): Pipe<O> {
        readable.setHandler {
            pipe.send(mapper(it))
        }
        return pipe
    }
}

/**
 * A [QueuedPipeHandler] can be used to establish an asynchronous link to a pipe-chain.
 * Messages passed to [handle] will be sent to a queue and can later be retrieved using [take].
 */
internal class QueuedPipeHandler<T> : PipeHandler<T> {
    private val queue = Channel<T>(Channel.UNLIMITED)

    override fun handle(msg: T) {
        val result = queue.trySend(msg)
        result.getOrThrow()
    }

    suspend fun take(): T = queue.receive()
}

/**
 * A processor for a [Pipe]. A [PipeProcessor] can consume messages or transform and re-emit them.
 */
internal interface PipeProcessor<I, O> {
    /**
     * Setup the processing of a pipe's messages. Setting up the processing is a synchronous process
     * that should return without significant processing time
     * The actual processing of messages will take place when messages are received and in the scope
     * of the handling routine.
     *
     * @param readable The pipe that shall be processed
     * @return a [Pipe] of the transformed messages
     */
    fun process(readable: Pipe<I>): Pipe<O>
}

/**
 * A [PipeProcessor] that encodes outbound message e.g. applies transport encryption.
 */
internal interface OutboundPipeProcessor<I, O> {
    val encoder: PipeProcessor<I, O>
}

/**
 * A [PipeProcessor] that decodes inbound messages e.g. decrypts transport encryption.
 */
internal interface InboundPipeProcessor<I, O> {
    val decoder: PipeProcessor<I, O>
}
