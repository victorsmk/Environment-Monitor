# BMP280 temperature and pressure sensor (also works with the BME280, which has the same temperature
# and pressure registers). Small driver written for this project, so it does not depend on a library
# of unknown origin. The conversion uses Bosch's integer formulas from the datasheet, so the result is
# exact, not rounded by the Pico's 32-bit floats.
#
# One measurement is taken on request ("forced mode") and the sensor sleeps in between. That keeps its
# self-heating negligible, which matters because a BMP280 left measuring all the time reads warm.

import struct
import time

REG_CHIP_ID = 0xD0
REG_RESET = 0xE0
REG_STATUS = 0xF3
REG_CTRL_MEAS = 0xF4
REG_CONFIG = 0xF5
REG_DATA = 0xF7
REG_CALIBRATION = 0x88

CHIP_IDS = (0x56, 0x57, 0x58, 0x60)  # BMP280 samples and final parts, and the BME280
ADDRESSES = (0x76, 0x77)             # 0x76 when the SDO pin is low (the usual case), 0x77 when it is high

# Temperature x2, pressure x4, forced mode: a measurement takes about 14 ms.
CTRL_FORCED = (2 << 5) | (3 << 2) | 1
CONFIG_FILTER_4 = 2 << 2


def _divide_towards_zero(a, b):
    """Integer division that rounds towards zero, as in the C code of the datasheet."""
    q = abs(a) // abs(b)
    return -q if (a < 0) != (b < 0) else q


class BMP280:
    def __init__(self, i2c, address=None):
        self.i2c = i2c
        if address is None:
            found = i2c.scan()
            for candidate in ADDRESSES:
                if candidate in found:
                    address = candidate
                    break
            else:
                raise OSError("BMP280 not found on the I2C bus")
        self.address = address
        chip = i2c.readfrom_mem(address, REG_CHIP_ID, 1)[0]
        if chip not in CHIP_IDS:
            raise OSError("not a BMP280: chip id 0x%02x" % chip)
        i2c.writeto_mem(address, REG_RESET, b"\xb6")  # start from a known state
        time.sleep_ms(5)
        i2c.writeto_mem(address, REG_CONFIG, bytes([CONFIG_FILTER_4]))
        self._calibration = struct.unpack("<HhhHhhhhhhhh", i2c.readfrom_mem(address, REG_CALIBRATION, 24))

    def measure(self):
        """(temperature in degrees C, pressure in Pa). Takes about 14 ms."""
        self.i2c.writeto_mem(self.address, REG_CTRL_MEAS, bytes([CTRL_FORCED]))
        time.sleep_ms(2)  # the sensor needs a moment before it reports that it is measuring
        started = time.ticks_ms()
        while self.i2c.readfrom_mem(self.address, REG_STATUS, 1)[0] & 0x08:  # bit 3: measuring
            if time.ticks_diff(time.ticks_ms(), started) > 100:
                raise OSError("BMP280 did not finish measuring")
            time.sleep_ms(1)
        data = self.i2c.readfrom_mem(self.address, REG_DATA, 6)
        raw_pressure = (data[0] << 12) | (data[1] << 4) | (data[2] >> 4)
        raw_temperature = (data[3] << 12) | (data[4] << 4) | (data[5] >> 4)
        return self.compensate(raw_temperature, raw_pressure)

    @property
    def temperature(self):
        return self.measure()[0]

    @property
    def pressure(self):
        return self.measure()[1]

    def compensate(self, raw_temperature, raw_pressure):
        """Raw sensor numbers to (degrees C, Pa), with the formulas of the BMP280 datasheet."""
        t1, t2, t3, p1, p2, p3, p4, p5, p6, p7, p8, p9 = self._calibration

        # Temperature, in hundredths of a degree
        var1 = (((raw_temperature >> 3) - (t1 << 1)) * t2) >> 11
        var2 = (((((raw_temperature >> 4) - t1) * ((raw_temperature >> 4) - t1)) >> 12) * t3) >> 14
        t_fine = var1 + var2
        temperature = ((t_fine * 5 + 128) >> 8) / 100

        # Pressure, in 1/256 Pa
        var1 = t_fine - 128000
        var2 = var1 * var1 * p6
        var2 = var2 + ((var1 * p5) << 17)
        var2 = var2 + (p4 << 35)
        var1 = ((var1 * var1 * p3) >> 8) + ((var1 * p2) << 12)
        var1 = (((1 << 47) + var1) * p1) >> 33
        if var1 == 0:
            return temperature, 0.0  # would divide by zero: the calibration data is not valid
        pressure = 1048576 - raw_pressure
        pressure = _divide_towards_zero(((pressure << 31) - var2) * 3125, var1)
        var1 = (p9 * (pressure >> 13) * (pressure >> 13)) >> 25
        var2 = (p8 * pressure) >> 19
        pressure = ((pressure + var1 + var2) >> 8) + (p7 << 4)
        return temperature, pressure / 256
