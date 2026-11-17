
import machine
from machine import Pin, I2C, ADC
import time
import math
import dht
import ssd1306

from bmp280 import BMP280

# ============================================================
# 1. IMPORT BH1750
# ============================================================

try:
    from bh1750 import BH1750
except ImportError:
    BH1750 = None


# ============================================================
# 2. CONFIGURARE GENERALA
# ============================================================

# Intervalele de citire (milisecunde)

INTERVAL_BMP = 1000
INTERVAL_DHT = 2500
INTERVAL_LUMINA = 1000
INTERVAL_DISPLAY = 1000

# Microfon

MIC_SAMPLE_INTERVAL_US = 250   # Tinta: 4000 esantioane/secunda
MIC_WINDOW_MS = 100             # Ferestre RMS de 100 ms
MIC_REPORT_INTERVAL_MS = 1000  # Agregare si actualizare la 1 secunda

# Calibrare initiala zgomot
# Aceste valori trebuie ajustate experimental.

NOISE_RMS_QUIET = 80
NOISE_RMS_LOUD = 2500

# LED - temperatura
TEMP_LED_ON = 26.0
TEMP_LED_OFF = 25.5

# Pauza foarte scurta intre iteratiile buclei principale
MAIN_LOOP_SLEEP_US = 50


# ============================================================
# 3. INITIALIZARE PINI SI INTERFETE
# ============================================================

i2c_senzori = I2C(
    0,
    sda=Pin(0),
    scl=Pin(1),
    freq=400000
)

i2c_oled = I2C(
    1,
    sda=Pin(2),
    scl=Pin(3),
    freq=400000
)

led = Pin("LED", Pin.OUT)

senzor_dht = dht.DHT22(Pin(15))

microfon = ADC(Pin(26))


# ============================================================
# 4. INITIALIZARE SENZORI
# ============================================================

# OLED

try:
    oled = ssd1306.SSD1306_I2C(128, 64, i2c_oled)
    print("OLED initializat.")

except Exception as e:
    print("Eroare OLED:", e)
    oled = None


# BMP280

try:
    senzor_bmp = BMP280(i2c_senzori)
    print("BMP280 initializat.")

except Exception as e:
    print("Eroare BMP280:", e)
    senzor_bmp = None


# BH1750

try:
    if BH1750:
        senzor_lumina = BH1750(i2c_senzori)
        print("BH1750 initializat.")
    else:
        senzor_lumina = None
        print("Libraria BH1750 nu este disponibila.")

except Exception as e:
    print("Eroare BH1750:", e)
    senzor_lumina = None


print("DHT22 configurat.")
print("MAX4466 configurat pe GP26.")


# ============================================================
# 5. STRUCTURA DATELOR
# ============================================================

# Pastram valorile numerice.
# Acestea vor putea fi folosite ulterior direct pentru JSON/MQTT.

ultimele_date = {

    "temperature": None,
    "humidity": None,
    "pressure_hpa": None,
    "light_lux": None,

    "noise_level": None,
    "noise_rms": None,
    "noise_peak_rms": None,

    "noise_samples": 0,
    "noise_adc_min": None,
    "noise_adc_max": None
}


# Starea senzorilor

stare_senzori = {

    "bmp280": False,
    "dht22": False,
    "bh1750": False
}


# ============================================================
# 6. RAPORTAREA ERORILOR
# ============================================================

ultima_raportare_eroare = {}


def raporteaza_eroare(nume, eroare):

    acum = time.ticks_ms()

    ultima = ultima_raportare_eroare.get(nume)

    # Nu afisam aceeasi eroare la fiecare citire.
    # Raportam cel mult o data la 5 secunde.

    if ultima is None or time.ticks_diff(acum, ultima) >= 5000:

        print("[EROARE]", nume, ":", eroare)

        ultima_raportare_eroare[nume] = acum


# ============================================================
# 7. DISPLAY OLED
# ============================================================

def animatie_boot():

    if oled is None:
        return

    oled.fill(0)

    oled.rect(0, 0, 128, 64, 1)

    oled.text("PICO METEO", 25, 20)
    oled.text("Pornire...", 32, 35)

    oled.show()

    time.sleep_ms(500)


def actualizare_display():

    if oled is None:
        return

    oled.fill(0)

    oled.text("PICO METEO", 25, 0)

    # Temperatura

    if ultimele_date["temperature"] is not None:
        temp = "{:.1f}".format(ultimele_date["temperature"])
    else:
        temp = "--"

    oled.text("Temp: " + temp + " C", 0, 10)

    # Umiditate

    if ultimele_date["humidity"] is not None:
        umid = "{:.1f}".format(ultimele_date["humidity"])
    else:
        umid = "--"

    oled.text("Umid: " + umid + " %", 0, 20)

    # Lumina

    if ultimele_date["light_lux"] is not None:
        lumina = "{:.0f}".format(ultimele_date["light_lux"])
    else:
        lumina = "--"

    oled.text("Lux: " + lumina, 0, 30)

    # Zgomot

    if ultimele_date["noise_level"] is not None:
        zgomot = str(ultimele_date["noise_level"])
    else:
        zgomot = "--"

    oled.text("Zgomot: " + zgomot + "/100", 0, 40)

    # RMS

    if ultimele_date["noise_rms"] is not None:
        rms = "{:.0f}".format(ultimele_date["noise_rms"])
    else:
        rms = "--"

    #oled.text("RMS: " + rms, 0, 50)

    oled.show()


# ============================================================
# 8. CITIRE BMP280
# ============================================================

def citeste_bmp():

    if senzor_bmp is None:
        return

    try:

        temperatura = float(senzor_bmp.temperature)

        presiune = float(senzor_bmp.pressure) / 100

        ultimele_date["temperature"] = temperatura
        ultimele_date["pressure_hpa"] = presiune

        stare_senzori["bmp280"] = True

    except Exception as e:

        stare_senzori["bmp280"] = False

        raporteaza_eroare("BMP280", e)


# ============================================================
# 9. CITIRE DHT22
# ============================================================

def citeste_dht():

    try:

        senzor_dht.measure()

        temperatura = float(senzor_dht.temperature())
        umiditate = float(senzor_dht.humidity())

        ultimele_date["humidity"] = umiditate

        stare_senzori["dht22"] = True

    except Exception as e:

        stare_senzori["dht22"] = False

        raporteaza_eroare("DHT22", e)


# ============================================================
# 10. CITIRE BH1750
# ============================================================

def citeste_lumina():

    if senzor_lumina is None:
        return

    try:

        valoare = senzor_lumina.luminance(
            BH1750.ONCE_HIRES_1
        )

        ultimele_date["light_lux"] = float(valoare)

        stare_senzori["bh1750"] = True

    except Exception as e:

        stare_senzori["bh1750"] = False

        raporteaza_eroare("BH1750", e)


# ============================================================
# 11. CALCUL RMS
# ============================================================

def calculeaza_rms(suma, suma_patrate, nr_esantioane):

    if nr_esantioane == 0:
        return 0

    medie = suma / nr_esantioane

    varianta = (
        suma_patrate / nr_esantioane
        - medie * medie
    )

    # Evitam valori negative din cauza erorilor numerice.

    if varianta < 0:
        varianta = 0

    return math.sqrt(varianta)


# ============================================================
# 12. TRANSFORMARE RMS IN INDICATOR 0-100
# ============================================================

def calculeaza_nivel_zgomot(rms):

    if rms <= NOISE_RMS_QUIET:
        return 0

    if rms >= NOISE_RMS_LOUD:
        return 100

    procent = (
        (rms - NOISE_RMS_QUIET)
        / (NOISE_RMS_LOUD - NOISE_RMS_QUIET)
    ) * 100

    return int(procent + 0.5)


# ============================================================
# 13. INITIALIZARE ACUMULATORI MICROFON
# ============================================================

# Acumulatori pentru fereastra de 100 ms

mic_window_sum = 0
mic_window_sum_sq = 0
mic_window_count = 0

# Acumulatori pentru intreaga secunda

mic_second_sum = 0
mic_second_sum_sq = 0
mic_second_count = 0

# Minime si maxime ADC pentru verificarea semnalului

mic_second_min = 65535
mic_second_max = 0

# Maximul RMS obtinut pe ferestrele de 100 ms

mic_max_window_rms = 0

# Ultimul RMS calculat pe o fereastra

mic_last_window_rms = 0

# Temporizari

timp_fereastra_mic = time.ticks_ms()
timp_raportare_mic = time.ticks_ms()

urmator_esantion_mic = time.ticks_us()


# ============================================================
# 14. CITIRE CONTINUA MICROFON
# ============================================================

def preia_esantion_microfon():

    global mic_window_sum
    global mic_window_sum_sq
    global mic_window_count

    global mic_second_sum
    global mic_second_sum_sq
    global mic_second_count

    global mic_second_min
    global mic_second_max

    valoare = microfon.read_u16()

    # Fereastra de 100 ms

    mic_window_sum += valoare
    mic_window_sum_sq += valoare * valoare
    mic_window_count += 1

    # Fereastra de 1 secunda

    mic_second_sum += valoare
    mic_second_sum_sq += valoare * valoare
    mic_second_count += 1

    # Pastram minimul si maximul pentru diagnostic

    if valoare < mic_second_min:
        mic_second_min = valoare

    if valoare > mic_second_max:
        mic_second_max = valoare


# ============================================================
# 15. FINALIZARE FEREASTRA RMS DE 100 ms
# ============================================================

def finalizeaza_fereastra_microfon():

    global mic_window_sum
    global mic_window_sum_sq
    global mic_window_count

    global mic_max_window_rms
    global mic_last_window_rms

    global timp_fereastra_mic

    acum = time.ticks_ms()

    if time.ticks_diff(acum, timp_fereastra_mic) >= MIC_WINDOW_MS:

        if mic_window_count > 0:

            rms = calculeaza_rms(
                mic_window_sum,
                mic_window_sum_sq,
                mic_window_count
            )

            mic_last_window_rms = rms

            if rms > mic_max_window_rms:
                mic_max_window_rms = rms

        # Resetam acumulatorii ferestrei.

        mic_window_sum = 0
        mic_window_sum_sq = 0
        mic_window_count = 0

        timp_fereastra_mic = acum


# ============================================================
# 16. AGREGARE MICROFON PE O SECUNDA
# ============================================================

def finalizeaza_raport_microfon():

    global mic_second_sum
    global mic_second_sum_sq
    global mic_second_count

    global mic_second_min
    global mic_second_max

    global mic_max_window_rms
    global timp_raportare_mic

    global mic_window_sum
    global mic_window_sum_sq
    global mic_window_count
    global timp_fereastra_mic
    global mic_last_window_rms

    acum = time.ticks_ms()

    if time.ticks_diff(acum, timp_raportare_mic) >= MIC_REPORT_INTERVAL_MS:

        if mic_second_count > 0:

            # RMS pe intreaga secunda

            rms_secunda = calculeaza_rms(
                mic_second_sum,
                mic_second_sum_sq,
                mic_second_count
            )

            # Maximul RMS pe ferestre de 100 ms

            rms_maxim = mic_max_window_rms

            ultimele_date["noise_rms"] = round(
                rms_secunda, 1
            )

            ultimele_date["noise_peak_rms"] = round(
                rms_maxim, 1
            )

            # Indicatorul 0-100 se bazeaza pe maximul RMS
            # din ferestrele de 100 ms.

            ultimele_date["noise_level"] = (
                calculeaza_nivel_zgomot(rms_maxim)
            )

            ultimele_date["noise_samples"] = mic_second_count

            ultimele_date["noise_adc_min"] = mic_second_min
            ultimele_date["noise_adc_max"] = mic_second_max

            print(
                "Zgomot:",
                ultimele_date["noise_level"],
                "/100 | RMS:",
                round(rms_secunda, 1),
                "| RMS maxim:",
                round(rms_maxim, 1),
                "| Esantioane:",
                mic_second_count,
                "| ADC min/max:",
                mic_second_min,
                "/",
                mic_second_max
            )

        # Resetam acumulatorii pentru urmatoarea secunda.

        mic_second_sum = 0
        mic_second_sum_sq = 0
        mic_second_count = 0

        mic_second_min = 65535
        mic_second_max = 0

        mic_max_window_rms = 0

        timp_raportare_mic = acum


# ============================================================
# 17. LED CU HISTEREZIS
# ============================================================

led_aprins = False


def actualizare_led():

    global led_aprins

    temperatura = ultimele_date["temperature"]

    if temperatura is None:
        return

    if not led_aprins and temperatura >= TEMP_LED_ON:

        led.on()
        led_aprins = True

    elif led_aprins and temperatura <= TEMP_LED_OFF:

        led.off()
        led_aprins = False


# ============================================================
# 18. PORNIRE SISTEM
# ============================================================

animatie_boot()

acum = time.ticks_ms()

# Setam temporizatoarele astfel incat prima citire
# sa se execute imediat.

ultim_bmp = time.ticks_add(acum, -INTERVAL_BMP)
ultim_dht = time.ticks_add(acum, -INTERVAL_DHT)
ultim_lumina = time.ticks_add(acum, -INTERVAL_LUMINA)
ultim_display = time.ticks_add(acum, -INTERVAL_DISPLAY)


# ============================================================
# 19. BUCLA PRINCIPALA
# ============================================================

print("Sistemul PICO METEO a pornit.")

while True:

    # --------------------------------------------------------
    # A. ESANTIONARE MICROFON
    # --------------------------------------------------------

    acum_us = time.ticks_us()

    if time.ticks_diff(acum_us, urmator_esantion_mic) >= 0:

        preia_esantion_microfon()

        # Programam urmatoarea citire.
        # Nu incercam sa recuperam esantioanele ratate.

        urmator_esantion_mic = time.ticks_add(
            acum_us,
            MIC_SAMPLE_INTERVAL_US
        )

    # --------------------------------------------------------
    # B. PROCESARE FERESTRE MICROFON
    # --------------------------------------------------------

    finalizeaza_fereastra_microfon()

    finalizeaza_raport_microfon()

    # --------------------------------------------------------
    # C. CITIRE BMP280
    # --------------------------------------------------------

    acum = time.ticks_ms()

    if time.ticks_diff(acum, ultim_bmp) >= INTERVAL_BMP:

        citeste_bmp()

        actualizare_led()

        ultim_bmp = time.ticks_ms()

    # --------------------------------------------------------
    # D. CITIRE DHT22
    # --------------------------------------------------------

    acum = time.ticks_ms()

    if time.ticks_diff(acum, ultim_dht) >= INTERVAL_DHT:

        citeste_dht()

        ultim_dht = time.ticks_ms()

    # --------------------------------------------------------
    # E. CITIRE BH1750
    # --------------------------------------------------------

    acum = time.ticks_ms()

    if time.ticks_diff(acum, ultim_lumina) >= INTERVAL_LUMINA:

        citeste_lumina()

        ultim_lumina = time.ticks_ms()

    # --------------------------------------------------------
    # F. ACTUALIZARE OLED
    # --------------------------------------------------------

    acum = time.ticks_ms()

    if time.ticks_diff(acum, ultim_display) >= INTERVAL_DISPLAY:

        actualizare_display()

        ultim_display = time.ticks_ms()

    # --------------------------------------------------------
    # G. PAUZA FOARTE SCURTA
    # --------------------------------------------------------

    time.sleep_us(MAIN_LOOP_SLEEP_US)


