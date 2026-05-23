package com.scascan.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.scascan.app.R
import com.scascan.app.databinding.FragmentBarcodeScanBinding
import com.scascan.app.ui.util.hapticClick
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@AndroidEntryPoint
class BarcodeScanFragment : Fragment() {

    private var _binding: FragmentBarcodeScanBinding? = null
    private val binding get() = _binding!!

    private val viewModel: BarcodeScanViewModel by viewModels()
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startScanner()
        else Toast.makeText(requireContext(), getString(R.string.permission_camera_denied), Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBarcodeScanBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // Whole pill bar is the back target
        binding.topBar.setOnClickListener { findNavController().navigateUp() }

        // Push the floating bar below the status bar (camera preview stays full-screen)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusBar = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.topBar.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = statusBar + (16 * resources.displayMetrics.density).toInt()
            }
            insets
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        binding.btnScanShutter.setOnClickListener {
            it.hapticClick()
            takePhoto()
        }

        observeUiState()
    }

    private fun startScanner() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = image.toBitmap()
                    image.close()
                    viewModel.onShutterClicked(bitmap)
                }

                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is BarcodeScanUiState.Scanning -> {
                        binding.layoutThinking.visibility = View.GONE
                        binding.viewFinder.visibility = View.VISIBLE
                        binding.ivReticle.visibility = View.VISIBLE
                        binding.btnScanShutter.visibility = View.VISIBLE
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = getString(R.string.barcode_scan_prompt)
                        binding.btnScanShutter.isEnabled = true
                        binding.btnScanShutter.alpha = 1.0f
                        binding.ivReticle.imageTintList = android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(requireContext(), R.color.colorTertiary)
                        )
                    }
                    is BarcodeScanUiState.Loading -> {
                        binding.layoutThinking.visibility = View.VISIBLE
                        binding.viewFinder.visibility = View.GONE
                        binding.ivReticle.visibility = View.GONE
                        binding.btnScanShutter.visibility = View.GONE
                        binding.topBar.isEnabled = false
                    }
                    is BarcodeScanUiState.Success -> {
                        findNavController().navigate(
                            R.id.action_barcodeScanFragment_to_nutritionResultFragment,
                            bundleOf("nutritionFacts" to state.nutritionFacts)
                        )
                        viewModel.resetToScanning()
                    }
                    is BarcodeScanUiState.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.topBar.isEnabled = true
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                        viewModel.resetToScanning()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        cameraExecutor.shutdown()
    }
}
