/**
 *  LG Oven
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

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
    def isFahrenheit = data["MonTempUnit"] == 0

    def frontRight = parent.cleanEnumValue(frontRightState, "@OV_STATE_")
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
    sendEvent(name: "frontRightState", value: frontRight)
    sendEvent(name: "frontLeftState", value: frontLeft)
    sendEvent(name: "rearLeftState", value: rearLeft)
    sendEvent(name: "rearRightState", value: rearRight)
    sendEvent(name: "centerState", value: center)
   
    sendEvent(name: "ovenState", value: data["UpperOvenState"]?.replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase()) ?: "power off"
    if (data["LowerOvenState"] != "NOT_DEFINE_VALUE") {
        sendEvent(name: "lowerOvenState", value: data["LowerOvenState"]?.replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase() ?: "power off")
    }
}