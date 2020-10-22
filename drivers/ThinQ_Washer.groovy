/**
 *  LG Washer
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "LG ThinQ Washer", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Switch"
        capability "Initialize"

        attribute "runTime", "number"
        attribute "runTimeDisplay", "string"
        attribute "remainingTime", "number"
        attribute "remainingTimeDisplay", "string"
        attribute "delayTime", "number"
        attribute "delayTimeDisplay", "string"
        attribute "currentState", "string"
        attribute "error", "string"
        attribute "course", "string"
        attribute "smartCourse", "string"
        attribute "soilLevel", "string"
        attribute "spinSpeed", "string"
        attribute "temperatureLevel", "string"
        attribute "doorLock", "string"
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

    def runTime = 0
    def remainingTime = 0
    def delayTime = 0
    def error
    def waterTemp = data["WaterTemp"] ?: ""

    if (checkValue(data,'Remain_Time_H')) {
      remainingTime += (data["Remain_Time_H"]*60*60)
    }
    if (checkValue(data,'Remain_Time_M')) {
      remainingTime += (data["Remain_Time_M"]*60)
    } else {
      remainingTime = 0
    }

    if (checkValue(data,'Initial_Time_H')) {
      runTime += (data["Initial_Time_H"]*60*60)
    }
    if (checkValue(data,'Initial_Time_M')) {
      runTime += (data["Initial_Time_M"]*60)
    } else {
      runTime = 0
    }

    if (checkValue(data,'Reserve_Time_H')) {
      delayTime += (data["Reserve_Time_H"]*60*60)
    }
    if (checkValue(data,'Reserve_Time_M')) {
      delayTime += (data["Reserve_Time_M"]*60)
    } else {
      delayTime = 0
    }


    if (checkValue(data,'State')) {
        String currentStateName = parent.cleanEnumValue(data["State"], "@WM_STATE_")
        sendEvent(name: "currentState", value: currentStateName)
        if (currentStateName =~ /power off/ ) {
            sendEvent(name: "switch", value: 'off')
        } else {
            sendEvent(name: "switch", value: 'on')
        }
    }

    sendEvent(name: "runTime", value: runTime)
    sendEvent(name: "runTimeDisplay", value: checkValue(data,'Remain_Time_H') ? "${data["Remain_Time_H"]}:${data["Remain_Time_M"]}" : "${data["Remain_Time_M"]}")
    sendEvent(name: "remainingTime", value: remainingTime)
    sendEvent(name: "remainingTimeDisplay", value: checkValue(data,'Initial_Time_H') ? "${data["Initial_Time_H"]}:${data["Initial_Time_M"]}" : "${data["Initial_Time_M"]}")
    sendEvent(name: "delayTime", value: delayTime)
    sendEvent(name: "delayTimeDisplay", value: checkValue(data,'Reserve_Time_H') ? "${data["Reserve_Time_H"]}:${data["Reserve_Time_M"]}" : "${data["Reserve_Time_M"]}")

    if (checkValue(data,'Error')) {
      sendEvent(name: "error", value: data["Error"].toLowerCase())
    }

    if (checkValue(data,'APCourse'))
        sendEvent(name: "course", value: data["APCourse"] != 0 ? data["APCourse"]?.toLowerCase() : "none")
    if (checkValue(data,'SmartCourse'))
        sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")
    if (checkValue(data,'Soil'))
        sendEvent(name: "soilLevel", value: parent.cleanEnumValue(data["Soil"], "@WM_MX_OPTION_SOIL_"))
    if (checkValue(data,'SpinSpeed'))
        sendEvent(name: "spinSpeed", value: parent.cleanEnumValue(data["SpinSpeed"], "@WM_MX_OPTION_SPIN_"))
    if (checkValue(data,'TempControl'))
        sendEvent(name: "temperatureLevel", value: parent.cleanEnumValue(data["TempControl"], "@WM_MX_OPTION_TEMP_"))
    if (checkValue(data,'doorLock'))
        sendEvent(name: "doorLock", value: parent.cleanEnumValue(data["doorLock"], "@DOOR_LOCK_"))
}

// check is map has an actuale value
private checkValue(data, String k) {
  if (data?.containsKey(k)) {
    if (data[k] != null && data[k] != '' && data[k] != 'null') {
      return true
    } else { return false }
  } else { return false }
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