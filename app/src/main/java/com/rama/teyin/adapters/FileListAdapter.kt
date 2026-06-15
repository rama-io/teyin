package com.rama.teyin.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.rama.teyin.R
import com.rama.bohio.R as BohioR
import com.rama.teyin.managers.FileManager
import com.rama.teyin.managers.FontManager
import com.rama.teyin.managers.FsEntry
import com.rama.teyin.managers.PrefsManager
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager
import com.rama.teyin.managers.ThemeManager

class FileListAdapter(
    private val context: Context,
) : BaseAdapter() {
    private var entries: List<FsEntry> = emptyList()
    private var showUpRow: Boolean = false
    var isSelectionMode: Boolean = false
        private set
    private val selectedPaths = mutableSetOf<String>()

    val selectedEntries: List<FsEntry>
        get() = entries.filter { it.file.absolutePath in selectedPaths }

    val selectedCount: Int get() = selectedPaths.size

    fun update(newEntries: List<FsEntry>, hasParent: Boolean) {
        entries = newEntries
        showUpRow = hasParent
        // Drop any selections that are no longer visible
        val validPaths = entries.map { it.file.absolutePath }.toSet()
        selectedPaths.retainAll(validPaths)
        notifyDataSetChanged()
    }

    fun enterSelectionMode(entry: FsEntry) {
        isSelectionMode = true
        selectedPaths.clear()
        selectedPaths.add(entry.file.absolutePath)
        notifyDataSetChanged()
    }

    fun toggleSelection(entry: FsEntry) {
        val path = entry.file.absolutePath
        if (!selectedPaths.add(path)) selectedPaths.remove(path)
        notifyDataSetChanged()
    }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedPaths.clear()
        notifyDataSetChanged()
    }

    // Position 0 is the ".." row when showUpRow is true; all others are offset by 1.
    private val upOffset get() = if (showUpRow) 1 else 0

    override fun getCount(): Int = entries.size + upOffset
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getItem(position: Int): FsEntry? =
        if (showUpRow && position == 0) null else entries[position - upOffset]

    /** Convenience — only call for non-UP rows. */
    fun getEntry(position: Int): FsEntry = entries[position - upOffset]

    fun isUpRow(position: Int) = showUpRow && position == 0

    override fun getViewTypeCount(): Int = 3  // UP, FOLDER, FILE

    override fun getItemViewType(position: Int): Int = when {
        isUpRow(position) -> TYPE_UP
        entries[position - upOffset].isDirectory -> TYPE_FOLDER
        else -> TYPE_FILE
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val inflater = LayoutInflater.from(context)

        if (isUpRow(position)) {
            val view = convertView ?: inflater.inflate(R.layout.list_item_up, parent, false)
            applyThemeAndFont(view)
            return view
        }

        val entry = entries[position - upOffset]
        val isSelected = entry.file.absolutePath in selectedPaths

        val view = if (entry.isDirectory) {
            val v = convertView ?: inflater.inflate(R.layout.list_item_folder, parent, false)
            v.findViewById<TextView>(R.id.folder_name).text = entry.name
            bindSelectionCheck(v, isSelected)
            v
        } else {
            val v = convertView ?: inflater.inflate(R.layout.list_item_file, parent, false)
            v.findViewById<TextView>(R.id.file_name).text = entry.name
            v.findViewById<TextView>(R.id.file_size).text =
                FileManager.formatSize(context.resources, entry.size)
            v.findViewById<ImageView>(R.id.file_icon)
                .setImageResource(iconForExtension(entry.extension))
            bindSelectionCheck(v, isSelected)
            v
        }

        applyThemeAndFont(view)
        return view
    }

    private fun applyThemeAndFont(view: View) {
        ThemeManager.applyTheme(context, view)
        val typeface = FontManager.getTypeface(
            context,
            PrefsManager.getInstance(context).getFontStyle() ?: BohioPrefsManager.FontStyle.DEFAULT
        )
        FontManager.applyTypefaceToView(view, typeface)
    }

    private fun bindSelectionCheck(view: View, isSelected: Boolean) {
        val check = view.findViewById<FrameLayout>(R.id.selection_check)
        check.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        check.alpha = if (isSelected) 1f else 0.25f
    }

    private fun iconForExtension(ext: String): Int = when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> BohioR.drawable.px_eye
        "mp3", "flac", "ogg", "wav", "aac", "m4a" -> BohioR.drawable.px_seedlings
        "mp4", "mkv", "avi", "mov", "webm" -> BohioR.drawable.px_disk
        "pdf", "doc", "docx", "xls", "xlsx", "txt", "md" -> BohioR.drawable.px_edit
        "zip", "tar", "gz", "bz2", "7z", "rar" -> BohioR.drawable.px_folder_enter
        else -> BohioR.drawable.px_disk
    }

    companion object {
        const val TYPE_UP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_FILE = 2
    }
}
