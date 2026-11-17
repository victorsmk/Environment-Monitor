# BH1750 light sensor, written so that reading it never makes the program wait.
#
# The common BH1750 libraries start a measurement and then sleep about 180 ms until it is done. In
# this program that sleep would stop the microphone from being sampled 18 % of the time. Here the
# measurement is started with trigger() and the result is picked up later with read(); the program
# does other work in between.


class BH1750:
    POWER_ON = 0x01
    ONE_TIME_HIGH_RES = 0x20  # 1 lux steps; the sensor switches itself off after measuring
    ADDRESSES = (0x23, 0x5C)  # 0x23 when the ADDR pin is low (the usual case), 0x5C when it is high

    def __init__(self, i2c, address=None):
        self.i2c = i2c
        if address is None:
            found = i2c.scan()
            for candidate in self.ADDRESSES:
                if candidate in found:
                    address = candidate
                    break
            else:
                raise OSError("BH1750 not found on the I2C bus")
        self.address = address

    def trigger(self):
        """Start one measurement. Returns at once; call read() at least 180 ms later."""
        self.i2c.writeto(self.address, bytes([self.POWER_ON]))
        self.i2c.writeto(self.address, bytes([self.ONE_TIME_HIGH_RES]))

    def read(self):
        """The light level in lux from the measurement started by trigger()."""
        data = self.i2c.readfrom(self.address, 2)
        raw = (data[0] << 8) | data[1]
        return raw / 1.2
