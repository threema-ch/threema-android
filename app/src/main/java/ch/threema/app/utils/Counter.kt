package ch.threema.app.utils

/**
 * A [Counter] that can be used whenever things have to be counted and for any reason
 * a simple variable will not do.
 * It also allows counting 'steps' where a step consist of multiple counts. The step size
 * can be defined using the [stepSize] constructor parameter.
 *
 * @param stepSize The number of counts that make a step. Must be greater than zero.
 *                 Defaults to one.
 *
 * @throws IllegalArgumentException if [stepSize] is <= 0
 */
class Counter(private val stepSize: Long) {
    init {
        require(stepSize > 0) { "stepSize must be > 0" }
    }

    private var _count = 0L
    private var _steps = 0L

    constructor() : this(1)

    /**
     * @return the current count of this [Counter]
     */
    val count: Long
        get() = _count

    /**
     * @return The current number of steps this counter has encountered so far.
     *         Note that the number of steps can be reset to zero, when
     *         [getAndResetSteps] is used.
     */
    val steps: Long
        get() = _steps

    /**
     * Increment the value of this counter by one.
     */
    fun count() {
        _count++
        if ((_count % stepSize) == 0L) {
            _steps++
        }
    }

    /**
     * Get the counted steps if they exceed [threshold].
     * If the [threshold] is exceeded the step counter is reset to zero.
     *
     * @return number of steps if [threshold] is exceeded, 0L otherwise.
     */
    fun getAndResetSteps(threshold: Long): Long {
        if (_steps < threshold) {
            return 0L
        }
        val steps = _steps
        _steps = 0
        return steps
    }

    override fun toString(): String {
        return "$_count"
    }
}
