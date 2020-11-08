/**
 *  LG Fridge
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "LG ThinQ Fridge", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Initialize"
        capability "ContactSensor"

        attribute "fridgeTemp", "number"
        attribute "freezerTemp", "number"
        attribute "craftIceMode", "number"
        attribute "icePlus", "string"
    }

    preferences {
      section { // General
        input name: "logLevel", title: "Log Level", type: "enum", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
        input name: "logDescText", title: "Log Description Text", type: "bool", defaultValue: false, required: false
      }
    }
}

def uninstalled() {
    logger("debug", "uninstalled()")
    parent.stopRTIMonitoring(device)
}

def initialize() {
    logger("debug", "initialize()")
    if (getDataValue("master") == "true") {
        if (interfaces.mqtt.isConnected())
            interfaces.mqtt.disconnect()

        mqttConnectUntilSuccessful()
    }

    parent.registerRTIMonitoring(device)
}

def mqttConnectUntilSuccessful() {
  logger("debug", "mqttConnectUntilSuccessful()")

  try {
    def mqtt = parent.retrieveMqttDetails()

    interfaces.mqtt.connect(mqtt.server,
                            mqtt.clientId,
                            null,
                            null,
                            tlsVersion: "1.2",
                            privateKey: mqtt.privateKey,
                            caCertificate: mqtt.caCertificate,
                            clientCertificate: mqtt.certificate)
    pauseExecution(3000)
    for (sub in mqtt.subscriptions) {
        interfaces.mqtt.subscribe(sub, 0)
    }
    return true
  }
  catch (e)
  {
    logger("warn", "Lost connection to MQTT, retrying in 15 seconds ${e}")
    runIn(15, "mqttConnectUntilSuccessful")
    return false
  }
}

def parse(message) {
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload)
    logger("debug", "parse(${payload})")

    parent.processMqttMessage(this, payload)
}

def mqttClientStatus(String message) {
    logger("debug", "mqttClientStatus(${message})")

    if (message.startsWith("Error:")) {
        logger("error", "MQTT Error: ${message}")

        try {
            interfaces.mqtt.disconnect() // Guarantee we're disconnected
        }
        catch (e) {
        }
        mqttConnectUntilSuccessful()
    }
}

def processStateData(data) {
    logger("debug", "processStateData(${data})")

    if (data.atLeastOneDoorOpen == "OPEN")
      sendEvent(name: "contact", value: "open")
    else if (data.atLeastOneDoorOpen == "CLOSE")
      sendEvent(name: "contact", value: "closed")

    if (data.fridgeTemp != null) {
      def temp = data.fridgeTemp
      if (getTemperatureScale() == "C")
        temp = fahrenheitToCelsius(data.fridgeTemp)
      sendEvent(name: "fridgeTemp", value: temp)
    }

    def freezerTemps = [5,4,3,2,1,0,-1,-2,-3,-4,-5,-6,-7]
    def fridgeTemps = [43,42,41,40,39,38,37,36,35,34,33]
    if (data.freezerTemp != null) {
      def temp = freezerTemps[data.freezerTemp-1]
      if (getTemperatureScale() == "C")
        temp = fahrenheitToCelsius(temp)
      sendEvent(name: "freezerTemp", value: temp)
    }
    if (data.fridgeTemp != null) {
      def temp = fridgeTemps[data.fridgeTemp-1]
      if (getTemperatureScale() == "C")
        temp = fahrenheitToCelsius(temp)
      sendEvent(name: "fridgeTemp", value: temp)
    }

    if (data.craftIceMode) {
      if (data.craftIceMode == "@RE_TERM_CRAFT_6B_W")
        sendEvent(name: "craftIceMode", value: 6)
      else
        sendEvent(name: "craftIceMode", value: 3)
    }

    if (data.expressMode) {
      sendEvent(name: "icePlus", value: parent.cleanEnumValue(data.expressMode, "@CP_"))
    }
}

/**
* @param level Level to log at, see LOG_LEVELS for options
* @param msg Message to log
*/
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx < 0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx <= setLevelIdx) {
      log."${level}" "${device.displayName} ${msg}"
    }
  }
}
