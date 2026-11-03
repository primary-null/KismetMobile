package com.kismet.mobile.sdr

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class SdrLaunchSheet : BottomSheetDialogFragment() {

    var host: String = ""
    var port: Int = 2501
    var sourceName: String = ""

    companion object {
        fun newInstance(host: String, port: Int, sourceName: String) = SdrLaunchSheet().also {
            it.host = host
            it.port = port
            it.sourceName = sourceName
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val ctx = requireContext()
        val dp = resources.displayMetrics.density

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#1A2025"))
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (28 * dp).toInt())
        }

        // Header
        root.addView(TextView(ctx).apply {
            text = "SDR INTEGRATION"
            setTextColor(Color.parseColor("#00BFA5"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            letterSpacing = 0.2f
            setPadding(0, 0, 0, (4 * dp).toInt())
        })
        root.addView(TextView(ctx).apply {
            text = if (sourceName.isNotEmpty()) "Source: $sourceName" else "HackRF / SDR source"
            setTextColor(Color.parseColor("#546E7A"))
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, 0, 0, (18 * dp).toInt())
        })

        val installed = SdrIntegration.findInstalled(requireContext().packageManager)

        if (installed.isNotEmpty()) {
            root.addView(sectionLabel(ctx, dp, "INSTALLED APPS"))
            installed.forEach { app ->
                root.addView(appRow(ctx, dp, app, installed = true))
            }
            if (installed.size < SdrIntegration.knownApps.size) {
                val missing = SdrIntegration.knownApps.filter { k -> installed.none { it.packageName == k.packageName } }
                root.addView(sectionLabel(ctx, dp, "GET MORE", topMargin = 16))
                missing.forEach { app -> root.addView(appRow(ctx, dp, app, installed = false)) }
            }
        } else {
            root.addView(TextView(ctx).apply {
                text = "No SDR apps detected on this device.\nInstall one to view live RF spectrum."
                setTextColor(Color.parseColor("#CFD8DC"))
                textSize = 12f
                typeface = android.graphics.Typeface.MONOSPACE
                setPadding(0, 0, 0, (14 * dp).toInt())
            })
            root.addView(sectionLabel(ctx, dp, "RECOMMENDED"))
            SdrIntegration.knownApps.forEach { app -> root.addView(appRow(ctx, dp, app, installed = false)) }
        }

        // Divider
        root.addView(View(ctx).apply {
            setBackgroundColor(Color.parseColor("#2C3539"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).also { it.topMargin = (16 * dp).toInt(); it.bottomMargin = (12 * dp).toInt() }
        })

        // Spectrum View button
        root.addView(makeButton(ctx, "RF SPECTRUM VIEW  ▶", Color.parseColor("#00BFA5")) {
            dismiss()
            requireActivity().let { activity ->
                val navHost = activity.supportFragmentManager
                    .findFragmentById(com.kismet.mobile.R.id.nav_host_fragment)
                        as? androidx.navigation.fragment.NavHostFragment
                navHost?.navController?.navigate(com.kismet.mobile.R.id.sdrSpectrumFragment)
            }
        })

        // Kali Tools button
        root.addView(makeButton(ctx, "KALI SDR TOOLS  ▶", Color.parseColor("#546E7A")) {
            dismiss()
            KaliToolsSheet().show(parentFragmentManager, "kali_tools")
        })

        // Open Kismet Web UI
        root.addView(makeButton(ctx, "OPEN KISMET WEB UI", Color.parseColor("#546E7A")) {
            SdrIntegration.openKismetWebUi(requireContext(), host, port)
            dismiss()
        })

        return root
    }

    private fun sectionLabel(ctx: android.content.Context, dp: Float, text: String, topMargin: Int = 0) =
        TextView(ctx).apply {
            this.text = text
            setTextColor(Color.parseColor("#546E7A"))
            textSize = 9f
            typeface = android.graphics.Typeface.MONOSPACE
            letterSpacing = 0.2f
            setPadding(0, (topMargin * dp).toInt(), 0, (6 * dp).toInt())
        }

    private fun appRow(ctx: android.content.Context, dp: Float, app: SdrApp, installed: Boolean): View {
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (10 * dp).toInt())
        }
        val nameRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        nameRow.addView(TextView(ctx).apply {
            text = app.name
            setTextColor(if (installed) Color.parseColor("#CFD8DC") else Color.parseColor("#546E7A"))
            textSize = 13f
            typeface = android.graphics.Typeface.MONOSPACE
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        val actionLabel = if (installed) "LAUNCH" else "INSTALL"
        val actionColor = if (installed) Color.parseColor("#00BFA5") else Color.parseColor("#546E7A")
        nameRow.addView(makeButton(ctx, actionLabel, actionColor) {
            if (installed) {
                SdrIntegration.launch(requireContext(), app)
                dismiss()
            } else {
                SdrIntegration.openPlayStore(requireContext(), app)
            }
        })
        row.addView(nameRow)
        row.addView(TextView(ctx).apply {
            text = app.description
            setTextColor(Color.parseColor("#455A64"))
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, (2 * dp).toInt(), 0, 0)
        })
        return row
    }

    private fun makeButton(ctx: android.content.Context, label: String, color: Int, action: () -> Unit): MaterialButton {
        return MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = label
            setTextColor(color)
            textSize = 10f
            typeface = android.graphics.Typeface.MONOSPACE
            letterSpacing = 0.1f
            setPadding(0, 0, 0, 0)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { action() }
        }
    }
}
