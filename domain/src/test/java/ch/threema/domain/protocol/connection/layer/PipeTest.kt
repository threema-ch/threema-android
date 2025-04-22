/*  _____ _
 * |_   _| |_  _ _ ___ ___ _ __  __ _
 *   | | | ' \| '_/ -_) -_) '  \/ _` |_
 *   |_| |_||_|_| \___\___|_|_|_\__,_(_)
 *
 * Threema for Android
 * Copyright (c) 2024-2025 Threema GmbH
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

package ch.threema.domain.protocol.connection.layer

import ch.threema.domain.protocol.connection.InputPipe
import ch.threema.domain.protocol.connection.MappingPipe
import ch.threema.domain.protocol.connection.PipeCloseHandler
import ch.threema.domain.protocol.connection.PipeHandler
import ch.threema.domain.protocol.connection.PipeSink
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking

class PipeTest {
    @Test
    fun `close signal must be propagated through pipes`() {
        // Arrange
        val sinkChannel = Channel<Long>(capacity = Channel.UNLIMITED)

        val source: InputPipe<Short, Unit> = InputPipe()
        val shortProcessor = MappingPipe<Short, Int, Unit>(Short::toInt)
        val intProcessor = MappingPipe<Int, Long, Unit>(Int::toLong)
        val sink = TestSink<Long, Unit>(sinkChannel)

        source
            .pipeThrough(shortProcessor)
            .pipeThrough(intProcessor)
            .pipeInto(sink)

        // Act
        source.send(0)
        source.send(1)
        source.send(2)
        source.send(3)
        source.close(Unit)

        // Assert
        val pipedValues = runBlocking { sinkChannel.consumeAsFlow().toList() }
        assertContentEquals(listOf(0, 1, 2, 3), pipedValues)
    }
}

/**
 * This sink sends all received values into the given channel and closes it when handling a close
 * event.
 */
private class TestSink<T, C>(private val channel: Channel<T>) : PipeSink<T, C> {
    override val sink: PipeHandler<T> = PipeHandler(channel::trySend)
    override val closeHandler: PipeCloseHandler<C> = PipeCloseHandler { channel.close() }
}
