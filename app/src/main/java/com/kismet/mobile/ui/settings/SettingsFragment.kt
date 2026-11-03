package com.kismet.mobile.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import com.google.android.material.snackbar.Snackbar
import com.kismet.mobile.databinding.FragmentSettingsBinding
import com.kismet.mobile.model.WigleProfile
import com.kismet.mobile.storage.AppPrefs
import com.kismet.mobile.viewmodel.WindowLayoutViewModel

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private lateinit var prefs: AppPrefs
    private var systemBarTop = 0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        prefs = AppPrefs(requireContext())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            systemBarTop = bars.top
            v.setPadding(0, bars.top, 0, v.paddingBottom)
            insets
        }

        windowViewModel.foldingFeature.observe(viewLifecycleOwner) { fold ->
            applyFoldPadding(fold)
        }

        val profile = prefs.getWigleProfile()
        binding.etWigleName.setText(profile.apiName)
        binding.etWigleToken.setText(profile.apiToken)

        binding.btnSave.setOnClickListener {
            val updated = WigleProfile(
                apiName = binding.etWigleName.text.toString().trim(),
                apiToken = binding.etWigleToken.text.toString().trim()
            )
            prefs.saveWigleProfile(updated)
            Snackbar.make(binding.root, "Saved", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun applyFoldPadding(fold: FoldingFeature?) {
        binding.root.doOnLayout {
            val bottomPad = if (fold?.state == FoldingFeature.State.HALF_OPENED &&
                fold.orientation == FoldingFeature.Orientation.HORIZONTAL) {
                val viewTop = IntArray(2).also { binding.root.getLocationInWindow(it) }[1]
                (binding.root.height - (fold.bounds.top - viewTop)).coerceAtLeast(0)
            } else 0
            binding.root.setPadding(0, systemBarTop, 0, bottomPad)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
