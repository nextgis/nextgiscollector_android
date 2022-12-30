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

import androidx.databinding.ViewDataBinding
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.nextgis.collector.R.color
import com.nextgis.collector.data.Resource
import com.nextgis.collector.databinding.ItemDirectoryBinding
import com.nextgis.collector.databinding.ItemEditableLayerBinding
import com.nextgis.maplib.util.GeoConstants.*
import com.nextgis.maplibui.mapui.NGWVectorLayerUI


class EditableLayersAdapter(private var items: List<Resource>,
                            private var listener: OnItemClickListener,
                            private var layers: List<NGWVectorLayerUI>)
    : RecyclerView.Adapter<EditableLayersAdapter.ViewHolder>() {

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
        return if (items[position].type != "dir") 0 else 1
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], listener,
    layers)

    override fun getItemCount(): Int = items.size

    interface OnItemClickListener {
        fun onGpsClick(id: String, useMap : Boolean)
        fun onMapClick(id: String)
        fun onDirClick(id: String)
    }

    open class ViewHolder(binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        open fun bind(repo: Resource, listener: OnItemClickListener?, layer: List<NGWVectorLayerUI>) {

        }
    }

    class LayerViewHolder(private var binding: ItemEditableLayerBinding) : ViewHolder(binding) {
        override fun bind(repo: Resource, listener: OnItemClickListener?, layers : List<NGWVectorLayerUI>) {
            binding.layer = repo
            var layType = 0
            layType = layerByPath(repo.id, layers)?.geometryType!!
            if (layType == GTPoint ||layType == GTMultiPoint ||  layType == GTPolygon
                || layType == GTLineString || layType == GTMultiPolygon || layType == GTMultiLineString) {
                binding.byGps.setEnabled(true)

                binding.byGps.setOnClickListener { listener?.onGpsClick(repo.id,
                    layType != GTPoint && layType != GTMultiPoint )
                }
            } else {
                binding.byGps.setTextColor(binding.byGps.context.resources.getColor(color.colorDisabled))
                binding.byGps.setEnabled(false)
                binding.byGps.setOnClickListener { null }
            }
            binding.byHand.setOnClickListener { listener?.onMapClick(repo.id) }
//            binding.icon.setImageDrawable(repo.getIcon(repo.context))
            binding.executePendingBindings()
        }

        private fun layerByPath(id: String, layers: List<NGWVectorLayerUI>): NGWVectorLayerUI? {
            return layers.firstOrNull { it.path.name == id }
        }
    }

    class DirViewHolder(private var binding: ItemDirectoryBinding) : ViewHolder(binding) {
        override fun bind(repo: Resource, listener: OnItemClickListener?, layers: List<NGWVectorLayerUI>) {
            binding.layer = repo
            binding.root.setOnClickListener { listener?.onDirClick(repo.id) }
            binding.executePendingBindings()
        }
    }
}