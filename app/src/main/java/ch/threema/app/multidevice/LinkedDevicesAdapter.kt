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

package ch.threema.app.multidevice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import ch.threema.app.R

class LinkedDevicesAdapter : RecyclerView.Adapter<LinkedDevicesAdapter.LinkedDevicesViewHolder>() {
    private val devices = mutableListOf<String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LinkedDevicesViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_linked_devices_list, parent, false)
        return LinkedDevicesViewHolder(view)
    }

    override fun onBindViewHolder(viewHolder: LinkedDevicesViewHolder, position: Int) {
        viewHolder.text.text = devices[position]
    }

    override fun getItemCount(): Int = devices.size

    fun setDevices(newDevices: List<String>) {
        val commonElementCount = minOf(newDevices.size, devices.size)

        if (commonElementCount > 0) {
            (0 until commonElementCount).forEach {
                if (devices[it] != newDevices[it]) {
                    devices[it] = newDevices[it]
                    notifyItemChanged(it)
                }
            }
        }

        if (newDevices.size < devices.size) {
            val count = devices.size - newDevices.size
            repeat(count) {
                devices.removeLast()
            }
            notifyItemRangeRemoved(newDevices.size, count)
        } else if (newDevices.size > devices.size) {
            val from = devices.size
            val to = newDevices.size
            (from until to).forEach {
                devices.add(newDevices[it])
            }
            notifyItemRangeInserted(from, to - from)
        }
    }

    class LinkedDevicesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val text: TextView = itemView.findViewById(R.id.text)
    }
}
