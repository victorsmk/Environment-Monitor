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

# Sound level 0-100 from the loudest 100 ms window of the last second, on a logarithmic scale (every
# doubling of the sound adds the same number of points). These two numbers are RMS values in 16-bit ADC
# units: QUIET = the loudest the quiet room gets (reads 0), LOUD = a sound that reads 100.
# Measured on the real Pico (November 2026, peak of a 100 ms window): silence 75 (never above 92), normal
# talking 127 (up to 164), loud voice or music 210 (up to 2700), shouting or hard claps 1200 (up to 2300).
# The microphone is not very sensitive; turning up the gain trimmer on the MAX4466 board would help.
NOISE_RMS_QUIET = 90
NOISE_RMS_LOUD = 2000

# --- Network (Wi-Fi, the /data page and finding the Pico) ------------------
USE_NETWORK = True
HOSTNAME = "pico-meteo"
HTTP_PORT = 80                  # GET /data
DISCOVERY_PORT = 4210           # UDP: the app asks "PICO?" and the Pico answers with its address
HTTP_REQUEST_TIMEOUT_MS = 500   # a connection that sends nothing for this long is dropped

WIFI_TRY_MS = 10000     # how long each known network gets before the next one is tried
WIFI_LOST_MS = 5000     # a joined network that has been gone this long is treated as lost
WIFI_KEEPALIVE_MS = 10000  # while joined, a tiny packet goes to the gateway this often (see wifi.py)
AP_AFTER_MS = 30000     # if no known network connects within this, the Pico opens its own network
AP_SSID = "PicoMeteo"   # ...with this name, at AP_ADDRESS. Needs AP_PASSWORD in secrets.py.
AP_ADDRESS = "192.168.4.1"
AP_RETRY_MS = 60000     # in its own network with nobody connected, a known network is tried again this often

# --- Safety ---------------------------------------------------------------
# The watchdog restarts the Pico if the program ever hangs for WATCHDOG_MS. It cannot be switched off
# once started, so while you are editing and testing in Thonny set USE_WATCHDOG = False, otherwise the
# Pico restarts 8 seconds after you press Stop.
USE_WATCHDOG = True
WATCHDOG_MS = 8000
