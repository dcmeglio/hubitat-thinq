/**
 *  LG Oven
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "LG ThinQ Oven", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Initialize"

        attribute "frontRightState", "string"
        attribute "frontLeftState", "string"
        attribute "rearRightState", "string"
        attribute "rearLeftState", "string"
        attribute "centerState", "string"
        attribute "ovenState", "string"
        attribute "lowerOvenState", "string"
        attribute "ovenTemperature", "number"
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
                            clientCertificate: mqtt.certificate,
                            ignoreSSLIssues: true)
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
    logger("trace", "parse(${payload})")

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

    def isFahrenheit = data["MonTempUnit"] == 0

    def rightFrontState = data["RFState"]
    def frontLeftState = data["LFState"]
    def rearLeftState = data["LRState"]
    def rearRightState = data["RRState"]
    def centerState = data["CenterState"]
    def upperOvenState = parent.cleanEnumValue(data["UpperOvenState"], "@OV_STATE_")
    def lowerOvenState = parent.cleanEnumValue(data["LowerOvenState"], "@OV_STATE_")

    def frontRight = parent.cleanEnumValue(rightFrontState, "@OV_STATE_")
    if (frontRight == "initial")
        frontRight = "power off"
    def frontLeft = parent.cleanEnumValue(frontLeftState, "@OV_STATE_")
    if (frontLeft == "initial")
        frontLeft = "power off"
    def rearLeft = parent.cleanEnumValue(rearLeftState, "@OV_STATE_")
    if (rearLeft == "initial")
        rearLeft = "power off"
    def rearRight = parent.cleanEnumValue(rearRightState, "@OV_STATE_")
    if (rearRight == "initial")
        rearRight = "power off"
    def center = parent.cleanEnumValue(centerState, "@OV_STATE_")
    if (center == "initial")
        center = "power off"

    if (upperOvenState == "initial")
        upperOvenState = "power off"
    if (lowerOvenState == "initial")
        lowerOvenState = "power off"

    if (frontRight != null && frontRight != "")
        sendEvent(name: "frontRightState", value: frontRight)
    if (frontLeft != null && frontLeft != "")
        sendEvent(name: "frontLeftState", value: frontLeft)
    if (rearLeft != null && rearLeft != "")
        sendEvent(name: "rearLeftState", value: rearLeft)
    if (rearRight != null && rearRight != "")
        sendEvent(name: "rearRightState", value: rearRight)
    if (center != null && center != "")
        sendEvent(name: "centerState", value: center)

    if (upperOvenState != null)
        sendEvent(name: "ovenState", value: upperOvenState ?: "power off")

    sendEvent(name: "ovenTemperature", value: "")
    // The API has a typo in it that causes this weird value
    if (lowerOvenState != "NOT_DEFINE_VALUE" && lowerOvenState != "NOT_DEFINE_VALUE value:7" && lowerOvenState != null) {
        sendEvent(name: "lowerOvenState", value: lowerOvenState ?: "power off")
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
