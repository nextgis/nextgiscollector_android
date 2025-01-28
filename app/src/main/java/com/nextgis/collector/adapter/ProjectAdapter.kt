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

import android.app.AlertDialog
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import com.nextgis.collector.R
import com.nextgis.collector.data.Project
import com.nextgis.collector.databinding.ItemProjectBinding


class ProjectAdapter(private var items: ArrayList<Project>,
                     private var listener: OnItemClickListener) : RecyclerView.Adapter<ProjectAdapter.ViewHolder>() {
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = ItemProjectBinding.inflate(layoutInflater, parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], listener)

    }

    override fun getItemCount(): Int = items.size

    fun replaceData(list: ArrayList<Project>) {
        items = list
        notifyDataSetChanged()
    }

    interface OnItemClickListener {
        fun onItemClick(project: Project)
    }

    class ViewHolder(private var binding: ItemProjectBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(repo: Project, listener: OnItemClickListener?) {
            binding.project = repo
            if (listener != null) {
                binding.root.setOnClickListener { _ -> listener.onItemClick(repo) }
            }

            binding.btnMore.setOnClickListener( {
                var info = ""
                if (repo.description.isNotBlank())
                    info += repo.description + "\n\n"
                info += binding.btnMore.context.getString(R.string.project_version, repo.version)
                if (repo.url.isNotBlank()) {
                    val url = "${repo.url}/resource/${repo.ngwId}"
                    info += "\n" + binding.btnMore.context.getString(R.string.project_instance, url, url)
                }
                val ss = SpannableString(info); // msg should have url to enable clicking
                Linkify.addLinks(ss, Linkify.ALL);
                val builder = AlertDialog.Builder(binding.btnMore.context).setTitle(repo.title)
                    .setMessage(ss)
                    .setPositiveButton(R.string.ok, null)
                    .create()

                builder.show()
                val message = builder.findViewById<TextView>(android.R.id.message)
                if (message != null) {
                    message.movementMethod = LinkMovementMethod.getInstance()
                    message.linksClickable = true
                }
            })

            binding.executePendingBindings()
        }
    }
}