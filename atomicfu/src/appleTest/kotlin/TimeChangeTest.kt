import kotlinx.atomicfu.locks.Fut
import kotlinx.atomicfu.locks.ParkingSupport
import kotlinx.atomicfu.locks.sleepMills
import kotlinx.datetime.Clock.System
import kotlin.time.Duration.Companion.minutes
import kotlinx.cinterop.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import platform.posix.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class TimeChangeTest {
    @Ignore // needs sudo rights
    @Test
    fun timeChangeTest() {
        val f = Fut {
            ParkingSupport.park(30.seconds)
        }

        sleepMills(3000)
        
        addToSystemTime((-4).minutes)
        
        val time = measureTime {
            f.waitThrowing()
        }
        println("time: $time")
        assertTrue(time < 35.seconds)
    }
}

fun addToSystemTime(duration: Duration) {
    val instant = System.now()
    println("Current time: $instant")

    val newTime = instant.plus(duration)
    
    
    val dateString = newTime.formatToString("MMddHHmmyy")

    executeCommand("sudo date $dateString")
    println("System time successfully changed to: ${System.now()}")

}

fun executeCommand(
    command: String,
    trim: Boolean = true,
    redirectStderr: Boolean = true
): String {
    val commandToExecute = if (redirectStderr) "$command 2>&1" else command
    val fp = popen(commandToExecute, "r") ?: error("Failed to run command: $command")

    val stdout = buildString {
        val buffer = ByteArray(4096)
        while (true) {
            val input = fgets(buffer.refTo(0), buffer.size, fp) ?: break
            append(input.toKString())
        }
    }

    val status = pclose(fp)
    if (status != 0) {
        error("Command `$command` failed with status $status${if (redirectStderr) ": $stdout" else ""}")
    }

    return if (trim) stdout.trim() else stdout
}


fun Instant.formatToString(pattern: String = "MMddHHmmyy", timeZone: TimeZone = TimeZone.currentSystemDefault()): String {
    // Convert Instant to LocalDateTime
    val localDateTime = this.toLocalDateTime(timeZone)

    // Extract components
    val month = localDateTime.month.number.toString().padStart(2, '0')
    val day = localDateTime.dayOfMonth.toString().padStart(2, '0')
    val hour = localDateTime.hour.toString().padStart(2, '0')
    val minute = localDateTime.minute.toString().padStart(2, '0')
    val year = (localDateTime.year % 100).toString().padStart(2, '0')

    // Handle different patterns
    return when (pattern) {
        "MMddHHmmyy" -> "$month$day$hour$minute$year"
        else -> throw IllegalArgumentException("Unsupported pattern: $pattern")
    }
}
