package ch.threema.app.tasks

import ch.threema.domain.taskmanager.ActiveTaskCodec
import ch.threema.domain.taskmanager.PassiveTaskCodec

interface ComposableTask<out R, in T> {
    suspend fun run(handle: T): R
}

interface ActiveComposableTask<out R> : ComposableTask<R, ActiveTaskCodec>

interface PassiveComposableTask<out R> : ComposableTask<R, PassiveTaskCodec>
