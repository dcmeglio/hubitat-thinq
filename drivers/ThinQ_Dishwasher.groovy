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
		log.warn "Lost connection to MQTT, retrying in 15 seconds"
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
    def currentState = data["Process"] ?: ""
    def course = data["Course"]
    def error 

    remainingTime += (data["Remain_Time_H"]*60*60)
    remainingTime += (data["Remain_Time_M"]*60)

    runTime += (data["Initial_Time_H"]*60*60)
    runTime += (data["Initial_Time_M"]*60)

    delayTime += (data["Reserve_Time_H"]*60*60)
    delayTime += (data["Reserve_Time_M"]*60)

    if (currentState == "-")
        currentState = "power off"

    sendEvent(name: "runTime", value: runTime)
    sendEvent(name: "runTimeDisplay", value: "${data["Remain_Time_H"]}:${data["Remain_Time_M"]}")
    sendEvent(name: "remainingTime", value: remainingTime)
    sendEvent(name: "remainingTimeDisplay", value: "${data["Initial_Time_H"]}:${data["Initial_Time_M"]}")
    sendEvent(name: "delayTime", value: delayTime)
    sendEvent(name: "delayTimeDisplay", value: "${data["Reserve_Time_H"]}:${data["Reserve_Time_M"]}")
    sendEvent(name: "currentState", value: currentState.replaceAll(/^@WM_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "error", value: data["Error"]?.toLowerCase())
    // There is a typo in the API, fix it
    if (course == "Haeavy")
        course = "heavy"
    sendEvent(name: "course", value: course != 0 ? course?.toLowerCase() : "none")
    sendEvent(name: "smartCourse", value: data["SmartCourse"] != 0 ? data["SmartCourse"]?.toLowerCase() : "none")
}