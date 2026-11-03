package com.kismet.mobile.sdr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class KaliToolsSheet : BottomSheetDialogFragment() {

    private var selectedCategory = KaliSdrTools.categories.first()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A2025"))
        }

        // Header
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (8 * dp).toInt())
        }
        header.addView(TextView(ctx).apply {
            text = "KALI SDR TOOLS"
            setTextColor(Color.parseColor("#00BFA5"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
            letterSpacing = 0.2f
        })
        header.addView(TextView(ctx).apply {
            text = "Tap command to copy · install or pipe into Kismet"
            setTextColor(Color.parseColor("#546E7A"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, (4 * dp).toInt(), 0, 0)
        })
        root.addView(header)

        // Category chips
        val chipScroll = HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = false
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        val chipRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

        val chipViews = mutableMapOf<String, TextView>()
        val contentArea = ScrollView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (480 * dp).toInt()
            )
        }
        val contentInner = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        contentArea.addView(contentInner)

        fun selectCategory(cat: String) {
            selectedCategory = cat
            chipViews.forEach { (c, tv) ->
                val sel = c == cat
                tv.setTextColor(if (sel) Color.parseColor("#00BFA5") else Color.parseColor("#546E7A"))
                tv.setBackgroundColor(if (sel) Color.parseColor("#0D2A27") else Color.TRANSPARENT)
            }
            contentInner.removeAllViews()
            KaliSdrTools.tools.filter { it.category == cat }.forEach { tool ->
                contentInner.addView(toolCard(ctx, dp, tool))
            }
        }

        KaliSdrTools.categories.forEach { cat ->
            val chip = TextView(ctx).apply {
                text = cat
                textSize = 10f
                typeface = Typeface.MONOSPACE
                setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
                setTextColor(Color.parseColor("#546E7A"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.marginEnd = (6 * dp).toInt() }
                setOnClickListener { selectCategory(cat) }
            }
            chipViews[cat] = chip
            chipRow.addView(chip)
        }
        chipScroll.addView(chipRow)
        root.addView(chipScroll)
        root.addView(contentArea)

        selectCategory(selectedCategory)
        return root
    }

    private fun toolCard(ctx: Context, dp: Float, tool: KaliTool): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0F1214"))
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).also { it.bottomMargin = (10 * dp).toInt() }
        }

        // Tool name
        card.addView(TextView(ctx).apply {
            text = tool.name
            setTextColor(Color.parseColor("#CFD8DC"))
            textSize = 13f
            typeface = Typeface.MONOSPACE
        })

        // Description
        card.addView(TextView(ctx).apply {
            text = tool.description
            setTextColor(Color.parseColor("#546E7A"))
            textSize = 10f
            typeface = Typeface.MONOSPACE
            setPadding(0, (3 * dp).toInt(), 0, (10 * dp).toInt())
        })

        // Install command
        if (tool.installCmd.isNotEmpty()) {
            card.addView(cmdLabel(ctx, dp, "INSTALL"))
            card.addView(cmdBlock(ctx, dp, tool.installCmd))
        }

        // Run command
        if (tool.runCmd.isNotEmpty()) {
            card.addView(cmdLabel(ctx, dp, "RUN"))
            card.addView(cmdBlock(ctx, dp, tool.runCmd))
        }

        // Kismet source line
        if (tool.kismetSource.isNotEmpty()) {
            card.addView(cmdLabel(ctx, dp, "KISMET SOURCE"))
            card.addView(cmdBlock(ctx, dp, tool.kismetSource))
        }

        return card
    }

    private fun cmdLabel(ctx: Context, dp: Float, text: String) = TextView(ctx).apply {
        this.text = text
        setTextColor(Color.parseColor("#37474F"))
        textSize = 8f
        typeface = Typeface.MONOSPACE
        letterSpacing = 0.2f
        setPadding(0, (4 * dp).toInt(), 0, (2 * dp).toInt())
    }

    private fun cmdBlock(ctx: Context, dp: Float, cmd: String) = TextView(ctx).apply {
        text = cmd
        setTextColor(Color.parseColor("#00BFA5"))
        textSize = 10f
        typeface = Typeface.MONOSPACE
        setBackgroundColor(Color.parseColor("#0A1A17"))
        setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).also { it.bottomMargin = (4 * dp).toInt() }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("cmd", cmd))
            Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
        }
    }
}
