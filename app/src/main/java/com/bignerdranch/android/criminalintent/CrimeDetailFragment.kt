package com.bignerdranch.android.criminalintent

import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.*
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.doOnLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bignerdranch.android.criminalintent.databinding.FragmentCrimeDetailBinding
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import com.google.mlkit.vision.face.*
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions

private const val DATE_FORMAT = "EEE, MMM, dd"

class CrimeDetailFragment : Fragment() {
//    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?): View?{
//        val view = inflater.inflate(R.layout.fragment_crime_detail, container, false)
//
//        return view
//    }


    private var mlOptions = 0
    var numFaces = ""
    private val contourOptions = FaceDetectorOptions.Builder()

        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .build()
    private val selfieOptions =
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .enableRawSizeMask()
            .build()
    private val mesh_detector = FaceMeshDetection.getClient()
    private val face_detector = FaceDetection.getClient()
    private val contour_detector = FaceDetection.getClient(contourOptions)
    private val segmenter = Segmentation.getClient(selfieOptions)


    private var _binding: FragmentCrimeDetailBinding? = null
    private val binding
        get() = checkNotNull(_binding) {
            "Cannot access binding because it is null. Is the view visible?"
        }

    private var crimePBox: Array<ImageView> = arrayOf()

    private val args: CrimeDetailFragmentArgs by navArgs()

    private val crimeDetailViewModel: CrimeDetailViewModel by viewModels {
        CrimeDetailViewModelFactory(args.crimeId)
    }

    private val selectSuspect = registerForActivityResult(
        ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        uri?.let { parseContactSelection(it) }
    }

    private var photoNames: Array<String?> = arrayOfNulls(4)
    private var photoEffects: Array<Int> = arrayOf(0,0,0,0)
    private val takePhoto = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { didTakePhoto: Boolean ->

        if (didTakePhoto && photoNames.isNotEmpty()) {
            crimeDetailViewModel.updateCrime { oldCrime ->
                oldCrime.copy(mostRecentPhoto = (crimeDetailViewModel.getCrimePhotoCount() + 1) % 4)
            }
            if(photoNames[0] != null) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoFileName0 = photoNames[0],photoEffect0 = mlOptions)
                }
            }
            if(photoNames[1] != null) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoFileName1 = photoNames[1],photoEffect1 = mlOptions)
                }
            }
            if(photoNames[2] != null) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoFileName2 = photoNames[2],photoEffect2 = mlOptions)
                }
            }
            if(photoNames[3] != null) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoFileName3 = photoNames[3],photoEffect3 = mlOptions)
                }
            }
            if((crimeDetailViewModel.getCrimePhotoCount() + 1) % 4 == 0) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoEffect2 = photoEffects[2])
                }
            }
            if((crimeDetailViewModel.getCrimePhotoCount() + 1) % 4 == 1) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoEffect3 = photoEffects[3])
                }
            }
            if((crimeDetailViewModel.getCrimePhotoCount() + 1) % 4 == 2) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoEffect0 = photoEffects[0])
                }
            }
            if((crimeDetailViewModel.getCrimePhotoCount() + 1) % 4 == 3) {
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(photoEffect1 = photoEffects[1])
                }
            }

        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding =
            FragmentCrimeDetailBinding.inflate(inflater, container, false)

        crimePBox = arrayOf(binding.crimePhoto1, binding.crimePhoto2, binding.crimePhoto3, binding.crimePhoto4)

        return binding.root

    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.apply {
            crimeTitle.doOnTextChanged { text, _, _, _ ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(title = text.toString())
                }
            }

            crimeSolved.setOnCheckedChangeListener { _, isChecked ->
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(isSolved = isChecked)
                }
            }

            crimeSuspect.setOnClickListener {
                selectSuspect.launch(null)
            }

            val selectSuspectIntent = selectSuspect.contract.createIntent(
                requireContext(),
                null
            )
            crimeSuspect.isEnabled = canResolveIntent(selectSuspectIntent)

            crimeCamera.setOnClickListener {
                photoNames[crimeDetailViewModel.getCrimePhotoCount() % 4] = "IMG_${Date()}.JPG"
                photoEffects[crimeDetailViewModel.getCrimePhotoCount() % 4] = mlOptions
                val photoFile = File(
                    requireContext().applicationContext.filesDir,
                    photoNames[crimeDetailViewModel.getCrimePhotoCount() % 4] as String
                )
                val photoUri = FileProvider.getUriForFile(
                    requireContext(),
                    "com.bignerdranch.android.criminalintent.fileprovider",
                    photoFile
                )

                takePhoto.launch(photoUri)
            }

            val captureImageIntent = takePhoto.contract.createIntent(
                requireContext(),
                null
            )
            crimeCamera.isEnabled = canResolveIntent(captureImageIntent)

        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                crimeDetailViewModel.crime.collect { crime ->
                    crime?.let { updateUi(it) }
                }
            }
        }

        setFragmentResultListener(
            DatePickerFragment.REQUEST_KEY_DATE
        ) { _, bundle ->
            val newDate =
                bundle.getSerializable(DatePickerFragment.BUNDLE_KEY_DATE) as Date
            crimeDetailViewModel.updateCrime { it.copy(date = newDate) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun updateUi(crime: Crime) {
        binding.apply {

            if (crimeTitle.text.toString() != crime.title) {
                crimeTitle.setText(crime.title)
                println("HI")
            }
            crimeDate.text = crime.date.toString()
            crimeDate.setOnClickListener {
                findNavController().navigate(
                    CrimeDetailFragmentDirections.selectDate(crime.date)
                )
            }

            crimeSolved.isChecked = crime.isSolved

            crimeReport.setOnClickListener {
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, getCrimeReport(crime))
                    putExtra(
                        Intent.EXTRA_SUBJECT,
                        getString(R.string.crime_report_subject)
                    )
                }
                val chooserIntent = Intent.createChooser(
                    reportIntent,
                    getString(R.string.send_report)
                )
                startActivity(chooserIntent)
            }

            crimeSuspect.text = crime.suspect.ifEmpty {
                getString(R.string.crime_suspect_text)
            }

            radioGroup.setOnCheckedChangeListener { group, checkedId ->  R.id.checkedId
                if(checkedId == R.id.base)
                {
                    mlOptions=0
                }
                if(checkedId == R.id.fd)
                {
                    mlOptions=1
                    faceNum.visibility = View.VISIBLE
                    faceNum.text= numFaces
                }
                if(checkedId == R.id.cd)
                {
                    mlOptions=2
                }
                if(checkedId == R.id.md)
                {
                    mlOptions=3
                }
                if(checkedId == R.id.ss)
                {
                    mlOptions=4
                }
            }

            var index: Int = 0
            for(i in arrayOf(crime.photoFileName0, crime.photoFileName1, crime.photoFileName2, crime.photoFileName3)){
                if(i != null) {
                    updatePhoto(crime, i, crimePBox[index], index)
                }
                index += 1
            }

        }
    }

    private fun getCrimeReport(crime: Crime): String {
        val solvedString = if (crime.isSolved) {
            getString(R.string.crime_report_solved)
        } else {
            getString(R.string.crime_report_unsolved)
        }

        val dateString = DateFormat.format(DATE_FORMAT, crime.date).toString()
        val suspectText = if (crime.suspect.isBlank()) {
            getString(R.string.crime_report_no_suspect)
        } else {
            getString(R.string.crime_report_suspect, crime.suspect)
        }

        return getString(
            R.string.crime_report,
            crime.title, dateString, solvedString, suspectText
        )
    }

    fun Bitmap.rotate(degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private fun Bitmap.drawWithRectangle(objects: List<Face>, curOptions: Int): Bitmap? {
        val bitmap = copy(config, true)
        val canvas = Canvas(bitmap)
        var thisLabel = 0
        for (obj in objects) {
            thisLabel++
            val bounds = obj.boundingBox

            Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                textSize = 32.0f
                strokeWidth = 4.0f
                isAntiAlias = true
                // draw rectangle on canvas
                canvas.drawRect(
                    bounds,
                    this
                )
                if (curOptions == 2) {
                    // Draws all face contours.
                    for (contour in obj.allContours) {
                        for (point in contour.points) {
                            canvas.drawCircle(point.x, point.y, 2F, this)
                        }
                    }
                }
            }

        }
        return bitmap
    }

    private fun Bitmap.drawWithMesh(objects: List<FaceMesh>): Bitmap? {
        val bitmap = copy(config, true)
        val canvas = Canvas(bitmap)
        var thisLabel = 0
        for (obj in objects) {
            thisLabel++
            val bounds = obj.boundingBox

            Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                textSize = 32.0f
                strokeWidth = 4.0f
                isAntiAlias = true
                // draw rectangle on canvas
                canvas.drawRect(
                    bounds,
                    this
                )
                val faceMeshpoints = obj.allPoints
                for (faceMeshpoint in faceMeshpoints) {
                    val pointPosition = faceMeshpoint.position
                    canvas.drawCircle(pointPosition.x, pointPosition.y, 2F, this)
                }

            }

        }
        return bitmap
    }

    private fun Bitmap.drawWithSegment(result: SegmentationMask): Bitmap? {
        val bitmap = copy(config, true)
        val canvas = Canvas(bitmap)

        val mask = result.buffer
        val maskWidth = result.width
        val maskHeight = result.height

        mask.rewind()
        val newBitmap = Bitmap.createBitmap( maskWidth , maskHeight , Bitmap.Config.ARGB_8888 )
        newBitmap.copyPixelsFromBuffer( mask )

            Paint().apply {
                color = Color.RED
                style = Paint.Style.STROKE
                textSize = 32.0f
                strokeWidth = 4.0f
                isAntiAlias = true

                canvas.drawBitmap(newBitmap!! , 0f , 0f , this )


        }
        return bitmap
    }

    private fun parseContactSelection(contactUri: Uri) {
        val queryFields = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)

        val queryCursor = requireActivity().contentResolver
            .query(contactUri, queryFields, null, null, null)

        queryCursor?.use { cursor ->
            if (cursor.moveToFirst()) {
                val suspect = cursor.getString(0)
                crimeDetailViewModel.updateCrime { oldCrime ->
                    oldCrime.copy(suspect = suspect)
                }
            }
        }
    }

    private fun canResolveIntent(intent: Intent): Boolean {
        val packageManager: PackageManager = requireActivity().packageManager
        val resolvedActivity: ResolveInfo? =
            packageManager.resolveActivity(
                intent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
        return resolvedActivity != null
    }

    private fun updatePhoto(crime: Crime, photoFileName: String?, crimePhoto: ImageView, index: Int) {
        var curOptions = 0
        when (index) {
            0 -> curOptions = crime.photoEffect0
            1 -> curOptions = crime.photoEffect1
            2 -> curOptions = crime.photoEffect2
            3 -> curOptions = crime.photoEffect3
            else -> { // Note the block
            }
        }
        if (crimePhoto.tag != photoFileName) {
            val photoFile = photoFileName?.let {
                File(requireContext().applicationContext.filesDir, it)
            }
            if (photoFile?.exists() == true) {
                crimePhoto.doOnLayout { measuredView ->
                    val scaledBitmap = getScaledBitmap(
                        photoFile.path,
                        measuredView.width,
                        measuredView.height
                    )
                    val rotatedBitmap = scaledBitmap.rotate(90f)
                    val image = InputImage.fromBitmap(rotatedBitmap, 0)
                    if (curOptions == 1) {
                        face_detector.process(image)
                            .addOnSuccessListener { faces ->
                                crime.numFaces = faces.size.toString()
                                numFaces = getString(R.string.num_faces,crime.numFaces)
                                rotatedBitmap.apply {
                                    crimePhoto.setImageBitmap(drawWithRectangle(faces,curOptions))
                                    crimePhoto.tag = photoFileName
                                    crimePhoto.contentDescription =
                                        getString(R.string.crime_photo_image_description)
                                }
                            }

                            .addOnFailureListener { e ->

                                crimePhoto.setImageBitmap(rotatedBitmap)
                                crimePhoto.tag = photoFileName
                                crimePhoto.contentDescription =
                                    getString(R.string.crime_photo_image_description)
                            }
                    } else if (curOptions == 2) {
                        contour_detector.process(image)
                            .addOnSuccessListener { faces ->
                                crime.numFaces = faces.size.toString()
                                rotatedBitmap.apply {
                                    crimePhoto.setImageBitmap(drawWithRectangle(faces,curOptions))
                                    crimePhoto.tag = photoFileName
                                    crimePhoto.contentDescription =
                                        getString(R.string.crime_photo_image_description)
                                }
                            }

                            .addOnFailureListener { e ->

                                crimePhoto.setImageBitmap(rotatedBitmap)
                                crimePhoto.tag = photoFileName
                                crimePhoto.contentDescription =
                                    getString(R.string.crime_photo_image_description)
                            }
                    } else if (curOptions == 3) {
                        mesh_detector.process(image)
                            .addOnSuccessListener { faces ->
                                crime.numFaces = faces.size.toString()
                                rotatedBitmap.apply {
                                    crimePhoto.setImageBitmap(drawWithMesh(faces))
                                    crimePhoto.tag = photoFileName
                                    crimePhoto.contentDescription =
                                        getString(R.string.crime_photo_image_description)
                                }
                            }

                            .addOnFailureListener { e ->

                                crimePhoto.setImageBitmap(rotatedBitmap)
                                crimePhoto.tag = photoFileName
                                crimePhoto.contentDescription =
                                    getString(R.string.crime_photo_image_description)
                            }
                    }
                    else if (curOptions == 4) {
                        segmenter.process(image)
                            .addOnSuccessListener { results ->
                                rotatedBitmap.apply {
                                    crimePhoto.setImageBitmap(drawWithSegment(results))
                                    crimePhoto.tag = photoFileName
                                    crimePhoto.contentDescription =
                                        getString(R.string.crime_photo_image_description)
                                }
                            }

                            .addOnFailureListener { e ->

                                crimePhoto.setImageBitmap(rotatedBitmap)
                                crimePhoto.tag = photoFileName
                                crimePhoto.contentDescription =
                                    getString(R.string.crime_photo_image_description)
                            }
                    }
                    else {
                        crimePhoto.setImageBitmap(rotatedBitmap)
                        crimePhoto.tag = photoFileName
                        crimePhoto.contentDescription =
                            getString(R.string.crime_photo_image_description)
                    }
                }
            } else {
                crimePhoto.setImageBitmap(null)
                crimePhoto.tag = null
                crimePhoto.contentDescription =
                    getString(R.string.crime_photo_no_image_description)
            }
        }
    }
}