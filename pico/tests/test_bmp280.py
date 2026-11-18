"""Tests for pico/bmp280.py with a pretend sensor on the I2C bus.

The numbers come from Bosch's BMP280 datasheet example: with these calibration values, the raw readings
519888 (temperature) and 415148 (pressure) must give 25.08 degrees C and 100653.27 Pa. (Bosch's pressure
comes from their floating-point formula; the driver uses their exact integer formula, which differs by
about 0.02 Pa, hence the small tolerance.)

    python -m unittest discover -s pico/tests -v
"""
import os
import struct
import sys
import time
import unittest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))
sys.path.insert(0, os.path.dirname(__file__))

import importlib.util  # noqa: E402

import fakes  # noqa: E402,F401  (the MicroPython time functions)

# The other tests put a pretend `bmp280` module in place, so the real driver is loaded from its file.
_spec = importlib.util.spec_from_file_location("real_bmp280", os.path.join(os.path.dirname(__file__), "..", "bmp280.py"))
bmp280 = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bmp280)
BMP280 = bmp280.BMP280

# The datasheet example: T1, T2, T3, P1..P9
CALIBRATION = (27504, 26435, -1000, 36477, -10685, 3024, 2855, 140, -7, 15500, -14600, 6000)
RAW_TEMPERATURE = 519888
RAW_PRESSURE = 415148


def data_bytes(raw_pressure, raw_temperature):
    """The six bytes at register 0xF7: pressure then temperature, 20 bits each, left aligned in 3 bytes."""
    def three(raw):
        return bytes([(raw >> 12) & 0xFF, (raw >> 4) & 0xFF, (raw & 0x0F) << 4])
    return three(raw_pressure) + three(raw_temperature)


class FakeI2C:
    def __init__(self, address=0x76, chip_id=0x58, raw_temperature=RAW_TEMPERATURE, raw_pressure=RAW_PRESSURE):
        self.devices = [address]
        self.address = address
        self.chip_id = chip_id
        self.raw_temperature = raw_temperature
        self.raw_pressure = raw_pressure
        self.writes = []
        self.measuring_polls = 2     # how many status reads say "still measuring"
        self.never_finishes = False
        self._polls_left = 0

    def scan(self):
        return list(self.devices)

    def writeto_mem(self, address, register, data):
        assert address == self.address
        self.writes.append((register, bytes(data)))
        if register == bmp280.REG_CTRL_MEAS:
            self._polls_left = self.measuring_polls

    def readfrom_mem(self, address, register, count):
        assert address == self.address
        if register == bmp280.REG_CHIP_ID:
            return bytes([self.chip_id])
        if register == bmp280.REG_CALIBRATION:
            return struct.pack("<HhhHhhhhhhhh", *CALIBRATION)
        if register == bmp280.REG_STATUS:
            if self.never_finishes or self._polls_left > 0:
                self._polls_left -= 1
                return bytes([0x08])
            return bytes([0x00])
        if register == bmp280.REG_DATA:
            return data_bytes(self.raw_pressure, self.raw_temperature)
        raise AssertionError("unexpected read of register 0x%02x" % register)


class Conversion(unittest.TestCase):
    def setUp(self):
        self.sensor = BMP280(FakeI2C())

    def test_the_datasheet_example_temperature(self):
        temperature, _ = self.sensor.compensate(RAW_TEMPERATURE, RAW_PRESSURE)
        self.assertAlmostEqual(temperature, 25.08, places=2)

    def test_the_datasheet_example_pressure(self):
        _, pressure = self.sensor.compensate(RAW_TEMPERATURE, RAW_PRESSURE)
        self.assertAlmostEqual(pressure, 100653.27, delta=0.05)

    def test_warmer_air_reads_warmer(self):
        cooler = self.sensor.compensate(RAW_TEMPERATURE - 4000, RAW_PRESSURE)[0]
        warmer = self.sensor.compensate(RAW_TEMPERATURE + 4000, RAW_PRESSURE)[0]
        self.assertLess(cooler, 25.08)
        self.assertGreater(warmer, 25.08)

    def test_higher_raw_pressure_means_lower_pressure(self):
        # The sensor reports a smaller number when the pressure is higher; the formula flips it.
        low = self.sensor.compensate(RAW_TEMPERATURE, RAW_PRESSURE - 2000)[1]
        high = self.sensor.compensate(RAW_TEMPERATURE, RAW_PRESSURE + 2000)[1]
        self.assertGreater(low, high)

    def test_broken_calibration_data_does_not_divide_by_zero(self):
        self.sensor._calibration = (27504, 26435, -1000, 0, -10685, 3024, 2855, 140, -7, 15500, -14600, 6000)
        _, pressure = self.sensor.compensate(RAW_TEMPERATURE, RAW_PRESSURE)
        self.assertEqual(pressure, 0.0)


class Measuring(unittest.TestCase):
    def test_measure_gives_temperature_and_pressure_from_one_reading(self):
        i2c = FakeI2C()
        sensor = BMP280(i2c)
        temperature, pressure = sensor.measure()
        self.assertAlmostEqual(temperature, 25.08, places=2)
        self.assertAlmostEqual(pressure, 100653.27, delta=0.05)

    def test_it_asks_for_one_forced_measurement(self):
        i2c = FakeI2C()
        sensor = BMP280(i2c)
        i2c.writes.clear()
        sensor.measure()
        self.assertEqual(i2c.writes, [(bmp280.REG_CTRL_MEAS, bytes([bmp280.CTRL_FORCED]))])
        self.assertEqual(bmp280.CTRL_FORCED & 0x03, 0x01)  # mode bits: forced

    def test_it_waits_while_the_sensor_is_still_measuring(self):
        i2c = FakeI2C()
        i2c.measuring_polls = 5
        sensor = BMP280(i2c)
        temperature, _ = sensor.measure()
        self.assertAlmostEqual(temperature, 25.08, places=2)

    def test_a_sensor_that_never_finishes_is_an_error_not_a_hang(self):
        i2c = FakeI2C()
        sensor = BMP280(i2c)
        i2c.never_finishes = True
        started = time.monotonic()
        with self.assertRaises(OSError):
            sensor.measure()
        self.assertLess(time.monotonic() - started, 1.0)

    def test_the_old_property_names_still_work(self):
        sensor = BMP280(FakeI2C())
        self.assertAlmostEqual(sensor.temperature, 25.08, places=2)
        self.assertAlmostEqual(sensor.pressure, 100653.27, delta=0.05)

    def test_a_negative_temperature_works(self):
        sensor = BMP280(FakeI2C(raw_temperature=RAW_TEMPERATURE - 60000))
        self.assertLess(sensor.measure()[0], 25.08)


class Starting(unittest.TestCase):
    def test_the_usual_address(self):
        self.assertEqual(BMP280(FakeI2C(address=0x76)).address, 0x76)

    def test_the_other_address(self):
        self.assertEqual(BMP280(FakeI2C(address=0x77)).address, 0x77)

    def test_nothing_on_the_bus_is_an_error(self):
        i2c = FakeI2C()
        i2c.devices = [0x23, 0x3C]       # a light sensor and a display, no BMP280
        with self.assertRaises(OSError):
            BMP280(i2c)

    def test_something_else_at_the_address_is_an_error(self):
        with self.assertRaises(OSError):
            BMP280(FakeI2C(chip_id=0x55))

    def test_a_bme280_is_accepted(self):
        self.assertAlmostEqual(BMP280(FakeI2C(chip_id=0x60)).measure()[0], 25.08, places=2)

    def test_it_is_reset_and_filtered_at_start(self):
        i2c = FakeI2C()
        BMP280(i2c)
        self.assertIn((bmp280.REG_RESET, b"\xb6"), i2c.writes)
        self.assertIn((bmp280.REG_CONFIG, bytes([bmp280.CONFIG_FILTER_4])), i2c.writes)

    def test_the_address_can_be_given(self):
        i2c = FakeI2C(address=0x77)
        i2c.devices = []                 # not found by scanning, but we know where it is
        self.assertEqual(BMP280(i2c, address=0x77).address, 0x77)


if __name__ == "__main__":
    unittest.main()
