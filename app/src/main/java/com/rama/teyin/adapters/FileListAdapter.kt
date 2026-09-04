package com.rama.teyin.adapters

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.rama.bohio.managers.FontManager
import com.rama.bohio.managers.ThemeManager
import com.rama.bohio.objects.Themes
import com.rama.teyin.R
import com.rama.bohio.R as BohioR
import com.rama.teyin.managers.FileManager
import com.rama.teyin.managers.FsNode
import com.rama.teyin.managers.PrefsManager

class FileListAdapter(
    private val context: Context,
) : BaseAdapter() {
    private var entries: List<FsNode> = emptyList()
    private var showUpRow: Boolean = false
    var isSelectionMode: Boolean = false
        private set
    private val selectedPaths = mutableSetOf<String>()

    private var typeface: Typeface? = null
    private var activeTheme: String = ""
    private var activePalette: Themes.Palette? = null

    init {
        reloadStyle()
    }

    private fun reloadStyle() {
        typeface = FontManager.getTypeface(
            context,
            PrefsManager.getInstance(context).getFontStyle()
        )
        activeTheme = PrefsManager.getInstance(context).getTheme()
        activePalette = ThemeManager.paletteFor(activeTheme, context)
    }

    val selectedEntries: List<FsNode>
        get() = entries.filter { it.path in selectedPaths }

    val selectedCount: Int get() = selectedPaths.size

    fun update(newEntries: List<FsNode>, hasParent: Boolean) {
        reloadStyle()
        entries = newEntries
        showUpRow = hasParent
        if (selectedPaths.isNotEmpty()) {
            val validPaths = newEntries.map { it.path }.toSet()
            selectedPaths.retainAll(validPaths)
        }
        notifyDataSetChanged()
    }

    fun enterSelectionMode(entry: FsNode) {
        isSelectionMode = true
        selectedPaths.clear()
        selectedPaths.add(entry.path)
        notifyDataSetChanged()
    }

    fun toggleSelection(entry: FsNode) {
        val path = entry.path
        if (!selectedPaths.add(path)) selectedPaths.remove(path)
        if (selectedPaths.isEmpty()) isSelectionMode = false
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

    override fun getItem(position: Int): FsNode? =
        if (isUpRow(position)) null else entries.getOrNull(position - upOffset)

    /** Convenience — only call for non-UP rows. */
    fun getEntry(position: Int): FsNode = entries[position - upOffset]

    fun isUpRow(position: Int) = showUpRow && position == 0

    override fun getViewTypeCount(): Int = 3  // UP, FOLDER, FILE

    override fun getItemViewType(position: Int): Int = when {
        isUpRow(position) -> TYPE_UP
        entries.getOrNull(position - upOffset)?.isDirectory == true -> TYPE_FOLDER
        else -> TYPE_FILE
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val viewType = getItemViewType(position)
        val holder: ViewHolder
        val view: View

        if (convertView == null) {
            val inflater = LayoutInflater.from(context)
            view = when (viewType) {
                TYPE_UP -> inflater.inflate(R.layout.list_item_up, parent, false)
                TYPE_FOLDER -> inflater.inflate(R.layout.list_item_folder, parent, false)
                else -> inflater.inflate(R.layout.list_item_file, parent, false)
            }
            holder = when (viewType) {
                TYPE_UP -> ViewHolder(
                    root = view,
                    nameView = view.findViewById(R.id.up_text),
                    extView = null,
                    sizeView = null,
                    iconView = null,
                    selectionCheck = null
                )
                TYPE_FOLDER -> ViewHolder(
                    root = view,
                    nameView = view.findViewById(R.id.folder_name),
                    extView = null,
                    sizeView = null,
                    iconView = null,
                    selectionCheck = view.findViewById(R.id.selection_check)
                )
                else -> ViewHolder(
                    root = view,
                    nameView = view.findViewById(R.id.file_name),
                    extView = view.findViewById(R.id.ext),
                    sizeView = view.findViewById(R.id.file_size),
                    iconView = view.findViewById(R.id.file_icon),
                    selectionCheck = view.findViewById(R.id.selection_check)
                )
            }
            view.tag = holder
        } else {
            view = convertView
            holder = convertView.tag as ViewHolder
        }
        if (holder.boundTheme != activeTheme || holder.boundTypeface != typeface || holder.boundPalette != activePalette) {
            applyThemeAndFont(holder)
            holder.boundTheme = activeTheme
            holder.boundTypeface = typeface
            holder.boundPalette = activePalette
        }

        if (isUpRow(position)) {
            return view
        }

        val entry = entries[position - upOffset]
        val isSelected = entry.path in selectedPaths

        if (entry.isDirectory) {
            holder.nameView?.text = entry.name
            bindSelectionCheck(holder.selectionCheck, isSelected)
        } else {
            holder.nameView?.text = entry.name
            holder.extView?.text = entry.extension.take(4)
            holder.sizeView?.text = FileManager.formatSize(context.resources, entry.size)
            holder.iconView?.setImageResource(iconForExtension(entry.extension))
            bindSelectionCheck(holder.selectionCheck, isSelected)
        }

        return view
    }

    private fun applyThemeAndFont(holder: ViewHolder) {
        ThemeManager.applyTheme(context, holder.root)
        FontManager.applyTypefaceToView(holder.root, typeface)
    }

    private fun bindSelectionCheck(selectionCheck: FrameLayout?, isSelected: Boolean) {
        if (selectionCheck == null) return
        selectionCheck.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
        selectionCheck.alpha = if (isSelected) 1f else 0.25f
    }

    private fun iconForExtension(ext: String): Int = when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg" -> BohioR.drawable.px_eye
        "mp3", "flac", "ogg", "wav", "aac", "m4a" -> BohioR.drawable.px_seedlings
        "mp4", "mkv", "avi", "mov", "webm" -> BohioR.drawable.px_disk
        "pdf", "doc", "docx", "xls", "xlsx", "txt", "md" -> BohioR.drawable.px_edit
        "zip", "tar", "gz", "bz2", "7z", "rar" -> BohioR.drawable.px_folder_enter
        else -> BohioR.drawable.px_disk
    }

    private class ViewHolder(
        val root: View,
        val nameView: TextView?,
        val extView: TextView?,
        val sizeView: TextView?,
        val iconView: ImageView?,
        val selectionCheck: FrameLayout?,
        var boundTheme: String? = null,
        var boundTypeface: Typeface? = null,
        var boundPalette: Themes.Palette? = null
    )
    companion object {
        const val TYPE_UP = 0
        const val TYPE_FOLDER = 1
        const val TYPE_FILE = 2
    }
}
