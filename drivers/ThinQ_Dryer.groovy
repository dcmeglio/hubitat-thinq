/**
 *  LG Dryer
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

metadata {
    definition(name: "LG ThinQ Dryer", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Initialize"

        attribute "runTime", "number"
        attribute "runTimeDisplay", "string"
        attribute "remainingTime", "number"
        attribute "remainingTimeDisplay", "string"
        attribute "currentState", "string"
        attribute "error", "string"
        attribute "course", "string"
        attribute "smartCourse", "string"
        attribute "dryLevel", "string"
        attribute "temperatureLevel", "string"
        attribute "timeDry", "string"
    }
}

import groovy.json.JsonSlurper

def uninstalled() {
    parent.stopRTIMonitoring(device)
}

def initialize() {
    if (getDataValue("master") == "true") {
        if (interfaces.mqtt.isConnected())
            interfaces.mqtt.disconnect()

        mqttConnectUntilSuccessful()
    }

    parent.registerRTIMonitoring(device)
}

def mqttConnectUntilSuccessful() {
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
		log.warn "Lost connection to MQTT, retrying in 15 seconds ${e}"
		runIn(15, "mqttConnectUntilSuccessful")
		return false
	}
}

def parse(message) {
    def topic = interfaces.mqtt.parseMessage(message)
    def payload = new JsonSlurper().parseText(topic.payload) 

    parent.processMqttMessage(this, payload)
}

def mqttClientStatus(String message) {
    log.debug "Status: " + message

    if (message.startsWith("Error:")) {
        log.error "MQTT Error: ${message}"
        try {
            interfaces.mqtt.disconnect() // Guarantee we're disconnected
        }
        catch (e) {
        }
		mqttConnectUntilSuccessful()
    }
}

def processStateData(data) {
    def runTime = 0
    def remainingTime = 0
    def currentState = data["State"] ?: ""
    def dryLevel = data["DryLevel"] ?: ""
    def temperatureLevel = data["TempControl"] ?: ""
    def error 

    remainingTime += (data["Remain_Time_H"]*60*60)
    remainingTime += (data["Remain_Time_M"]*60)

    runTime += (data["Initial_Time_H"]*60*60)
    runTime += (data["Initial_Time_M"]*60)

    sendEvent(name: "runTime", value: runTime)
    sendEvent(name: "runTimeDisplay", value: "${data["Remain_Time_H"]}:${data["Remain_Time_M"]}")
    sendEvent(name: "remainingTime", value: remainingTime)
    sendEvent(name: "remainingTimeDisplay", value: "${data["Initial_Time_H"]}:${data["Initial_Time_M"]}")
    if (currentState != null)
        sendEvent(name: "currentState", value: parent.cleanEnumValue(currentState, "@WM_STATE_"))
    sendEvent(name: "error", value: data["Error"]?.toLowerCase())
    if (data["Course"] != null)
        sendEvent(name: "course", value: data["Course"] != 0 ? data["Course"]?.toLowerCase() : "none")
    if (data["SmartCourse"] != null)
        sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")
    if (dryLevel != null)
        sendEvent(name: "dryLevel", value: parent.cleanEnumValue(dryLevel, "@WM_DRY27_DRY_LEVEL_"))
    if (temperatureLevel != null)
        sendEvent(name: "temperatureLevel", value: parent.cleanEnumValue(temperatureLevel, "@WM_DRY27_TEMP_"))
    if (data["TimeDry"] != null)
        sendEvent(name: "timeDry", value: data["TimeDry"])
}