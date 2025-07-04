package com.example.sorter

import android.animation.*
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * MainActivity is the primary activity for the sorting visualizer application.
 * It allows users to select a sorting algorithm, choose a file containing numbers,
 * visualize the sorting process on a bar graph, and control the animation speed.
 */
class MainActivity : ComponentActivity() {

    private val PICK_FILE = 1 // Request code for picking a file
    private var numbers: List<Int> = listOf() // The list of numbers to be sorted
    private lateinit var barGraph: BarGraphView // Custom view to display numbers as a bar graph
    private lateinit var seekBar: SeekBar // Controls animation delay
    private lateinit var delayText: TextView
    private lateinit var tvUnsorted: TextView
    private lateinit var tvSorted: TextView
    private lateinit var tvSelectedAlgorithm: TextView
    private lateinit var btnSelectAlgorithm: Button
    private lateinit var btnChooseFile: Button
    private lateinit var btnSort: Button
    private var sortJob: Job? = null // Manages the sorting coroutine
    private var delayMs = 300L // Delay in milliseconds for animation steps
    private var isSorted = false // Flag to check if the current list is sorted
    private var selectedAlgorithm = SortingAlgorithm.BUBBLE_SORT // Currently selected algorithm

    private val activeAnimators = mutableSetOf<Animator>() // Tracks active animators for cleanup

    /**
     * Enum defining available sorting algorithms with display names.
     */
    enum class SortingAlgorithm(val displayName: String) {
        BUBBLE_SORT("Bubble Sort"),
        INSERTION_SORT("Insertion Sort"),
        SELECTION_SORT("Selection Sort"),
        QUICK_SORT("Quick Sort"),
        MERGE_SORT("Merge Sort")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupEventListeners()
        setupInitialAnimations()
    }

    /** Initializes all UI components and sets their initial states. */
    private fun initializeViews() {
        barGraph = findViewById(R.id.barGraph)
        seekBar = findViewById(R.id.seekBar)
        delayText = findViewById(R.id.delayText)
        tvUnsorted = findViewById(R.id.tvUnsorted)
        tvSorted = findViewById(R.id.tvSorted)
        tvSelectedAlgorithm = findViewById(R.id.tvSelectedAlgorithm)
        btnSelectAlgorithm = findViewById(R.id.btnSelectAlgorithm)
        btnChooseFile = findViewById(R.id.btnChooseFile)
        btnSort = findViewById(R.id.btnSort)

        seekBar.max = 1000
        seekBar.progress = delayMs.toInt()
        delayText.text = "${delayMs}ms"
        setColoredAlgorithmName(tvSelectedAlgorithm, "Selected: ${selectedAlgorithm.displayName}", selectedAlgorithm.displayName)
    }

    /** Sets up initial entrance animations for key UI elements. */
    private fun setupInitialAnimations() {
        val views = listOf(tvSelectedAlgorithm, btnSelectAlgorithm, btnChooseFile, btnSort)
        views.forEachIndexed { index, view ->
            view.alpha = 0f
            view.translationY = 50f
            view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 100L)
                .setDuration(500)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /** Configures event listeners for UI interactions. */
    private fun setupEventListeners() {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    delayMs = progress.coerceAtLeast(20).toLong()
                    animateDelayTextUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnSelectAlgorithm.setOnClickListener {
            animateButtonPress(btnSelectAlgorithm) { showAlgorithmSelectionDialog() }
        }

        btnChooseFile.setOnClickListener {
            animateButtonPress(btnChooseFile) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                }
                startActivityForResult(intent, PICK_FILE)
            }
        }

        btnSort.setOnClickListener {
            animateButtonPress(btnSort) { startSorting() }
        }

        // Handles back button press to cancel sorting and reset UI
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sortJob?.cancel()
                resetUI()
            }
        })
    }

    /** Animates a button press with a spring effect and executes an action. */
    private fun animateButtonPress(button: Button, action: () -> Unit) {
        val scaleXAnimation = SpringAnimation(button, DynamicAnimation.SCALE_X, 0.95f)
        val scaleYAnimation = SpringAnimation(button, DynamicAnimation.SCALE_Y, 0.95f)

        scaleXAnimation.spring.apply {
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            stiffness = SpringForce.STIFFNESS_HIGH
        }
        scaleYAnimation.spring.apply {
            dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
            stiffness = SpringForce.STIFFNESS_HIGH
        }
        scaleXAnimation.start()
        scaleYAnimation.start()

        button.postDelayed({
            val restoreXAnimation = SpringAnimation(button, DynamicAnimation.SCALE_X, 1f)
            val restoreYAnimation = SpringAnimation(button, DynamicAnimation.SCALE_Y, 1f)
            restoreXAnimation.spring.apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
            restoreYAnimation.spring.apply {
                dampingRatio = SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
                stiffness = SpringForce.STIFFNESS_MEDIUM
            }
            restoreXAnimation.start()
            restoreYAnimation.start()
            action()
        }, 100)
    }

    /** Animates the delay text update to provide visual feedback. */
    private fun animateDelayTextUpdate() {
        delayText.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                delayText.text = "${delayMs}ms"
                delayText.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    /** Displays a dialog for selecting a sorting algorithm. */
    private fun showAlgorithmSelectionDialog() {
        val algorithms = SortingAlgorithm.values()
        val algorithmNames = algorithms.map { it.displayName }.toTypedArray()
        val currentSelection = algorithms.indexOf(selectedAlgorithm)

        AlertDialog.Builder(this)
            .setTitle("Select Sorting Algorithm")
            .setSingleChoiceItems(algorithmNames, currentSelection) { dialog, which ->
                selectedAlgorithm = algorithms[which]
                animateAlgorithmSelection()
                resetUI()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /** Animates the algorithm selection text change. */
    private fun animateAlgorithmSelection() {
        tvSelectedAlgorithm.animate()
            .translationX(-100f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                setColoredAlgorithmName(tvSelectedAlgorithm, "Selected: ${selectedAlgorithm.displayName}", selectedAlgorithm.displayName)
                tvSelectedAlgorithm.translationX = 100f
                tvSelectedAlgorithm.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(OvershootInterpolator(0.5f))
                    .start()
            }
            .start()
    }

    /** Initiates the sorting process based on the selected algorithm. */
    private fun startSorting() {
        if (numbers.isEmpty()) {
            showAnimatedToast("No numbers to sort!")
            return
        }
        if (isSorted) {
            showAnimatedToast("Numbers are already sorted!")
            return
        }

        animateSortingState(true) // Update button state

        sortJob?.cancel() // Cancel any ongoing sort
        sortJob = when (selectedAlgorithm) {
            SortingAlgorithm.BUBBLE_SORT -> startBubbleSort()
            SortingAlgorithm.INSERTION_SORT -> startInsertionSort()
            SortingAlgorithm.SELECTION_SORT -> startSelectionSort()
            SortingAlgorithm.QUICK_SORT -> startQuickSort()
            SortingAlgorithm.MERGE_SORT -> startMergeSort()
        }
    }

    /** Updates the UI state of the sort button during and after sorting. */
    private fun animateSortingState(isSorting: Boolean) {
        if (isSorting) {
            btnSort.text = "SORTING..."
            btnSort.isEnabled = false
        } else {
            btnSort.text = "START SORTING"
            btnSort.isEnabled = true
            activeAnimators.forEach { it.cancel() }
            activeAnimators.clear()
        }
    }

    /** Displays an animated Toast message. */
    private fun showAnimatedToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()

        val toastView = toast.view
        toastView?.let { view ->
            view.alpha = 0f
            view.scaleX = 0f
            view.scaleY = 0f
            view.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(OvershootInterpolator())
                .start()
        }
    }

    // --- Sorting Algorithm Implementations ---
    // Each function runs in a coroutine, updating the bar graph with delays for visualization.

    private fun startBubbleSort(): Job = lifecycleScope.launch {
        val mutableList = numbers.toMutableList()
        var swapped: Boolean
        do {
            swapped = false
            for (i in 0 until mutableList.size - 1) {
                barGraph.numbers = mutableList.toList()
                barGraph.highlightIndices = Pair(i, i + 1)
                barGraph.swappedIndex = null
                delay(delayMs)
                if (mutableList[i] > mutableList[i + 1]) {
                    val temp = mutableList[i]
                    mutableList[i] = mutableList[i + 1]
                    mutableList[i + 1] = temp
                    swapped = true
                    barGraph.numbers = mutableList.toList()
                    barGraph.swappedIndex = i + 1
                    delay(delayMs)
                }
                if (!isActive) return@launch
            }
        } while (swapped)
        finishSorting(mutableList)
    }

    private fun startInsertionSort(): Job = lifecycleScope.launch {
        val mutableList = numbers.toMutableList()
        for (i in 1 until mutableList.size) {
            val key = mutableList[i]
            var j = i - 1

            barGraph.numbers = mutableList.toList()
            barGraph.highlightIndices = Pair(i, i)
            barGraph.swappedIndex = null
            delay(delayMs)

            while (j >= 0 && mutableList[j] > key) {
                barGraph.highlightIndices = Pair(j, j + 1)
                delay(delayMs)

                mutableList[j + 1] = mutableList[j]
                barGraph.numbers = mutableList.toList()
                barGraph.swappedIndex = j + 1
                delay(delayMs)

                j--
                if (!isActive) return@launch
            }
            mutableList[j + 1] = key
            barGraph.numbers = mutableList.toList()
            barGraph.swappedIndex = j + 1
            delay(delayMs)
        }
        finishSorting(mutableList)
    }

    private fun startSelectionSort(): Job = lifecycleScope.launch {
        val mutableList = numbers.toMutableList()
        for (i in 0 until mutableList.size - 1) {
            var minIndex = i
            barGraph.numbers = mutableList.toList()
            barGraph.highlightIndices = Pair(i, i)
            delay(delayMs)

            for (j in i + 1 until mutableList.size) {
                barGraph.highlightIndices = Pair(minIndex, j)
                delay(delayMs)

                if (mutableList[j] < mutableList[minIndex]) {
                    minIndex = j
                    barGraph.highlightIndices = Pair(i, minIndex)
                    delay(delayMs)
                }
                if (!isActive) return@launch
            }

            if (minIndex != i) {
                val temp = mutableList[i]
                mutableList[i] = mutableList[minIndex]
                mutableList[minIndex] = temp
                barGraph.numbers = mutableList.toList()
                barGraph.swappedIndex = i
                delay(delayMs)
            }
        }
        finishSorting(mutableList)
    }

    private fun startQuickSort(): Job = lifecycleScope.launch {
        val mutableList = numbers.toMutableList()
        quickSortRecursive(mutableList, 0, mutableList.size - 1)
        finishSorting(mutableList)
    }

    /** Recursive helper for Quick Sort. */
    private suspend fun quickSortRecursive(arr: MutableList<Int>, low: Int, high: Int) {
        if (low < high) {
            val pivot = partition(arr, low, high)
            quickSortRecursive(arr, low, pivot - 1)
            quickSortRecursive(arr, pivot + 1, high)
        }
    }

    /** Partitions the array around a pivot for Quick Sort. */
    private suspend fun partition(arr: MutableList<Int>, low: Int, high: Int): Int {
        val pivot = arr[high]
        var i = low - 1

        for (j in low until high) {
            barGraph.numbers = arr.toList()
            barGraph.highlightIndices = Pair(j, high)
            delay(delayMs)

            if (arr[j] <= pivot) {
                i++
                if (i != j) {
                    val temp = arr[i]
                    arr[i] = arr[j]
                    arr[j] = temp
                    barGraph.numbers = arr.toList()
                    barGraph.swappedIndex = i
                    delay(delayMs)
                }
            }
        }

        val temp = arr[i + 1]
        arr[i + 1] = arr[high]
        arr[high] = temp
        barGraph.numbers = arr.toList()
        barGraph.swappedIndex = i + 1
        delay(delayMs)

        return i + 1
    }

    private fun startMergeSort(): Job = lifecycleScope.launch {
        val mutableList = numbers.toMutableList()
        mergeSortRecursive(mutableList, 0, mutableList.size - 1)
        finishSorting(mutableList)
    }

    /** Recursive helper for Merge Sort. */
    private suspend fun mergeSortRecursive(arr: MutableList<Int>, left: Int, right: Int) {
        if (left < right) {
            val mid = left + (right - left) / 2
            mergeSortRecursive(arr, left, mid)
            mergeSortRecursive(arr, mid + 1, right)
            merge(arr, left, mid, right)
        }
    }

    /** Merges two sorted sub-arrays for Merge Sort. */
    private suspend fun merge(arr: MutableList<Int>, left: Int, mid: Int, right: Int) {
        val leftArray = arr.subList(left, mid + 1).toMutableList()
        val rightArray = arr.subList(mid + 1, right + 1).toMutableList()

        var i = 0
        var j = 0
        var k = left

        while (i < leftArray.size && j < rightArray.size) {
            barGraph.highlightIndices = Pair(k, k)
            delay(delayMs)

            if (leftArray[i] <= rightArray[j]) {
                arr[k] = leftArray[i]
                i++
            } else {
                arr[k] = rightArray[j]
                j++
            }
            barGraph.numbers = arr.toList()
            barGraph.swappedIndex = k
            delay(delayMs)
            k++
        }

        while (i < leftArray.size) {
            arr[k] = leftArray[i]
            barGraph.numbers = arr.toList()
            barGraph.swappedIndex = k
            delay(delayMs)
            i++
            k++
        }

        while (j < rightArray.size) {
            arr[k] = rightArray[j]
            barGraph.numbers = arr.toList()
            barGraph.swappedIndex = k
            delay(delayMs)
            j++
            k++
        }
    }

    /** Finalizes sorting visualization and updates UI after completion. */
    private fun finishSorting(sortedList: MutableList<Int>) {
        barGraph.numbers = sortedList.toList()
        barGraph.highlightIndices = null
        barGraph.swappedIndex = null
        isSorted = true

        animateSortingState(false)
        setColoredNumbers(tvSorted, "SORTED: ${sortedList.joinToString()}")
        showAnimatedToast("${selectedAlgorithm.displayName} complete!")
    }

    /** Resets the UI to its initial state. */
    private fun resetUI() {
        numbers = listOf()
        barGraph.numbers = emptyList()
        barGraph.highlightIndices = null
        barGraph.swappedIndex = null
        isSorted = false
        tvUnsorted.text = "UNSORTED: "
        tvSorted.text = "SORTED: "
        animateSortingState(false)
        showAnimatedToast("Reset to initial state")
    }

    // --- Utility Functions ---

    /** Sets the text of a TextView, coloring numeric parts. */
    private fun setColoredNumbers(textView: TextView, fullText: String) {
        val spannable = SpannableString(fullText)
        val regex = "\\d+".toRegex()
        regex.findAll(fullText).forEach {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.accent_cyan)),
                it.range.first,
                it.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = spannable
    }

    /** Sets the text of a TextView, coloring the algorithm name. */
    private fun setColoredAlgorithmName(textView: TextView, fullText: String, algorithmName: String) {
        val spannable = SpannableString(fullText)
        val startIndex = fullText.indexOf(algorithmName)
        if (startIndex >= 0) {
            spannable.setSpan(
                ForegroundColorSpan(ContextCompat.getColor(this, R.color.glass_text_secondary)),
                startIndex,
                startIndex + algorithmName.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        textView.text = spannable
    }

    /** Handles the result from the file picker activity. */
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_FILE && resultCode == Activity.RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.openInputStream(uri)?.bufferedReader().use { reader ->
                    numbers = reader?.readLines()?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                    if (numbers.isEmpty()) {
                        showAnimatedToast("File is empty or invalid!")
                    } else {
                        animateDataLoaded()
                    }
                    barGraph.numbers = numbers
                    barGraph.highlightIndices = null
                    barGraph.swappedIndex = null
                    isSorted = false
                    setColoredNumbers(tvUnsorted, "UNSORTED: ${numbers.joinToString()}")
                    tvSorted.text = "SORTED: "
                }
            }
        }
    }

    /** Animates the display of loaded data (unsorted text and bar graph). */
    private fun animateDataLoaded() {
        tvUnsorted.alpha = 0f
        tvUnsorted.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(DecelerateInterpolator())
            .start()

        barGraph.alpha = 0f
        barGraph.animate()
            .alpha(1f)
            .setDuration(800)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    /** Cleans up active animators when the activity is destroyed. */
    override fun onDestroy() {
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
        super.onDestroy()
    }
}
