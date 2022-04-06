# ftduinoBlue.py
#  
# This is a Micropython implementation of ftDuinoBlue.
#
# For more info see http://ftduino.de/blue  
#

from hm10 import HM10

class ftduinoBlue(HM10):
    def __init__(self, layout, name="ESP32"):
        super().__init__(name)
        self.layout = layout
        self.rx_buffer = b""

    def rx(self, data):
        # all data is buffered binary since multibyte characters may come in two chunks
        self.rx_buffer = self.rx_buffer + data

        # check if we have a complete line in buffer
        if b"\n" in self.rx_buffer:
            cmd, self.rx_buffer = self.rx_buffer.split(b"\n", 1)
            self.handle_line(cmd.strip())

    def handle_line(self, line):
        # verify checksum if present
        if ":" in line:
            cmd, csum = line.rsplit(":", 1)
            csum = int(csum.decode("utf-8"), 16)

            # calculate and check cmd checksum
            if self.checksum(cmd) == csum:
                self.handle_cmd(cmd.decode("utf-8"))
        else:
            self.handle_cmd(line.decode("utf-8"))

    def checksum(self, data):
        c = 0
        for i in data: c = (c + i) & 0xff
        return c       

    def handle_cmd(self, cmd):
        # the command name itself is everything before the first whitespace
        cmd_name = cmd.split()[0]

        if cmd_name == "VERSION":
            self.send_reply("VERSION 1.0.0")
        elif cmd_name == "LAYOUT":
            self.send_reply("LAYOUT " + self.layout)
        elif cmd_name == "STATE":
            self.state()
        elif cmd_name == "SWITCH" or cmd_name == "SLIDER" or cmd_name == "BUTTON":
            id,state = cmd.split()[1:3]
            if cmd_name == "SLIDER": state = int(state)
            self.event(cmd_name, int(id), state)
        elif cmd_name == "JOYSTICK":
            id,x,y = cmd.split()[1:4]
            self.event(cmd_name, int(id), int(x), int(y))
        else:
            print("unexpected command:", cmd_name)

    def state(self):
        pass

    def event(self, dev, id, *args):
        pass

    def send(self, dev, id, *args):
        # assemble reply
        reply = dev.strip() + " " + str(id)
        for a in args: reply += " " + str(a)
        self.send_reply(reply)

    def send_reply(self, reply):
        # encode into byte array and append checksum
        reply = reply.encode("utf-8")
        reply += b":" + hex(self.checksum(reply))[-2:].encode("utf-8") + b"\n"
        self.tx(reply)