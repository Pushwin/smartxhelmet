#include <Wire.h>
#include <Adafruit_MPU6050.h>
#include <Adafruit_Sensor.h>
#include <BluetoothSerial.h>
#include <HardwareSerial.h>
#include <EEPROM.h>

Adafruit_MPU6050 mpu;
BluetoothSerial SerialBT;
HardwareSerial sim800(1); // UART1 for SIM800L

const int buzzerPin = 25;   // Buzzer pin
const int buttonPin = 26;   // Push button pin

bool accidentDetected = false;

String contactName = "None";
String contactPhone = "";
String contactEmail = "None";

// EEPROM layout
#define EEPROM_SIZE 256
#define EEPROM_ADDR 0

void setup() {
  Serial.begin(115200);
  SerialBT.begin("SmartHelmetESP32");
  sim800.begin(9600, SERIAL_8N1, 16, 17); // RX = 16, TX = 17
  pinMode(buzzerPin, OUTPUT);
  pinMode(buttonPin, INPUT_PULLUP); // Internal pull-up for push button

  EEPROM.begin(EEPROM_SIZE);
  loadContactFromEEPROM();

  if (!mpu.begin()) {
    Serial.println("❌ MPU6050 not found!");
    while (1);
  }

  Serial.println("✅ MPU6050 ready");

  delay(2000);
  sendAT("AT");
  sendAT("AT+CMGF=1"); // Set SMS text mode
}

void loop() {
  // Bluetooth: Receive contact
  if (SerialBT.available()) {
    String received = SerialBT.readStringUntil('\n');
    received.trim();
    Serial.println("📩 Received: " + received);

    if (received.startsWith("SAVE,")) {
      parseAndSaveContact(received);
      saveContactToEEPROM();
      SerialBT.println("OK");

    } else if (received == "GET_CONTACT") {
      String response = "SAVED," + contactName + "," + contactPhone + "," + contactEmail;
      SerialBT.println(response);
    }
  }

  // Accident detection
  sensors_event_t a, g, temp;
  mpu.getEvent(&a, &g, &temp);

  float totalAccel = sqrt(a.acceleration.x * a.acceleration.x +
                          a.acceleration.y * a.acceleration.y +
                          a.acceleration.z * a.acceleration.z);

  if (totalAccel > 25 && !accidentDetected) {
    accidentDetected = true;
    Serial.println("🚨 Accident detected!");
    
    digitalWrite(buzzerPin, HIGH); // Buzzer ON
    delay(3000);
    digitalWrite(buzzerPin, LOW);  // Buzzer OFF

    Serial.println("⏳ Waiting 15 seconds...");
    bool canceled = false;

    for (int i = 15; i > 0; i--) {
      Serial.println(i);
      if (digitalRead(buttonPin) == LOW) { // Button pressed
        Serial.println("❌ Call canceled by user!");
        canceled = true;
        break;
      }
      delay(1000);
    }

    if (!canceled) {
      if (contactPhone != "") {
        makeCall(contactPhone); // 📞 Call FIRST
        delay(2000);
        sendSMS(contactPhone, "🚨Hai thamma Uta atha"); // 📩 Then SMS
      } else {
        Serial.println("⚠ No contact saved. Cannot send SMS or call.");
      }
    }

    accidentDetected = false;
  }

  delay(300);
}

void parseAndSaveContact(String data) {
  int first = data.indexOf(',');
  int second = data.indexOf(',', first + 1);
  int third = data.indexOf(',', second + 1);

  contactName = data.substring(first + 1, second);
  contactPhone = data.substring(second + 1, third);
  contactEmail = data.substring(third + 1);

  Serial.println("📥 Contact saved:");
  Serial.println("👤 " + contactName);
  Serial.println("📞 " + contactPhone);
  Serial.println("📧 " + contactEmail);
}

void saveContactToEEPROM() {
  String full = contactName + "," + contactPhone + "," + contactEmail;
  int len = full.length();
  for (int i = 0; i < len; i++) {
    EEPROM.write(EEPROM_ADDR + i, full[i]);
  }
  EEPROM.write(EEPROM_ADDR + len, '\0'); // Null-terminate
  EEPROM.commit();
  Serial.println("💾 Contact saved to EEPROM");
}

void loadContactFromEEPROM() {
  char buf[EEPROM_SIZE];
  for (int i = 0; i < EEPROM_SIZE; i++) {
    buf[i] = EEPROM.read(EEPROM_ADDR + i);
    if (buf[i] == '\0') break;
  }

  String saved = String(buf);
  int first = saved.indexOf(',');
  int second = saved.indexOf(',', first + 1);

  if (first > 0 && second > first) {
    contactName = saved.substring(0, first);
    contactPhone = saved.substring(first + 1, second);
    contactEmail = saved.substring(second + 1);
    Serial.println("📂 Loaded contact from EEPROM:");
    Serial.println("👤 " + contactName);
    Serial.println("📞 " + contactPhone);
    Serial.println("📧 " + contactEmail);
  } else {
    Serial.println("⚠ No contact found in EEPROM");
  }
}

void sendAT(String cmd) {
  sim800.println(cmd);
  delay(500);
  while (sim800.available()) {
    Serial.write(sim800.read());
  }
  Serial.println();
}

void makeCall(String number) {
  Serial.println("📞 Calling...");
  sim800.print("ATD");
  sim800.print(number);
  sim800.println(";");
  delay(10000); // Ring for 10 seconds
  sim800.println("ATH"); // Hang up
  delay(1000);
  Serial.println("📴 Call Ended");
}

void sendSMS(String number, String message) {
  sim800.println("AT+CMGF=1"); // Text mode
  delay(500);
  sim800.print("AT+CMGS=\"");
  sim800.print(number);
  sim800.println("\"");
  delay(1000);
  sim800.print(message);
  sim800.write(26); // Ctrl+Z to send
  delay(3000);
  Serial.println("📤 SMS Sent");
}