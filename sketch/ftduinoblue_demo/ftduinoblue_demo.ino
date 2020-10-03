/*
 *  ftduinoblue_demo.ino
 *  
 *  This is a demo sketch for ftDuinoBlue. It allows you to create a custom
 *  remote android user interface for your project right inside you arduino
 *  sketch. No android programming required.
 *  
 *  For more info see http://ftduino.de/blue  
 */

#include <avr/pgmspace.h>
#include "ftduinoblue.h"

#if defined(__AVR_ATmega328P__)  // Arduino UNO, NANO
// auto select UNO setup if the atmega328p is found
#define USE_UNO_SU   // soft uart on arduino uno (HM10 on: RX D10, TX D09, GND D11, VCC D12)
#else
// This sketch supports different hardware setups. Choose you one below:
#define USE_I2C_BT   // ftDuino with ftduino i2c bluetooth adapter 
// #define USE_SERIAL1  // ftDuino as explained at https://harbaum.github.io/ftduino/www/manual/experimente.html#6.19.2
#endif

#ifdef USE_I2C_BT
// use i2c bluetooth adapter
#include "I2cSerialBt.h"
I2cSerialBt btSerial;
#else 
#ifdef USE_UNO_SU
#include <SoftwareSerial.h>
SoftwareSerial btSerial(10, 9);
#else
#ifdef USE_SERIAL1
#define btSerial Serial1   // default = Serial1 of e.g. Leonardo or Pro Micro
#else
#error "Please specify your setup!"
#endif
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
#endif

  // register callback for ftduinoblue
  ftdblue.setCallback(ftduinoblue_callback);
  
  btSerial.begin(9600);
  
// optionally set the bluetooth name.
//    delay(10);
//    btSerial.println("AT+NAMEftDuinoBlue");
//    delay(10);

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


// the i2c_bluetooth adapter has a on-board led which can
// be used to show that the communication is fine.
void adapter_led() {
#ifdef USE_I2C_BT
  // flash led on adapter
  static uint32_t led_tick = 0;
  static bool led_state = false;
  if((!led_state && (millis() - led_tick) > 100) ||
     ( led_state && (millis() - led_tick) > 900)) {
    btSerial.led(led_state);
    led_tick = millis();
    led_state = !led_state;
  }  
#endif
}

void ftduinoblue_callback(struct FtduinoBlue::reply *r) {    
  switch(r->type) {
    case FtduinoBlue::STATE:
      Serial.println("STATE");
      
      ftdblue.print("SWITCH 1 ");
      ftdblue.println(ledState?"ON":"OFF");
      ftdblue.print("SLIDER 3 ");
      ftdblue.println(ledBrightness);
      ftdblue.print("SLIDER 5 ");
      ftdblue.println(ledBlinkSpeed);    
      break;

    case FtduinoBlue::BUTTON:
      Serial.print("BUTTON ");
      Serial.print(r->id, DEC);
      Serial.print(" ");
      Serial.println(r->state?"DOWN":"UP");
      break;

    case FtduinoBlue::SWITCH:
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

    case FtduinoBlue::SLIDER:
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
      
    case FtduinoBlue::JOYSTICK:
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
  adapter_led();

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
  }

  ftdblue.handle();
};
