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
    }
}

import groovy.json.JsonSlurper

def initialize() {
    if (interfaces.mqtt.isConnected())
		interfaces.mqtt.disconnect()

    mqttConnectUntilSuccessful()
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
    def payload =  new JsonSlurper().parseText(topic.payload) 

    log.debug "parse: " + payload
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

