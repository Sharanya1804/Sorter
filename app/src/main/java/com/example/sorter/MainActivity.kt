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

class MainActivity : ComponentActivity() {
    private val PICK_FILE = 1
    private var numbers: List<Int> = listOf()
    private lateinit var barGraph: BarGraphView
    private lateinit var seekBar: SeekBar
    private lateinit var delayText: TextView
    private lateinit var tvUnsorted: TextView
    private lateinit var tvSorted: TextView
    private lateinit var tvSelectedAlgorithm: TextView
    private lateinit var btnSelectAlgorithm: Button
    private lateinit var btnChooseFile: Button
    private lateinit var btnSort: Button
    private var sortJob: Job? = null
    private var delayMs = 300L
    private var isSorted = false
    private var selectedAlgorithm = SortingAlgorithm.BUBBLE_SORT

    // Animation management
    private val activeAnimators = mutableSetOf<Animator>()

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

        // Setup initial values
        seekBar.max=1000
        seekBar.progress = delayMs.toInt()
        delayText.text = "${delayMs}ms"
        setColoredAlgorithmName(tvSelectedAlgorithm, "Selected: ${selectedAlgorithm.displayName}", selectedAlgorithm.displayName)
    }

    private fun setupInitialAnimations() {
        // Animate UI elements on startup
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

    private fun setupEventListeners() {
        // Enhanced delay slider with animation
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

        // Enhanced algorithm selection with animations
        btnSelectAlgorithm.setOnClickListener {
            animateButtonPress(btnSelectAlgorithm) {
                showAlgorithmSelectionDialog()
            }
        }

        // Enhanced file chooser with animations
        btnChooseFile.setOnClickListener {
            animateButtonPress(btnChooseFile) {
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "text/plain"
                }
                startActivityForResult(intent, PICK_FILE)
            }
        }

        // Enhanced sort button with animations
        btnSort.setOnClickListener {
            animateButtonPress(btnSort) {
                startSorting()
            }
        }

        // Back button handler
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                sortJob?.cancel()
                resetUI()
            }
        })
    }

    private fun animateButtonPress(button: Button, action: () -> Unit) {
        // Create spring animation for button press
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

        // Animate back to normal size and execute action
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

    private fun animateDelayTextUpdate() {
        // Pulse animation for delay text
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

    private fun animateAlgorithmSelection() {
        // Slide out old text
        tvSelectedAlgorithm.animate()
            .translationX(-100f)
            .alpha(0f)
            .setDuration(200)
            .withEndAction {
                setColoredAlgorithmName(tvSelectedAlgorithm, "Selected: ${selectedAlgorithm.displayName}", selectedAlgorithm.displayName)
                // Slide in new text
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

    private fun startSorting() {
        if (numbers.isEmpty()) {
            showAnimatedToast("No numbers to sort!")
            return
        }
        if (isSorted) {
            showAnimatedToast("Numbers are already sorted!")
            return
        }

        // Animate sort button during sorting
        animateSortingState(true)

        sortJob?.cancel()
        sortJob = when (selectedAlgorithm) {
            SortingAlgorithm.BUBBLE_SORT -> startBubbleSort()
            SortingAlgorithm.INSERTION_SORT -> startInsertionSort()
            SortingAlgorithm.SELECTION_SORT -> startSelectionSort()
            SortingAlgorithm.QUICK_SORT -> startQuickSort()
            SortingAlgorithm.MERGE_SORT -> startMergeSort()
        }
    }

    private fun animateSortingState(isSorting: Boolean) {
        if (isSorting) {
            btnSort.text = "SORTING..."
            btnSort.isEnabled = false
            // Rotation animation removed - button stays static during sorting
        } else {
            btnSort.text = "START SORTING"
            btnSort.isEnabled = true
            // No need to reset rotation since there's no rotation animation
            activeAnimators.forEach { it.cancel() }
            activeAnimators.clear()
        }
    }


    private fun showAnimatedToast(message: String) {
        val toast = Toast.makeText(this, message, Toast.LENGTH_SHORT)
        toast.show()

        // Find toast view and animate it
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

    // Simplified sorting algorithms without bar graph animations
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

    private suspend fun quickSortRecursive(arr: MutableList<Int>, low: Int, high: Int) {
        if (low < high) {
            val pivot = partition(arr, low, high)
            quickSortRecursive(arr, low, pivot - 1)
            quickSortRecursive(arr, pivot + 1, high)
        }
    }

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

    private suspend fun mergeSortRecursive(arr: MutableList<Int>, left: Int, right: Int) {
        if (left < right) {
            val mid = left + (right - left) / 2
            mergeSortRecursive(arr, left, mid)
            mergeSortRecursive(arr, mid + 1, right)
            merge(arr, left, mid, right)
        }
    }

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

    private fun finishSorting(sortedList: MutableList<Int>) {
        barGraph.numbers = sortedList.toList()
        barGraph.highlightIndices = null
        barGraph.swappedIndex = null
        isSorted = true

        animateSortingState(false)
        setColoredNumbers(tvSorted, "SORTED: ${sortedList.joinToString()}")
        showAnimatedToast("${selectedAlgorithm.displayName} complete!")
    }

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

    // Utility functions for colored text
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

    override fun onDestroy() {
        activeAnimators.forEach { it.cancel() }
        activeAnimators.clear()
        super.onDestroy()
    }
}
