package com.example.jammate.ui.fragments

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.jammate.R
import com.example.jammate.databinding.FragmentCreatePostBinding
import com.example.jammate.databinding.ModalPostTypePickerBinding
import com.example.jammate.model.LocationData
import com.example.jammate.model.Post
import com.example.jammate.utilities.Constants
import com.example.jammate.utilities.PlacePicker
import com.example.jammate.utilities.PostPublication
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

// Manages the immersive post creation flow, including media selection and type-specific details.
class CreatePostFragment : Fragment() {

    private var _binding: FragmentCreatePostBinding? = null
    private val binding get() = _binding!!

    private var currentType = Constants.PostTypes.NORMAL_POST
    private var selectedLocation: LocationData? = null
    private var selectedDateTime: Long? = null
    
    private lateinit var placePicker: PlacePicker
    private val publisher = PostPublication()
    
    private var pickedUri: Uri? = null
    private var pickedType: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreatePostBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initMediaPicker()
        initInteractions()
        updateTypeUi()
    }

    // Configures the media selection behavior and handles the transition to the editor.
    private fun initMediaPicker() {
        val picker = com.example.jammate.utilities.MediaPicker(this) { uri, type ->
            pickedUri = uri
            pickedType = type
            binding.createPostMEDIAPreview.setMedia(uri, type)
            showEditor()
        }

        binding.createPostBTNPickImage.setOnClickListener { picker.openImage() }
        binding.createPostBTNPickVideo.setOnClickListener { picker.openVideo() }
        
        binding.createPostMEDIAPreview.onRemove = {
            pickedUri = null
            pickedType = null
            showPicker()
        }
    }

    // Connects UI buttons to their respective logic like location picking and type selection.
    private fun initInteractions() {
        placePicker = PlacePicker(this, requireContext(), android.widget.EditText(requireContext())) { loc ->
            selectedLocation = loc
            binding.createPostLBLLocation.text = loc.name
        }

        binding.createPostBTNClose.setOnClickListener { findNavController().popBackStack() }
        binding.createPostBTNTypeSelector.setOnClickListener { showTypePicker() }
        binding.createPostBTNPublish.setOnClickListener { publishPost() }
        binding.createPostBTNLocation.setOnClickListener { placePicker.open() }
        binding.createPostLBLDateTime.setOnClickListener { showDateTimePicker() }

        ChipHelper.buildChips(requireContext(), binding.createPostCHIPSInstruments, Constants.Lists.instruments)
        ChipHelper.buildChips(requireContext(), binding.createPostCHIPSGenres, Constants.Lists.genres)
        ChipHelper.buildChips(requireContext(), binding.createPostCHIPSSkill, listOf("Beginner", "Intermediate","Advanced", "Professional"))
    }

    // Displays the post editing interface once media has been selected.
    private fun showEditor() {
        binding.createPostLAYInitialPicker.isVisible = false
        binding.createPostLAYEditor.isVisible = true
        binding.createPostVIEWOverlay.isVisible = true
    }

    // Returns to the initial media selection screen.
    private fun showPicker() {
        binding.createPostLAYInitialPicker.isVisible = true
        binding.createPostLAYEditor.isVisible = false
        binding.createPostVIEWOverlay.isVisible = false
        binding.createPostMEDIAPreview.clear()
    }

    // Opens a bottom sheet allowing the user to choose between Normal, Jam, or Member posts.
    private fun showTypePicker() {
        val dialog = BottomSheetDialog(requireContext())
        val binding2 = ModalPostTypePickerBinding.inflate(layoutInflater)
        dialog.setContentView(binding2.root)

       binding2.postTypePickerBTNNormal.setOnClickListener {
            currentType = Constants.PostTypes.NORMAL_POST
            updateTypeUi()
            dialog.dismiss()
        }

        binding2.postTypePickerBTNJam.setOnClickListener {
            currentType = Constants.PostTypes.JAM_SESSION
            updateTypeUi()
            dialog.dismiss()
        }

        binding2.postTypePickerBTNMember.setOnClickListener {
            currentType = Constants.PostTypes.BAND_MEMBER
            updateTypeUi()
            dialog.dismiss()
        }

        dialog.show()
    }

    // Updates the visibility of overlay fields based on the selected post type.
    private fun updateTypeUi() {
        binding.createPostBTNTypeSelector.text = when (currentType) {
            Constants.PostTypes.JAM_SESSION -> "Jam Session"
            Constants.PostTypes.BAND_MEMBER -> "Band Member"
            else -> "Normal Post"
        }

        binding.createPostINPUTTitle.isVisible = (currentType == Constants.PostTypes.JAM_SESSION)
        binding.createPostLBLDateTime.isVisible = (currentType == Constants.PostTypes.JAM_SESSION)
        binding.createPostLAYMemberFields.isVisible = (currentType == Constants.PostTypes.BAND_MEMBER)
        
        if (currentType == Constants.PostTypes.JAM_SESSION) {
            binding.createPostLBLDateTime.text = if (selectedDateTime == null) "📅 Set Date & Time" else "📅 ${android.text.format.DateFormat.format("MMM dd, HH:mm", selectedDateTime!!)}"
        }
    }

    // Launches the date and time pickers for Jam Session posts.
    private fun showDateTimePicker() {
        val datePicker = MaterialDatePicker.Builder.datePicker().setTitleText("Select Date").build()
        datePicker.addOnPositiveButtonClickListener { timeLong ->
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText("Select Time")
                .build()
            
            timePicker.addOnPositiveButtonClickListener {
                val cal = Calendar.getInstance().apply {
                    timeInMillis = timeLong
                    set(Calendar.HOUR_OF_DAY, timePicker.hour)
                    set(Calendar.MINUTE, timePicker.minute)
                }
                selectedDateTime = cal.timeInMillis
                updateTypeUi()
            }
            timePicker.show(parentFragmentManager, "time")
        }
        datePicker.show(parentFragmentManager, "date")
    }

    // Validates inputs, shows the loading animation, and uploads the post to Firebase.
    private fun publishPost() {
        val caption = binding.createPostINPUTCaption.text.toString().trim()
        if (caption.isBlank()) { toast("Write a caption first!"); return }
        if (pickedUri == null) { toast("Select a photo or video!"); return }

        val post = Post(
            type = currentType,
            description = caption,
            location = selectedLocation,
            createdAt = System.currentTimeMillis()
        )

        if (currentType == Constants.PostTypes.JAM_SESSION) {
            post.dateTime = selectedDateTime
            if (post.dateTime == null) { toast("When is the Jam?"); return }
        }

        if (currentType == Constants.PostTypes.BAND_MEMBER) {
            post.instrument = ChipHelper.getCheckedChipTexts(binding.createPostCHIPSInstruments)
            post.genre = ChipHelper.getCheckedChipTexts(binding.createPostCHIPSGenres)
            post.skillLevel = ChipHelper.getSingleSelectedChipText(binding.createPostCHIPSSkill)
            
            if (post.instrument.isEmpty()) { toast("Select instruments!"); return }
            if (post.genre.isEmpty()) { toast("Select genres!"); return }
            if (post.skillLevel.isNullOrBlank()) { toast("Select skill level!"); return }
        }

        binding.createPostLAYLoading.visibility = View.VISIBLE
        publisher.publish(post, pickedUri, pickedType) { ok, err ->
            if (ok) {
                binding.createPostLAYLoading.visibility = View.GONE
                toast("Posted!")
                findNavController().popBackStack()
            } else {
                binding.createPostLAYLoading.visibility = View.GONE
                toast(err ?: "Upload failed")
            }
        }
    }

    private fun toast(msg: String) = Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}