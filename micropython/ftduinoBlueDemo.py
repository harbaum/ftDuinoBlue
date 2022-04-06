# ftDuinoBlueDemo.py
#  
# This is a demo sketch for ftDuinoBlue. It allows you to create a custom
# remote android user interface for your project right inside you arduino
# sketch. No android programming required.
#  
# For more info see http://ftduino.de/blue  
#

import time
from ftduinoBlue import ftduinoBlue
from machine import Timer, PWM

class ftduinoBlueDemo(ftduinoBlue):
    LAYOUT = (
        "<layout orientation='portrait' name='ftDuinoBlue Micropython'>" +
          "<switch id='1' size='20' width='parent' place='hcenter;top'>LED on/off</switch>" +
          "<label id='2' size='20' place='left;below:1'>LED brightness</label>" +
          "<slider id='3' width='parent' max='255' place='hcenter;below:2'/>" +
          "<label id='4' size='20' place='left;below:3'>Blink speed</label>" +
          "<slider id='5' width='parent' place='hcenter;below:4'/>"
        "</layout>" )

    def __init__(self):
        super().__init__(ftduinoBlueDemo.LAYOUT)

        # LED is on pin 2
        self.pwm = PWM(Pin(2)) 
        self.pwm.freq(200)
        self.set_brightness(128)

        self.tim = Timer(0)
        self.set_speed(50)

        self.tick_state = False

        self.set_on(True)

    def set_speed(self, value):
        self.speed = value
        self.tim.init(period=50+10*(100-value), mode=Timer.PERIODIC, callback=self.tick)

    def set_brightness(self, value):
        self.brightness = value
        self.pwm.duty(4 * value)  # 0..255 -> 0..1020

    def set_on(self, value):
        self.on = value

    def state(self):
        # app requests state to update its view
        self.send("SWITCH", 1, "ON" if self.on else "OFF")
        self.send("SLIDER", 3, self.brightness)
        self.send("SLIDER", 5, self.speed)

    def event(self, element, id, *args):
        # app sends gui events
        if element == "SLIDER" and id == 3:
            self.set_brightness(args[0])
        elif element == "SLIDER" and id == 5:
            self.set_speed(args[0])
        elif element == "SWITCH" and id == 1:
            self.set_on(args[0] == "ON")
        else:
            print("unknown event:", element, id, args)

    def tick(self, timer):
        self.tick_state = not self.tick_state
        if self.tick_state and self.on:
            self.pwm.duty(4 * self.brightness)
        else:
            self.pwm.duty(0)

if __name__ == "__main__":
    m = ftduinoBlueDemo()

    while True:
        time.sleep_ms(100)
