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

    sendEvent(name: "frontRightState", value: data["RFState"].replaceAll(/^OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "frontLeftState", value: data["LFState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "rearRightState", value: data["RRState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "rearLeftState", value: data["LRState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "centerState", value: data["CenterState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    sendEvent(name: "ovenState", value: data["UpperOvenState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    if (data["LowerOvenState"] != "NOT_DEFINE_VALUE") {
        sendEvent(name: "lowerOvenState", value: data["LowerOvenState"].replaceAll(/^@OV_STATE_/,"").replaceAll(/_W$/,"").replaceAll(/_/," ").toLowerCase())
    }
}