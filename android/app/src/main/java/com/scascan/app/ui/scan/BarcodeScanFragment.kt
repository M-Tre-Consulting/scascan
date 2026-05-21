package com.scascan.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.scascan.app.R
import com.scascan.app.databinding.FragmentBarcodeScanBinding
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

    private val barcodeReader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(
                    BarcodeFormat.QR_CODE,
                    BarcodeFormat.EAN_13, BarcodeFormat.EAN_8,
                    BarcodeFormat.UPC_A, BarcodeFormat.UPC_E,
                    BarcodeFormat.CODE_128, BarcodeFormat.CODE_39,
                    BarcodeFormat.ITF, BarcodeFormat.DATA_MATRIX,
                    BarcodeFormat.PDF_417, BarcodeFormat.AZTEC
                ),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

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

        observeUiState()
    }

    private fun startScanner() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (viewModel.uiState.value is BarcodeScanUiState.Loading) {
                            imageProxy.close()
                            return@setAnalyzer
                        }
                        try {
                            val bitmap = imageProxy.toBitmap()
                            val pixels = IntArray(bitmap.width * bitmap.height)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
                            val result = barcodeReader.decodeWithState(BinaryBitmap(HybridBinarizer(source)))
                            viewModel.analyzeBarcode(result.text)
                        } catch (_: NotFoundException) {
                            // No barcode in this frame — keep scanning
                        } finally {
                            barcodeReader.reset()
                            imageProxy.close()
                        }
                    }
                }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun observeUiState() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                when (state) {
                    is BarcodeScanUiState.Scanning -> {
                        binding.progressBar.visibility = View.GONE
                        binding.tvStatus.text = getString(R.string.barcode_scan_prompt)
                    }
                    is BarcodeScanUiState.Loading -> {
                        binding.progressBar.visibility = View.VISIBLE
                        binding.tvStatus.text = getString(R.string.analyzing)
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
