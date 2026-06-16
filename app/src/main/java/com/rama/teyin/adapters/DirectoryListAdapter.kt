package com.rama.teyin.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.rama.bohio.objects.PrefFontStyle
import com.rama.teyin.R
import com.rama.bohio.R as BohioR
import com.rama.teyin.managers.FontManager
import com.rama.teyin.managers.PrefsManager
import com.rama.teyin.managers.ThemeManager

sealed class DirEntry {
    /** Non-deletable system entry (root, storage, standard folder) or removable (USB). */
    data class Fixed(
        val label: String,
        val path: String,
        val iconRes: Int,
        val removable: Boolean = false,
    ) : DirEntry()

    /** Visual separator between fixed and user-added sections. */
    object Divider : DirEntry()

    /** User-bookmarked folder has a remove button. */
    data class UserAdded(val path: String) : DirEntry()
}

class DirectoryListAdapter(
    private val context: Context,
    private val onRemove: (String) -> Unit,
) : BaseAdapter() {

    private var entries: List<DirEntry> = emptyList()

    fun update(newEntries: List<DirEntry>) {
        entries = newEntries
        notifyDataSetChanged()
    }

    override fun getCount(): Int = entries.size
    override fun getItem(position: Int): DirEntry = entries[position]
    override fun getItemId(position: Int): Long = position.toLong()
    override fun areAllItemsEnabled(): Boolean = false
    override fun isEnabled(position: Int): Boolean = entries[position] !is DirEntry.Divider

    companion object {
        private const val VIEW_FIXED = 0
        private const val VIEW_DIVIDER = 1
        private const val VIEW_USER = 2
    }

    override fun getViewTypeCount(): Int = 3

    override fun getItemViewType(position: Int): Int = when (entries[position]) {
        is DirEntry.Fixed -> VIEW_FIXED
        is DirEntry.Divider -> VIEW_DIVIDER
        is DirEntry.UserAdded -> VIEW_USER
    }

    /** Returns the navigable path for a position, or null for dividers. */
    fun pathAt(position: Int): String? = when (val e = entries[position]) {
        is DirEntry.Fixed -> e.path
        is DirEntry.UserAdded -> e.path
        is DirEntry.Divider -> null
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)
        val entry = entries[position]

        val view: View = when (entry) {

            is DirEntry.Divider -> {
                convertView ?: inflater.inflate(R.layout.list_item_dir_divider, parent, false)
            }

            is DirEntry.Fixed -> {
                val v = convertView
                    ?: inflater.inflate(R.layout.list_item_favorite_dir, parent, false)
                v.findViewById<ImageView>(R.id.dir_icon).setImageResource(entry.iconRes)
                v.findViewById<TextView>(R.id.dir_name).text = entry.label
                v.findViewById<TextView>(R.id.dir_path).text = entry.path
                // Fixed entries (system, SD card, USB) never show a remove button
                v.findViewById<FrameLayout>(R.id.remove_favorite_btn).visibility = View.GONE
                v
            }

            is DirEntry.UserAdded -> {
                val v = convertView
                    ?: inflater.inflate(R.layout.list_item_favorite_dir, parent, false)
                val file = java.io.File(entry.path)
                v.findViewById<ImageView>(R.id.dir_icon)
                    .setImageResource(BohioR.drawable.px_folder)
                v.findViewById<TextView>(R.id.dir_name).text =
                    file.name.ifEmpty { context.getString(R.string.dir_label_storage) }
                v.findViewById<TextView>(R.id.dir_path).text = entry.path
                val removeBtn = v.findViewById<FrameLayout>(R.id.remove_favorite_btn)
                removeBtn.visibility = View.VISIBLE
                removeBtn.setOnClickListener { onRemove(entry.path) }
                v
            }
        }

        if (entry !is DirEntry.Divider) {
            ThemeManager.applyTheme(context, view)
            val typeface = FontManager.getTypeface(
                context,
                PrefsManager.getInstance(context).getFontStyle()
                    ?: PrefFontStyle.DEFAULT
            )
            FontManager.applyTypefaceToView(view, typeface)
        }

        return view
    }
}
