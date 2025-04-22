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

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import ch.threema.app.R
import ch.threema.app.multidevice.LinkedDevicesAdapter.ListItem
import ch.threema.app.utils.ConfigUtils
import com.google.android.material.card.MaterialCardView

class LinkedDevicesAdapter(
    private val onClickedDevice: (LinkedDeviceInfoUiModel) -> Unit,
) : ListAdapter<ListItem, ViewHolder>(ListItemDiffCallback()) {
    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ListItem.DeviceAmountWarning -> ListItem.DeviceAmountWarning.VIEW_TYPE
        is ListItem.Device -> ListItem.Device.VIEW_TYPE
        else -> throw IllegalStateException("Unknown view type")
    }

    @Throws(IllegalStateException::class)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        when (viewType) {
            ListItem.DeviceAmountWarning.VIEW_TYPE -> MaxDevicesReachedWarningViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_max_devices_reached_warning, parent, false),
            )

            ListItem.Device.VIEW_TYPE -> DeviceViewHolder(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_linked_devices_list, parent, false),
            )

            else -> throw IllegalStateException("Unknown view type $viewType")
        }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val listItem: ListItem = getItem(position)
        when (viewHolder) {
            is MaxDevicesReachedWarningViewHolder -> viewHolder.bind(listItem as ListItem.DeviceAmountWarning)
            is DeviceViewHolder -> viewHolder.bind(
                listItem = listItem as ListItem.Device,
                onClickedDevice = onClickedDevice,
            )
        }
    }

    internal class MaxDevicesReachedWarningViewHolder(itemView: View) : ViewHolder(itemView) {
        private val reachedMaxDeviceCountTv: TextView
            get() = itemView.findViewById(R.id.reached_max_device_count_Tv)

        fun bind(listItem: ListItem.DeviceAmountWarning) {
            val maxDeviceSlotsWithoutSelf = (listItem.maxDeviceSlots - 1).coerceAtLeast(0)
            reachedMaxDeviceCountTv.text = itemView.context.resources.getQuantityString(
                R.plurals.md_max_device_slots_reached_info,
                maxDeviceSlotsWithoutSelf,
                maxDeviceSlotsWithoutSelf,
            )
        }
    }

    internal class DeviceViewHolder(itemView: View) : ViewHolder(itemView) {
        private val context: Context
            get() = itemView.context

        private val cardView: MaterialCardView
            get() = itemView as MaterialCardView

        private val platformIconIv: ImageView
            get() = itemView.findViewById(R.id.platformIconIv)

        private val firstLineTv: TextView
            get() = itemView.findViewById(R.id.firstLineTv)

        private val secondLineTv: TextView
            get() = itemView.findViewById(R.id.secondLineTv)

        private val thirdLineTv: TextView
            get() = itemView.findViewById(R.id.thirdLineTv)

        fun bind(listItem: ListItem.Device, onClickedDevice: (LinkedDeviceInfoUiModel) -> Unit) {
            cardView.apply {
                strokeWidth =
                    resources.getDimensionPixelSize(listItem.deviceInfo.getListItemStrokeWidth())
                strokeColor = ConfigUtils.getColorFromAttribute(
                    context,
                    listItem.deviceInfo.getListItemStrokeColor(),
                )
                setOnClickListener {
                    onClickedDevice(listItem.deviceInfo)
                }
            }

            platformIconIv.setImageResource(listItem.deviceInfo.getPlatformDrawable())
            platformIconIv.imageTintList = ColorStateList.valueOf(
                ConfigUtils.getColorFromAttribute(
                    context,
                    listItem.deviceInfo.getListItemStrokeColor(),
                ),
            )

            firstLineTv.text = listItem.deviceInfo.getLabelTextOrDefault(context)
            secondLineTv.text = listItem.deviceInfo.getPlatformDetailsTextOrDefault(context)
            thirdLineTv.text = listItem.deviceInfo.getFormattedTimeInfo(context)
        }
    }

    internal class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }

    sealed interface ListItem {
        data class DeviceAmountWarning(val maxDeviceSlots: Int) : ListItem {
            companion object {
                const val VIEW_TYPE = 0
            }
        }

        data class Device(val deviceInfo: LinkedDeviceInfoUiModel) : ListItem {
            companion object {
                const val VIEW_TYPE = 1
            }
        }
    }
}
