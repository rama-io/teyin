package com.rama.puma.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.TextView
import com.rama.puma.R
import java.io.File

class DirectoryListAdapter(
    private val context: Context,
    private val onRemove: (String) -> Unit,
) : BaseAdapter() {

    private var paths: List<String> = emptyList()

    fun update(newPaths: List<String>) {
        paths = newPaths
        notifyDataSetChanged()
    }

    override fun getCount(): Int = paths.size
    override fun getItem(position: Int): String = paths[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_item_favorite_dir, parent, false)

        val path = paths[position]
        val file = File(path)

        view.findViewById<TextView>(R.id.dir_name).text = file.name.ifEmpty { "Storage" }
        view.findViewById<TextView>(R.id.dir_path).text = path

        view.findViewById<FrameLayout>(R.id.remove_favorite_btn).setOnClickListener {
            onRemove(path)
        }

        return view
    }
}
