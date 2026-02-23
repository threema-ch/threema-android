package ch.threema.app.voip.groupcall

import androidx.annotation.AnyThread

interface GroupCallObserver {
    /**
     * Called when there is an update of a group call.
     * The cases when this is called might differ depending on the subscription used.
     *
     * If a subscription is made for a specific group it will be called whenever the state of this
     * group's call changes.
     *
     * If a subscription is made for joined calls this method will be called when a call is either joined
     * or left.
     *
     * @param call The description of the ongoing call or null if there is no ongoing call
     */
    @AnyThread
    fun onGroupCallUpdate(call: GroupCallDescription?)
}
