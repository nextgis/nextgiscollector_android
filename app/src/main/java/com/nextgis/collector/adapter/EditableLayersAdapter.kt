/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018 NextGIS, info@nextgis.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextgis.collector.adapter

import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.nextgis.collector.databinding.ItemDirectoryBinding
import com.nextgis.collector.databinding.ItemEditableLayerBinding
import com.nextgis.maplibui.mapui.NGWVectorLayerUI


class EditableLayersAdapter(private var items: List<NGWVectorLayerUI>,
                            private var listener: OnItemClickListener) : RecyclerView.Adapter<EditableLayersAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        return if (viewType == 0) {
            val binding = ItemEditableLayerBinding.inflate(layoutInflater, parent, false)
            LayerViewHolder(binding)
        } else {
            val binding = ItemDirectoryBinding.inflate(layoutInflater, parent, false)
            DirViewHolder(binding)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].id > 0) 0 else 1
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], listener)

    override fun getItemCount(): Int = items.size

    interface OnItemClickListener {
        fun onGpsClick(layer: NGWVectorLayerUI)
        fun onMapClick(layer: NGWVectorLayerUI)
        fun onDirClick(id: String)
    }

    open class ViewHolder(binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        open fun bind(repo: NGWVectorLayerUI, listener: OnItemClickListener?) {

        }
    }

    class LayerViewHolder(private var binding: ItemEditableLayerBinding) : ViewHolder(binding) {
        override fun bind(repo: NGWVectorLayerUI, listener: OnItemClickListener?) {
            binding.layer = repo
            binding.byGps.setOnClickListener { listener?.onGpsClick(repo) }
            binding.byHand.setOnClickListener { listener?.onMapClick(repo) }
            binding.executePendingBindings()
        }
    }

    class DirViewHolder(private var binding: ItemDirectoryBinding) : ViewHolder(binding) {
        override fun bind(repo: NGWVectorLayerUI, listener: OnItemClickListener?) {
            binding.layer = repo
            binding.root.setOnClickListener { listener?.onDirClick(repo.path.name) }
            binding.executePendingBindings()
        }
    }
}