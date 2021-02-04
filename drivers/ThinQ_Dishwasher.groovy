/**
 *  LG Dishwasher
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

import groovy.transform.Field
import groovy.json.JsonSlurper

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]

metadata {
    definition(name: "LG ThinQ Dishwasher", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Switch"
        capability "ContactSensor"
        capability "Initialize"

        attribute "runTime", "number"
        attribute "runTimeDisplay", "string"
        attribute "remainingTime", "number"
        attribute "remainingTimeDisplay", "string"
        attribute "delayTime", "number"
        attribute "delayTimeDisplay", "string"
        attribute "finishTimeDisplay", "string"
        attribute "currentState", "string"
        attribute "error", "string"
        attribute "course", "string"
        attribute "smartCourse", "string"
        attribute "steam", "string"
        attribute "highTemp", "string"
        attribute "extraDry", "string"
        attribute "halfLoad", "string"
        attribute "dualZone", "string"
        attribute "nightDry", "string"
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

    if (data.Door == "@CP_ON_EN_W")
      sendEvent(name: "contact", value: "open")
    else if (data.Door == "@CP_OFF_EN_W")
      sendEvent(name: "contact", value: "closed")

    if (parent.checkValue(data,'Initial_Time_H')) {
      runTime += (data["Initial_Time_H"]*60*60)
      updateDataValue("initialHours", data["Initial_Time_H"].toString())
    }
    else {
      runTime += (getDataValue("initialHours") ?: "0").toInteger()*60*60
    }
    if (parent.checkValue(data,'Initial_Time_M')) {
      runTime += (data["Initial_Time_M"]*60)
    }
    runTimeDisplay = parent.convertSecondsToTime(runTime)

    if (parent.checkValue(data,'Remain_Time_H')) {
      remainingTime += (data["Remain_Time_H"]*60*60)
      updateDataValue("remainHours", data["Remain_Time_H"].toString())
    }
    else {
      remainingTime += (getDataValue("remainHours") ?: "0").toInteger()*60*60
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
      updateDataValue("reserveHours", data["Reserve_Time_H"].toString())
    }
    else {
      delayTime += (getDataValue("reserveHours") ?: "0").toInteger()*60*60
    }
    if (parent.checkValue(data,'Reserve_Time_M')) {
      delayTime += (data["Reserve_Time_M"]*60)
    }
    delayTimeDisplay = parent.convertSecondsToTime(delayTime)

    if (parent.checkValue(data,'State')) {
        String currentStateName = parent.cleanEnumValue(data["State"], "@DW_STATE_")
        if (device.currentValue("currentState") != currentStateName) {
          if(logDescText) {
            log.info "${device.displayName} CurrentState: ${currentStateName}"
          } else {
            logger("info", "CurrentState: ${currentStateName}")
          }
        }
        sendEvent(name: "currentState", value: currentStateName)

        def currentStateSwitch = (currentStateName =~ /power off|-/ ? 'off' : 'on')
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

    if (parent.checkValue(data,'Course')) {
        def course = data["Course"]
        if (course == "Haeavy") { course = "heavy" } // Fix typo in the API
        sendEvent(name: "course", value: course != 0 ? course?.toLowerCase() : "none")
    }
    if (parent.checkValue(data,'SmartCourse'))
        sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")

    if (parent.checkValue(data,'Steam')) 
      sendEvent(name: "steam", value:  parent.cleanEnumValue(data["Steam"], "@CP_"))
    if (parent.checkValue(data,'HighTemp')) 
      sendEvent(name: "highTemp", value:  parent.cleanEnumValue(data["HighTemp"], "@CP_"))
    if (parent.checkValue(data,'ExtraDry')) 
      sendEvent(name: "extraDry", value:  parent.cleanEnumValue(data["ExtraDry"], "@CP_"))
    if (parent.checkValue(data,'HalfLoad')) 
      sendEvent(name: "halfLoad", value:  parent.cleanEnumValue(parent.cleanEnumValue(data["ExtraDry"], "@CP_"),"@DW_OPTION_"))
    if (parent.checkValue(data,'DualZone')) 
      sendEvent(name: "dualZone", value:  parent.cleanEnumValue(data["DualZone"], "@CP_"))          
    if (parent.checkValue(data,'NightDry')) 
      sendEvent(name: "nightDry", value:  parent.cleanEnumValue(data["NightDry"], "@CP_"))   
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
