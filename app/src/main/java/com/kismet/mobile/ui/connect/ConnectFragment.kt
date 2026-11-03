package com.kismet.mobile.ui.connect

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.window.layout.FoldingFeature
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.kismet.mobile.BuildConfig
import com.kismet.mobile.R
import com.kismet.mobile.databinding.FragmentConnectBinding
import com.kismet.mobile.model.ConnectionProfile
import com.kismet.mobile.viewmodel.ConnectionState
import com.kismet.mobile.viewmodel.KismetViewModel
import com.kismet.mobile.viewmodel.WindowLayoutViewModel
import androidx.core.text.HtmlCompat
import androidx.lifecycle.lifecycleScope
import android.text.method.LinkMovementMethod
import kotlinx.coroutines.launch

class ConnectFragment : Fragment() {

    private var _binding: FragmentConnectBinding? = null
    private val binding get() = _binding!!
    private val viewModel: KismetViewModel by activityViewModels()
    private val windowViewModel: WindowLayoutViewModel by activityViewModels()
    private var systemBarTop = 0
    private lateinit var profileAdapter: ProfileAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentConnectBinding.inflate(inflater, container, false)
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

        profileAdapter = ProfileAdapter(
            onSelect = { profile -> fillFromProfile(profile) },
            onDelete = { profile -> viewModel.deleteProfile(profile.id) }
        )
        binding.rvProfiles.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfiles.adapter = profileAdapter

        binding.btnConnect.setOnClickListener { attemptConnect() }
        binding.btnSave.setOnClickListener { showSaveDialog() }

        val versionName = requireContext().packageManager
            .getPackageInfo(requireContext().packageName, 0).versionName
        binding.tvAbout.text = HtmlCompat.fromHtml(
            getString(R.string.about_footer, versionName),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        binding.tvAbout.movementMethod = LinkMovementMethod.getInstance()

        binding.switchSsh.setOnCheckedChangeListener { _, isChecked ->
            binding.layoutSshSettings.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        viewModel.connectionState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is ConnectionState.Connecting -> {
                    binding.btnConnect.isEnabled = false
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvStatus.text = "CONNECTING..."
                }
                is ConnectionState.Connected -> {
                    findNavController().navigate(R.id.action_connect_to_dashboard)
                }
                is ConnectionState.Disconnected -> {
                    binding.btnConnect.isEnabled = true
                    binding.progressBar.visibility = View.GONE
                    binding.tvStatus.text = "OFFLINE"
                }
            }
        }

        viewModel.profiles.observe(viewLifecycleOwner) { profiles ->
            binding.tvProfilesLabel.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            binding.rvProfiles.visibility = if (profiles.isEmpty()) View.GONE else View.VISIBLE
            profileAdapter.submitList(profiles)

            if (profiles.isNotEmpty()) {
                val p = profiles.first()
                viewLifecycleOwner.lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    val isOnline = try {
                        val socket = java.net.Socket()
                        socket.connect(java.net.InetSocketAddress(p.host, p.port), 1000)
                        socket.close()
                        true
                    } catch (e: Exception) {
                        false
                    }
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val b = _binding ?: return@withContext
                        if (isOnline) {
                            b.tvStatus.text = "ONLINE"
                            b.tvStatus.setTextColor(android.graphics.Color.parseColor("#39FF14"))
                        } else {
                            b.tvStatus.text = "OFFLINE"
                            b.tvStatus.setTextColor(androidx.core.content.ContextCompat.getColor(requireContext(), R.color.text_secondary))
                        }
                    }
                }
            }
        }

        viewModel.error.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        if (BuildConfig.IS_DEMO) {
            viewModel.enterDemoMode()
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

    private fun attemptConnect() {
        val profile = buildProfile("temp")
        viewModel.connect(profile)
    }

    private fun buildProfile(name: String): ConnectionProfile {
        return ConnectionProfile(
            name = name,
            host = binding.etHost.text.toString().trim().ifEmpty { "localhost" },
            port = binding.etPort.text.toString().toIntOrNull() ?: 2501,
            username = binding.etUsername.text.toString().trim(),
            password = binding.etPassword.text.toString(),
            useSsh = binding.switchSsh.isChecked,
            sshHost = binding.etSshHost.text.toString().trim(),
            sshPort = binding.etSshPort.text.toString().toIntOrNull() ?: 22,
            sshUsername = binding.etSshUsername.text.toString().trim(),
            sshPassword = binding.etSshPassword.text.toString(),
            sshPrivateKey = binding.etSshPrivateKey?.text?.toString()?.trim() ?: ""
        )
    }

    private fun fillFromProfile(profile: ConnectionProfile) {
        binding.etHost.setText(profile.host)
        binding.etPort.setText(profile.port.toString())
        binding.etUsername.setText(profile.username)
        binding.etPassword.setText(profile.password)
        
        binding.switchSsh.isChecked = profile.useSsh
        binding.etSshHost.setText(profile.sshHost)
        binding.etSshPort.setText(profile.sshPort.toString())
        binding.etSshUsername.setText(profile.sshUsername)
        binding.etSshPassword.setText(profile.sshPassword)
        binding.etSshPrivateKey?.setText(profile.sshPrivateKey)
    }

    private fun showSaveDialog() {
        val input = TextInputEditText(requireContext()).apply {
            hint = "Profile name"
            setPadding(48, 32, 48, 16)
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Save Connection")
            .setView(input)
            .setPositiveButton("SAVE") { _, _ ->
                val name = input.text.toString().trim().ifEmpty { binding.etHost.text.toString() }
                val profile = buildProfile(name)
                viewModel.saveProfile(profile)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
