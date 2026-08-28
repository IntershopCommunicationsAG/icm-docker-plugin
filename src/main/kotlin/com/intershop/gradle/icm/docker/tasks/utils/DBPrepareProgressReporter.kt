package com.intershop.gradle.icm.docker.tasks.utils

import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.management.MBeanServerConnection
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

/**
 * Reads the preparation progress of the dbPrepare process running inside the container and reports it as a rendered
 * progress bar.
 *
 * The dbPrepare process exposes its progress as JMX attributes instead of writing them to its output, so that the
 * progress cannot interfere with the log output. Unlike a locally started process, a process inside a container cannot
 * be attached to by its process id, so it is read through the JMX connector of the container. The connector is enabled
 * by the environment variable `ENABLE_JMX`, see the entrypoint of the ICM-AS image.
 *
 * All errors are handled silently: the progress is purely informational and must never fail the build.
 *
 * @param host the host the JMX connector of the container is reachable at
 * @param port the port the JMX connector of the container is published to
 * @param onProgress consumer of the rendered progress bar
 */
class DBPrepareProgressReporter(
    private val host: String,
    private val port: Int,
    private val onProgress: (String) -> Unit,
) {

    companion object {
        /**
         * Object name of the MBean exposing the progress.
         * Must be kept in sync with `PreparationProgressExecutionListener` of ICM-AS.
         */
        private const val MBEAN_NAME = "com.intershop.enfinity:type=DBPrepareProgress"

        private val ATTRIBUTES = arrayOf(
            "ProgressPercent", "ProcessedSteps", "TotalSteps", "CurrentStep", "CurrentCartridge"
        )

        /** Delay before the first attempt, the process inside the container needs some time to start up. */
        private val INITIAL_DELAY = Duration.ofSeconds(3)

        /** Delay between two progress updates. */
        private val POLL_DELAY = Duration.ofMillis(500)

        /** Number of characters used to render the progress bar itself. */
        private const val BAR_WIDTH = 42

        /** Characters of the progress bar, as used by the progress bar of Gradle itself. */
        private const val BLOCK = '\u2588'

        /** Half of a [BLOCK], used to render a half filled position of the progress bar. */
        private const val HALF_BLOCK = '\u258c'

        /** Character of a position of the progress bar that is not reached yet. */
        private const val REMAINING = '\u00b7'

        /** Character enclosing the progress bar. */
        private const val DELIMITER = '\u2502'

        /**
         * Renders the progress bar shown in Gradle's status line.
         *
         * @param percent the progress in percent
         * @param processed the number of executed preparation steps
         * @param total the number of preparation steps expected to be executed
         * @param cartridge the path of the cartridge of the current step, may be `null` or empty
         * @param step the name of the current step, may be `null` or empty
         * @return the rendered progress bar
         */
        @JvmStatic
        fun render(percent: Int, processed: Int, total: Int, cartridge: String?, step: String?): String {
            // mimics the progress bar of Gradle itself, a position can be filled by half so count in half positions
            val filledHalves = BAR_WIDTH * 2 * percent.coerceIn(0, 100) / 100
            val filled = filledHalves / 2
            val half = filledHalves % 2 != 0
            val bar = buildString {
                append(BLOCK.toString().repeat(filled))
                if (half) {
                    append(HALF_BLOCK)
                }
                append(REMAINING.toString().repeat(BAR_WIDTH - filled - if (half) 1 else 0))
            }
            val current = listOfNotNull(
                cartridge?.takeIf { it.isNotEmpty() }?.let { "[$it]" },
                step?.takeIf { it.isNotEmpty() }
            ).joinToString(" ")

            val progress = "$DELIMITER$bar$DELIMITER $percent% $processed/$total preparation steps"
            return if (current.isEmpty()) progress else "$progress - $current"
        }
    }

    // daemon thread: progress reporting is informational only and must never block JVM shutdown
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "dbPrepare-progress").apply { isDaemon = true }
    }

    /**
     * The connection is established by the thread reading the progress,
     * but it is released by the thread stopping this reporter,
     * so the state shared by both threads is volatile.
     */
    @Volatile
    private var connector: JMXConnector? = null

    @Volatile
    private var connection: MBeanServerConnection? = null

    /** Set by [stop], so no connection is established anymore
     * once this reporter has been stopped. */
    @Volatile
    private var stopped = false

    private val objectName = ObjectName(MBEAN_NAME)

    /**
     * Starts reading the progress in the background.
     */
    fun start() {
        executor.scheduleWithFixedDelay(
            { readProgress() }, INITIAL_DELAY.toMillis(), POLL_DELAY.toMillis(), TimeUnit.MILLISECONDS
        )
    }

    /**
     * Stops reading the progress and releases the connection to the container.
     */
    fun stop() {
        // set before the connection is released, so a connection established concurrently is released as well
        stopped = true
        executor.shutdownNow()
        releaseConnection()
    }

    private fun readProgress() {
        try {
            val mBeanServer = connection ?: connect() ?: return
            // the MBean only exists while preparation steps are executed
            if (!mBeanServer.isRegistered(objectName)) {
                return
            }
            val values = mBeanServer.getAttributes(objectName, ATTRIBUTES)
                .associate { (it as javax.management.Attribute).name to it.value }

            val processed = values["ProcessedSteps"] as? Int ?: return
            val total = values["TotalSteps"] as? Int ?: return
            val percent = values["ProgressPercent"] as? Int ?: return

            onProgress(
                render(
                    percent, processed, total, values["CurrentCartridge"] as? String, values["CurrentStep"] as? String
                )
            )
        } catch (_: Exception) {
            releaseConnection()
        }
    }

    /**
     * Releases the connection to the container.
     * It is safe to call this concurrently to the thread reading the progress:
     * that thread only re-establishes a connection while this reporter has not been stopped.
     */
    private fun releaseConnection() {
        runCatching { connector?.close() }
        connector = null
        connection = null
    }

    /**
     * Connects to the JMX connector of the container.
     *
     * @return the connection to the MBean server, `null` if this reporter has been stopped meanwhile
     */
    private fun connect(): MBeanServerConnection? {
        val jmxConnector = JMXConnectorFactory.connect(
            JMXServiceURL("service:jmx:rmi:///jndi/rmi://$host:$port/jmxrmi")
        )
        if (stopped) {
            // stopped while connecting, the connection would not be released by anybody else
            runCatching { jmxConnector.close() }
            return null
        }
        connector = jmxConnector
        connection = jmxConnector.mBeanServerConnection
        return jmxConnector.mBeanServerConnection
    }
}
