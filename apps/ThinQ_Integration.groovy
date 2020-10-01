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
	page(name: "prefCert")
	page(name: "prefDevices")
}

@Field static def gatewayUrl = "https://route.lgthinq.com:46030/v1/service/application/gateway-uri"

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

def prefMain() {
	def countries = getCountries()

	def countriesList = [:]
	countries.each { countriesList << ["${it.langCode}": it.description]}

	if (region != null) {
		state.langCode = region
		state.countryCode = countries.find { it.langCode == region}?.countryCode
		def apiGatewayResult = getGatewayDetails()
		log.debug apiGatewayResult
		state.oauthUrl = apiGatewayResult.oauthUri
		state.empUrl = apiGatewayResult.empUri
		state.thinqUrl = apiGatewayResult.thinq2Uri
		state.thinq1Url = apiGatewayResult.thinq1Uri
		state.empSpxUri = apiGatewayResult.empSpxUri
		state.rtiUri = apiGatewayResult.rtiUri

	}

	return dynamicPage(name: "prefMain", title: "LG ThinQ OAuth", nextPage: "prefCert", uninstall:false, install: false) {
		section {	
			input "region", "enum", title: "Select your region", options: countriesList, required: true, submitOnChange: true
			if (state.countryCode != null && state.langCode != null) {
				def desc = ""
				if (!state.authToken) {
					desc = "To continue you will need to connect your LG ThinQ and Hubitat accounts"
				}
				else {
					desc = "Your Hubitat and LG ThinQ accounts are connected"
				}
				paragraph "When you click the link below a popup will open to allow you to login to your LG account. After you login, the popup will go blank. At that point, copy the URL from that popup, close the popup and wait for this screen to reload. At that point, paste the URL into the box below and click Next."
				href url: oauthInitialize(), style: "external", required: true, title: "LG ThinQ Account Authorization", description: desc
				input "url", "text", title: "Enter the URL you are redirected to after logging in"
			}
		}
	}
}

def prefCert() {
	return dynamicPage(name: "prefCert", title: "LG ThinQ OAuth", nextPage: "prefDevices", uninstall:false, install: false) {
		section {
			paragraph "The LG ThinQ server uses certificate based authentication. You will need to create an RSA 2048bit private key and a CSR for the certificate. In PKCS#1 PEM format. If you know how to create certificates yourself, you can feel free. If not there is a web tool <a href=\"https://certificatetools.com/\" target=\"_blank\">https://certificatetools.com/</a> which you can use to generate the key and CSR. Once you have them paste them below."
			input "privateKey", "textarea", title: "Private Key", required: true
			input "csr", "textarea", title: "CSR", required: true
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
			def child = addChildDevice("dcm.thinq", driverName, "thinq:" + deviceDetails.id, 1234, ["name": deviceDetails.name,isComponent: false])
			if (!findMasterDevice()) {
				child.updateDataValue("master", "true")
				child.initialize()
			}
			else if (child.getDataValue("master") != "true")
				child.updateDataValue("master", "false")
		}
	}
}

def getCountries() {
	def result
	httpGet(
		[
			uri: "https://aic-service.lgthinq.com:46030/v1/service/application/country-language",
			headers: [
				"Accept": "application/json",
				"x-thinq-app-os": "IOS",
				"x-thinq-app-ver": "3.5.0000",
				"x-thinq-app-level": "PRD",
				"x-country-code": "US",
				"x-message-id": "wideq",
				"x-api-key": "VGhpblEyLjAgU0VSVklDRQ==",
				"x-thinq-app-type": "NUTS",
				"x-service-code": "SVC202",
				"x-service-phase": "OP",
				"x-client-id": "LGAO221A02",
				"x-language-code": "en-US",
				"Host": "aic-service.lgthinq.com:46030"
			],
			requestContentType: "application/json"
		]
	) {
		resp ->
		result = resp.data?.result
	}
	return result
}

def getGatewayDetails() {
	def result
	httpGet(
		[
			uri: gatewayUrl,
			headers: [
				"Accept": "application/json",
				"x-thinq-app-os": "IOS",
				"x-thinq-app-ver": "3.5.0000",
				"x-thinq-app-level": "PRD",
				"x-country-code": "US",
				"x-message-id": "wideq",
				"x-api-key": "VGhpblEyLjAgU0VSVklDRQ==",
				"x-thinq-app-type": "NUTS",
				"x-service-code": "SVC202",
				"x-service-phase": "OP",
				"x-client-id": "LGAO221A02",
				"x-language-code": "en-US",
				"Host": "aic-service.lgthinq.com:46030"
			],
			requestContentType: "application/json"
		]
	) {
		resp ->
		result = resp.data?.result
	}
	return result
}


def getMqttServer() {
	def result
	httpGet(
		[
			uri: "https://common.lgthinq.com",
			path: "/route",
			headers: [
				"x-country-code": state.countryCode,
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
		//		"Host": "kic.lgthinq.com:46030",
		//		"x-thinq-application-key": "wideq",
		//		"x-thinq-security-key": "nuts_securitykey",
		//		"Accept": "application/json"
				"Accept": "application/json",
				"x-thinq-app-os": "IOS",
				"x-thinq-app-ver": "3.5.0000",
				"x-thinq-app-level": "PRD",
				"x-country-code": "US",
				"x-message-id": "wideq",
				"x-api-key": "VGhpblEyLjAgU0VSVklDRQ==",
				"x-thinq-app-type": "NUTS",
				"x-service-code": "SVC202",
				"x-service-phase": "OP",
				"x-client-id": "LGAO221A02",
				"x-language-code": "en-US",
				"Host": "aic-service.lgthinq.com:46030"
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
	return "${state.empUrl}/spx/login/signIn?country=${state.countryCode}&language=${state.langCode}&svc_list=SVC202&client_id=LGAO221A02&division=ha&&state=xxx&show_thirdparty_login=GGL,AMZ,FBK&redirect_uri=${URLEncoder.encode("https://kr.m.lgaccount.com/login/iabClose")}"
}

def getDevices() {
	def data = lgEdmPost(state.thinq1Url + "/member/login", [
		countryCode: state.countryCode,
        langCode: state.langCode,
        loginType: "EMP",
        token: state.access_token
	])
	if (data) {
		def devices = data.item
		
		
		return devices.findAll { d -> supportedDeviceTypes.find { supported -> supported == d.deviceType } }
	}
}

def findMasterDevice() {
    return getChildDevices().find { 
        it.hasCapability("Initialize") && it.getDataValue("master") == "true"
    }
}

def logDebug(msg) {
	log.debug msg
}