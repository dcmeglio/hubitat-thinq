/**
 *  LG Dishwasher
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

metadata {
    definition(name: "LG ThinQ Dishwasher", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
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
    def delayTime = 0
    def currentState = data["State"] ?: ""
    def course = data["Course"]
    def error 

    if (parent.checkValue(data,'Remain_Time_H')) {
      remainingTime += (data["Remain_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Remain_Time_M')) {
      remainingTime += (data["Remain_Time_M"]*60)
    }

    if (parent.checkValue(data,'Initial_Time_H')) {
      runTime += (data["Initial_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Initial_Time_M')) {
      runTime += (data["Initial_Time_M"]*60)
    }

    if (parent.checkValue(data,'Reserve_Time_H')) {
      delayTime += (data["Reserve_Time_H"]*60*60)
    }
    if (parent.checkValue(data,'Reserve_Time_M')) {
      delayTime += (data["Reserve_Time_M"]*60)
    }

    if (currentState == "-")
        currentState = "power off"

    sendEvent(name: "runTime", value: runTime)
    sendEvent(name: "runTimeDisplay", value: parent.checkValue(data,'Remain_Time_H') ? "${data["Remain_Time_H"]}:${data["Remain_Time_M"]}" : "${data["Remain_Time_M"]}")
    sendEvent(name: "remainingTime", value: remainingTime)
    sendEvent(name: "remainingTimeDisplay", value: parent.checkValue(data,'Initial_Time_H') ? "${data["Initial_Time_H"]}:${data["Initial_Time_M"]}" : "${data["Initial_Time_M"]}")
    sendEvent(name: "delayTime", value: delayTime)
    sendEvent(name: "delayTimeDisplay", value: parent.checkValue(data,'Reserve_Time_H') ? "${data["Reserve_Time_H"]}:${data["Reserve_Time_M"]}" : "${data["Reserve_Time_M"]}")
    if (currentState != null)
        sendEvent(name: "currentState", value: parent.cleanEnumValue(currentState, "@DW_STATE_"))
    if (parent.checkValue(data,'Error')) {
      sendEvent(name: "error", value: data["Error"].toLowerCase())
    }
    // There is a typo in the API, fix it
    if (course == "Haeavy")
        course = "heavy"
    if (course != null)
        sendEvent(name: "course", value: course != 0 ? course?.toLowerCase() : "none")
    if (parent.checkValue(data,'SmartCourse'))
        sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")
}