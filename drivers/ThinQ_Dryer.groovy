/**
 *  LG Dryer
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "LG ThinQ Dryer", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Switch"
        capability "Initialize"

        attribute "runTime", "number"
        attribute "runTimeDisplay", "string"
        attribute "remainingTime", "number"
        attribute "remainingTimeDisplay", "string"
        attribute "finishTimeDisplay", "string"
        attribute "currentState", "string"
        attribute "currentState_", "string"
        attribute "error", "string"
        attribute "course", "string"
        attribute "smartCourse", "string"
        attribute "dryLevel", "string"
        attribute "temperatureLevel", "string"
        attribute "timeDry", "string"
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

    def runTime = 0
    def runTimeDisplay = '00:00'
    def remainingTime = 0
    def remainingTimeDisplay = '00:00'
    def delayTime = 0
    def delayTimeDisplay = '00:00'
    def error

    if (parent.checkValue(data,'Initial_Time_H')) {
      runTime += (data["Initial_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Initial_Time_M')) {
      runTime += (data["Initial_Time_M"]*60)
    }
    runTimeDisplay = parent.convertSecondsToTime(runTime)

    if (parent.checkValue(data,'Remain_Time_H')) {
      remainingTime += (data["Remain_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Remain_Time_M')) {
      remainingTime += (data["Remain_Time_M"]*60)
    }
    remainingTimeDisplay = parent.convertSecondsToTime(remainingTime)

    Date currentTime = new Date()
    use(groovy.time.TimeCategory) {
      currentTime = currentTime + (remainingTime as int).seconds
    }
    def finishTimeDisplay = currentTime.format("yyyy-MM-dd'T'HH:mm:ssZ", location.timeZone)

    if (parent.checkValue(data,'Reserve_Time_H')) {
      delayTime += (data["Reserve_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Reserve_Time_M')) {
      delayTime += (data["Reserve_Time_M"]*60)
    }
    delayTimeDisplay = parent.convertSecondsToTime(delayTime)

    if (parent.checkValue(data,'State')) {
      String currentStateName = parent.cleanEnumValue(data["State"], "@WM_STATE_")
      if (device.currentValue("currentState") != currentStateName) {
        if(logDescText) {
          log.info "${device.displayName} CurrentState: ${currentStateName}"
        } else {
          logger("info", "CurrentState: ${currentStateName}")
        }
          parent.notificationCheck(device.displayName, currentStateName )
      }
      sendEvent(name: "currentState", value: currentStateName)
      sendEvent(name: "currentState_", value: titleCase(currentStateName))

      def currentStateSwitch = (currentStateName =~ /power off/ ? 'off' : 'on')
      if (device.currentValue("switch") != currentStateSwitch) {
        if(logDescText) {
            log.info "${device.displayName} Was turned ${currentStateSwitch}"
        } else {
          logger("info", "Was turned ${currentStateSwitch}")
        }
      }
      sendEvent(name: "switch", value: currentStateSwitch, descriptionText: "Was turned ${currentStateSwitch}")
    }

    sendEvent(name: "runTime", value: runTime, unit: "seconds")
    sendEvent(name: "runTimeDisplay", value: runTimeDisplay, unit: "hh:mm")
    sendEvent(name: "remainingTime", value: remainingTime, unit: "seconds")
    sendEvent(name: "remainingTimeDisplay", value: remainingTimeDisplay, unit: "hh:mm")
    sendEvent(name: "delayTime", value: delayTime, unit: "seconds")
    sendEvent(name: "delayTimeDisplay", value: delayTimeDisplay, unit: "hh:mm")
    sendEvent(name: "finishTimeDisplay", value: finishTimeDisplay, unit: "hh:mm")

    if (parent.checkValue(data,'Error')) {
      sendEvent(name: "error", value: data["Error"].toLowerCase())
    }

    if (parent.checkValue(data,'Course'))
        sendEvent(name: "course", value: data["Course"] != 0 ? data["Course"]?.toLowerCase() : "none")
    if (parent.checkValue(data,'SmartCourse'))
        sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")
    if (parent.checkValue(data,'DryLevel'))
        sendEvent(name: "dryLevel", value: parent.cleanEnumValue(data["DryLevel"], "@WM_DRY27_DRY_LEVEL_"))
    if (parent.checkValue(data,'TempControl'))
        sendEvent(name: "temperatureLevel", value: parent.cleanEnumValue(data["TempControl"], "@WM_DRY27_TEMP_"))
    if (parent.checkValue(data,'TimeDry'))
        sendEvent(name: "timeDry", value: data["TimeDry"])
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

def titleCase(str) {
    str.split("\\s+").collect { it.toLowerCase().capitalize() }.join(" ")
}
