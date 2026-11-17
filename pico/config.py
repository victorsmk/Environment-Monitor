# Settings of the weather station. Change a value here, not in main.py.
# All times are in milliseconds unless the name says otherwise.

# --- Wiring ---------------------------------------------------------------
I2C_SENSORS_ID = 0   # BMP280 + BH1750
SDA_SENSORS = 0
SCL_SENSORS = 1
I2C_OLED_ID = 1      # SSD1306 display
SDA_OLED = 2
SCL_OLED = 3
I2C_FREQ = 400000
DHT_PIN = 15         # DHT22 (humidity)
MIC_PIN = 26         # MAX4466 microphone (ADC0)

# --- How often each sensor is read ---------------------------------------
INTERVAL_BMP = 1000
INTERVAL_DHT = 2500      # the DHT22 cannot be read faster than every 2 seconds
INTERVAL_LIGHT = 1000
LIGHT_MEASURE_MS = 200   # the BH1750 needs up to 180 ms to measure; it is read this long after it is started
INTERVAL_DISPLAY = 1000
INTERVAL_REPORT = 1000   # one line on the serial console
INTERVAL_REINIT = 10000  # how often a sensor that failed to start (or broke) is tried again

# --- How old a value may get before it counts as "no value" ---------------
# A sensor that stops answering must show "--" and be sent as null, never as a frozen number.
# The limits are a few read intervals long, so one failed read does not blank the value.
MAX_AGE_BMP = 5000
MAX_AGE_DHT = 10000
MAX_AGE_LIGHT = 5000
MAX_AGE_SOUND = 5000

# --- Microphone ------------------------------------------------------------
MIC_SAMPLE_INTERVAL_US = 250   # target: 4000 samples per second
MIC_BURST_US = 10000           # the microphone is sampled in bursts of 10 ms between the other jobs
MIC_WINDOW_MS = 100            # RMS is worked out per 100 ms window
MIC_REPORT_MS = 1000           # and the sound level is updated once a second
MIC_MIN_WINDOW_SAMPLES = 100   # a window with fewer samples (the loop was busy) is not trusted

# Sound level 0-100 from the loudest 100 ms window of the last second.
# These two numbers are RMS values in 16-bit ADC units (the same units as the first version of the
# program used). Calibrate them: QUIET = the room at its quietest, LOUD = a really loud room.
NOISE_RMS_QUIET = 80
NOISE_RMS_LOUD = 2500

# --- Safety ---------------------------------------------------------------
# The watchdog restarts the Pico if the program ever hangs for WATCHDOG_MS. It cannot be switched off
# once started, so while you are editing and testing in Thonny set USE_WATCHDOG = False, otherwise the
# Pico restarts 8 seconds after you press Stop.
USE_WATCHDOG = True
WATCHDOG_MS = 8000
