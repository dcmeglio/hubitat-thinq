/**
 *  LG Fridge
 *
 *  Copyright 2020 Dominick Meglio
 *
 */

metadata {
    definition(name: "LG ThinQ Fridge", namespace: "dcm.thinq", author: "dmeglio@gmail.com") {
        capability "Sensor"
        capability "Initialize"
    }
}

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
    log.debug "parse: " + message
}

def mqttClientStatus(String message) {
    log.debug "Status: " + message
}