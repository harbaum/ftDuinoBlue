/*
 *  ftduinoblue_demo.ino
 *  
 *  This is a demo sketch for ftDuinoBlue. It allows you to create a custom
 *  remote android user interface for your project right inside you arduino
 *  sketch. No android programming required.
 *  
 *  For more info see http://ftduino.de/blue  
 */

/*
 * This sketch works on two quite different setups: 
 * 
 * a) A Arduino Uni with the HM-10 bluetooth le module plugged right into
 *    D8 to D13 of the Arduino. An optional led plus 1k resistor between
 *    D3 and GND is helpful.
 *    
 *    This setup is automatically selected during compile time if the 
 *    board is set to Arduino Uno
 *    
 * b) A ftDuino (Arduino designed for use with fischertechnik) with the
 *    ftDuino bluetooth LE module. An optional lamp or led connected
 *    between output O1 and GND is helpful.
 *    
 *    This setup is automatically selected during compile time if the
 *    board is set to ftDuino.
 */

#include <Wire.h>
#include <avr/pgmspace.h>
#include "ftduinoblue.h"

#if defined(__AVR_ATmega328P__)  // Arduino UNO, NANO
#define USE_UNO_SU   // soft uart on arduino uno (HM10 on: RX D10, TX D09, GND D11, VCC D12)
#else
#define USE_I2C_BT   // ftDuino with ftduino i2c bluetooth adapter 
#endif

#ifdef USE_I2C_BT
// use i2c bluetooth adapter
#include "I2cSerialBt.h"
I2cSerialBt btSerial;
#include <Ftduino.h>
#define FTDUINO
#else 
#ifdef USE_UNO_SU
#include <SoftwareSerial.h>
SoftwareSerial btSerial(10, 9);
#define LED_PIN 3
#endif
#endif

// the layout to be sent to the ftDuinoBlue app
const char layout[] PROGMEM = 
    "<layout orientation='portrait' name='ftDuinoBlue Led Demo'>"
    "<switch id='1' size='20' width='parent' place='hcenter;top'>LED on/off</switch>"
    "<label id='2' size='20' place='left;below:1'>LED brightness</label>"
    "<slider id='3' width='parent' max='255' place='hcenter;below:2'/>"
    "<label id='4' size='20' place='left;below:3'>Blink speed</label>"
    "<slider id='5' width='parent' place='hcenter;below:4'/>"
//    "<button id='6' width='parent' place='hcenter;below:5'>BUTTON</button>"
//    "<joystick id='7' width='parent' place='hcenter;below:6' />"
    "</layout>";

FtduinoBlue ftdblue(btSerial, layout);

void setup() {
  Serial.begin(9600);     // some debug output is done on Serial (USB)

#ifdef USE_UNO_SU
  // power up bt module on uno
  pinMode(11, OUTPUT);  digitalWrite(11, LOW);
  pinMode(12, OUTPUT);  digitalWrite(12, HIGH);

#ifdef LED_PIN
  // optional extra LED
  pinMode(LED_PIN, OUTPUT);  digitalWrite(LED_PIN, HIGH);
#endif
#endif

#ifdef FTDUINO
  ftduino.init();
#endif

  // register callback for ftduinoblue
  ftdblue.setCallback(ftduinoblue_callback);

#ifdef USE_I2C_BT
  // wait max 1 sec for adapter to appear on bus. This is not
  // needed as begin() will wait for the device. But this way
  // we can use the led as an inidictaor for problems with 
  // the i2c uart adapater
  if(!btSerial.check(1000)) {
    // fast blink with led on failure
    pinMode(LED_BUILTIN, OUTPUT); 
    while(true) {
      digitalWrite(LED_BUILTIN, HIGH);
      delay(100);
      digitalWrite(LED_BUILTIN, LOW);
      delay(100);
    }
  }
#endif

  // initialize i2c uart to 9600 baud
  btSerial.begin(9600);

  // prepare led
  pinMode(LED_BUILTIN, OUTPUT);
  digitalWrite(LED_BUILTIN, LOW);   
};

// led config. This has a startup default and can be changed
// by the user via bluetooth. This is also reported back to
// the user app to provide the correct initial values should
// the user reconnect with the app
static char ledChanged = true;
static char ledState = true;         // LED is on
static uint8_t ledBlinkSpeed = 50;   // ~ 2 Hz
static uint8_t ledBrightness = 128;  // 50% brightness

void ftduinoblue_callback(struct FtduinoBlue::reply *r) {    
  switch(r->type) {
    case FtduinoBlue::FTDB_STATE:
      Serial.println("STATE");
      
      ftdblue.print("SWITCH 1 ");
      ftdblue.println(ledState?"ON":"OFF");
      ftdblue.print("SLIDER 3 ");
      ftdblue.println(ledBrightness);
      ftdblue.print("SLIDER 5 ");
      ftdblue.println(ledBlinkSpeed);    
      break;

    case FtduinoBlue::FTDB_BUTTON:
      Serial.print("BUTTON ");
      Serial.print(r->id, DEC);
      Serial.print(" ");
      Serial.println(r->state?"DOWN":"UP");
      break;

    case FtduinoBlue::FTDB_SWITCH:
      Serial.print("SWITCH ");
      Serial.print(r->id, DEC);
      Serial.print(" ");
      Serial.println(r->state?"ON":"OFF");

      // make sure the led reacts on switch 1
      if(r->id == 1) {
        ledChanged = true;
        ledState = r->state;       
      }
      break;

    case FtduinoBlue::FTDB_SLIDER:
      Serial.print("SLIDER ");
      Serial.print(r->id, DEC);
      Serial.print(" ");
      Serial.println(r->slider, DEC);
      
      if(r->id == 3) {
        ledBrightness = r->slider;
        ledChanged = true;   // user has changed something -> led needs to be updated
      }
      if(r->id == 5) {
        ledBlinkSpeed = r->slider;
        ledChanged = true;   // user has changed something -> led needs to be updated
      }
      break;
      
    case FtduinoBlue::FTDB_JOYSTICK:
      Serial.print("JOYSTICK ");
      Serial.print(r->id, DEC);
      Serial.print(" ");
      Serial.print(r->joystick.x, DEC);
      Serial.print(" ");
      Serial.println(r->joystick.y, DEC);
      break;

    default:
      Serial.print("UNKNOWN ");
      Serial.println(r->type, DEC);
      break;
  }
}

void loop() {

  // led blink timer
  static uint32_t led_tick = 0;
  static bool led_state = false;

  // led is updated on timer or by user change
  if(ledChanged || (millis() - led_tick) > 50+10*(100-ledBlinkSpeed)) {
    // check if led update was triggered by user
    if(!ledChanged) {
      // no. So it was the timer -> handle timer
      led_state = !led_state;
      led_tick = millis();
    } else
      ledChanged = false;

    // update state of physical led. Set analog brightness if
    // a) led is enabled by user and b) led is actually in the on state
    // of the blinking. Switch it off otherwise
    if(led_state && ledState) analogWrite(LED_BUILTIN, ledBrightness);
    else                      digitalWrite(LED_BUILTIN, LOW);      
#ifdef FTDUINO
    if(led_state && ledState) ftduino.output_set(Ftduino::O1, Ftduino::HI, ledBrightness/4);
    else                      ftduino.output_set(Ftduino::O1, Ftduino::OFF, 0);
#endif
#ifdef LED_PIN
    if(led_state && ledState) analogWrite(LED_PIN, ledBrightness);
    else                      digitalWrite(LED_PIN, LOW);
#endif
  }

  ftdblue.handle();
};
