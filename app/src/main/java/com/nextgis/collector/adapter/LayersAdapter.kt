/*
 * Project:  NextGIS Collector
 * Purpose:  Light mobile GIS for collecting data
 * Author:   Stanislav Petriakov, becomeglory@gmail.com
 * *********************************************************************
 * Copyright (c) 2018, 2020 NextGIS, info@nextgis.com
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

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import com.nextgis.collector.R
import com.nextgis.collector.databinding.ItemLayerBinding
import com.nextgis.maplib.datasource.GeoEnvelope
import com.nextgis.maplib.map.Layer
import com.nextgis.maplibui.mapui.NGWRasterLayerUI
import com.nextgis.maplibui.mapui.NGWWebMapLayerUI
import com.nextgis.maplibui.mapui.RemoteTMSLayerUI


class LayersAdapter(private var items: List<Layer>,
                    private var listener: OnItemClickListener) : RecyclerView.Adapter<LayersAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemLayerBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position], listener)

    override fun getItemCount(): Int = items.size

    interface OnItemClickListener {
        fun onItemClick(layer: Layer)
        fun onDownloadTilesClick(layer: Layer)
    }

    class ViewHolder(private var binding: ItemLayerBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(repo: Layer, listener: OnItemClickListener?) {
            binding.layer = repo
            binding.visibility.setOnClickListener {
                repo.isVisible = !repo.isVisible
                repo.save()
                val on = R.drawable.ic_action_visibility_on_light
                val off = R.drawable.ic_action_visibility_off_light
                binding.visibility.setImageResource(if (repo.isVisible) on else off)
            }
            binding.downloadTiles.setOnClickListener { listener?.onDownloadTilesClick(repo) }
            binding.root.setOnClickListener { listener?.onItemClick(repo) }

            binding.executePendingBindings()
        }
    }
}