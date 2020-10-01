/**
 *
 *  LG ThinQ
 *
 *  Copyright 2020 Dominick Meglio
 *
 *	If you find this useful, donations are always appreciated 
 *	https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url
 */

 import groovy.transform.Field
 import java.text.SimpleDateFormat
 
definition(
    name: "ThinQ Integration",
    namespace: "dcm.thinq",
    author: "Dominick Meglio",
    description: "Integrate LG ThinQ smart devices with Hubitat.",
    category: "My Apps",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
	documentationLink: "https://github.com/dcmeglio/hubitat-vacationmanager/blob/master/README.md")

preferences {
    page(name: "prefMain")
	page(name: "prefDevices")
}

@Field static def gatewayUrl = "https://kic.lgthinq.com:46030/api/common/gatewayUriList"

@Field static def supportedDeviceTypes = [
	101, // Fridge
	201, // Washer
	202, // Dryer
	204, // Dishwasher
	301 // Oven
]

@Field static def deviceTypeConstants = [
	Fridge: 101,
	Washer: 201,
	Dryer: 202,
	Dishwasher: 204,
	Oven: 301
]

@Field static def countryCode = "US"
@Field static def languageCode = "en-US"

def prefMain() {
	def apiGatewayResult = lgEdmPost(gatewayUrl, [countryCode: countryCode, langCode: languageCode])
	log.debug apiGatewayResult
	state.oauthUrl = apiGatewayResult.oauthUri
	state.empUrl = apiGatewayResult.empUri
	state.thinqUrl = apiGatewayResult.thinqUri

	return dynamicPage(name: "prefMain", title: "LG ThinQ OAuth", nextPage: "prefDevices", uninstall:false, install: false) {
		section {	
			def desc = ""
			if (!state.authToken) {
				desc = "To continue you will need to connect your LG ThinQ and Hubitat accounts"
			}
			else {
				desc = "Your Hubitat and LG ThinQ accounts are connected"
			}
			href url: oauthInitialize(), style: "external", required: true, title: "LG ThinQ Account Authorization", description: desc
			input "url", "text", title: "Enter the URL you are redirected to after logging in"
		}
	}
}

def prefDevices() {
	def oauthDetails = getOAuthDetailsFromUrl()
	log.debug oauthDetails
	def result = getAccessToken(oauthDetails)
	log.debug result
	state.mqttServer = getMqttServer().mqttServer
	state.access_token = result.access_token
	state.refresh_token = result.refresh_token
	state.user_number = oauthDetails.user_number

	def devices = getDevices()
	def deviceList = [:]
	state.foundDevices = []
	devices.each { 
		deviceList << ["${it.deviceId}":it.alias] 
		state.foundDevices << [id: it.deviceId, name: it.alias, type: it.deviceType]
	}

	
	return dynamicPage(name: "prefDevices", title: "LG ThinQ OAuth",  uninstall:false, install: true) {
		section {
			input "thinqDevices", "enum", title: "Devices", required: true, options: deviceList, multiple: true
		}
	}
}

def installed() {
	initialize()
}

def updated() {
	initialize()
}

def initialize() {
	for (d in thinqDevices) {
		def deviceDetails = state.foundDevices.find { it.id == d }
		def driverName = ""
		switch (deviceDetails.type) {
			case deviceTypeConstants.Dryer:
				driverName = "LG ThinQ Dryer"
				break
			case deviceTypeConstants.Washer:
				driverName = "LG ThinQ Washer"
				break
			case deviceTypeConstants.Fridge:
				driverName = "LG ThinQ Fridge"
				break
			case deviceTypeConstants.Oven:
				driverName = "LG ThinQ Oven"
				break
			case deviceTypeConstants.Dishwasher:
				driverName = "LG ThinQ Dishwasher"
				break
		}
		if (!getChildDevice(deviceDetails.id)) {
			addChildDevice("dcm.thinq", driverName, "thinq:" + deviceDetails.id, 1234, ["name": deviceDetails.name,isComponent: false])
		}
	}
}

def getMqttServer() {
	def result
	httpGet(
		[
			uri: "https://common.lgthinq.com",
			path: "/route",
			headers: [
				"x-country-code": countryCode,
				"x-service-phase": "OP"
			]
		]
	) { resp ->
		result = resp.data?.result
	}
	return result
}

def getOAuthDetailsFromUrl() {
	def queryStr = url.split(/\?/)[1]
	def queryParams = queryStr.split('&')
	def result = [code: "", url: "", user_number: ""]
	for (param in queryParams) {
		def kvp = param.split('=')
		if (kvp[0] == "code")
			result.code = kvp[1]
		else if (kvp[0] == "oauth2_backend_url")
			result.url = URLDecoder.decode(kvp[1])
		else if (kvp[0] == "user_number")
			result.user_number = kvp[1]
	}
	return result
}

def getTimestamp() {
    def date = new Date()
    return date.format("EEE, dd MMM yyyy HH:mm:ss '+0000'", TimeZone.getTimeZone('UTC'))
}

def getAccessToken(oauthdetails) {
	def result
	
	httpPost([
		uri: oauthdetails.url[0..-2],
		path: "/oauth/1.0/oauth2/token",
		headers: [
			"Accept": "application/json",
			"x-lge-appkey": "LGAO221A02",
			"x-lge-oauth-date": getTimestamp()
		],
		body: [
			code: oauthdetails.code,
        	grant_type: "authorization_code",
        	redirect_uri: "https://kr.m.lgaccount.com/login/iabClose"
		]
	]) { resp ->
		result = resp.data
	}
	return result
}

def lgEdmPost(url, body) {
	def data

		httpPost([
			uri: url,
			headers: [
				"Host": "kic.lgthinq.com:46030",
				"x-thinq-application-key": "wideq",
				"x-thinq-security-key": "nuts_securitykey",
				"Accept": "application/json"
			],
			requestContentType: "application/json",
			body: [
				lgedmRoot: body
			],
		]) { resp -> 
			data = resp.data?.lgedmRoot
		}
	
	return data
}


def oauthInitialize() {
	return "${state.empUrl}/spx/login/signIn?country=${countryCode}&language=${languageCode}&svc_list=SVC202&client_id=LGAO221A02&division=ha&&state=xxx&show_thirdparty_login=GGL,AMZ,FBK&redirect_uri=${URLEncoder.encode("https://kr.m.lgaccount.com/login/iabClose")}"
}

def getDevices() {
	def data = lgEdmPost(state.thinqUrl + "/member/login", [
		countryCode: countryCode,
        langCode: languageCode,
        loginType: "EMP",
        token: state.access_token
	])
	if (data) {
		def devices = data.item
		
		
		return devices.findAll { d -> supportedDeviceTypes.find { supported -> supported == d.deviceType } }
	}
}

def logDebug(msg) {
    if (parent.getDebugLogging()) {
		log.debug msg
	}
}