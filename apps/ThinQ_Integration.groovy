/**
 *
 *  LG ThinQ
 *
 *  Copyright 2020 Dominick Meglio
 *
 *	If you find this useful, donations are always appreciated 
 *	https://www.paypal.com/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=7LBRPJRLJSDDN&source=url
 *
 * I'll add something more formal as I keep developing this, but big thanks to all of those involved in wideq
 * and thinq2-python as the research they did and their code as a reference has been invaluable
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
@Field static def caCertUrl = "https://www.websecurity.digicert.com/content/dam/websitesecurity/digitalassets/desktop/pdfs/roots/VeriSign-Class%203-Public-Primary-Certification-Authority-G5.pem"

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

@Field static def responseCodeText = [
	"0000": "OK",
	"0001": "PARTIAL_OK",
	"0103": "OPERATION_IN_PROGRESS_DEVICE",
	"0007": "PORTAL_INTERWORKING_ERROR",
	"0104": "PROCESSING_REFRIGERATOR",
	"0111": "RESPONSE_DELAY_DEVICE",
	"8107": "SERVICE_SERVER_ERROR",
	"8102": "SSP_ERROR",
	"9020": "TIME_OUT",
	"8104": "WRONG_XML_OR_URI",
	"9000": "AWS_IOT_ERROR",
	"8105": "AWS_S3_ERROR",
	"8106": "AWS_SQS_ERROR",
	"9002": "BASE64_DECODING_ERROR",
	"9001": "BASE64_ENCODING_ERROR",
	"8103": "CLIP_ERROR",
	"0105": "CONTROL_ERROR_REFRIGERATOR",
	"9003": "CREATE_SESSION_FAIL",
	"9004": "DB_PROCESSING_FAIL",
	"8101": "DM_ERROR",
	"0013": "DUPLICATED_ALIAS",
	"0008": "DUPLICATED_DATA",
	"0004": "DUPLICATED_LOGIN",
	"0102": "EMP_AUTHENTICATION_FAILED",
	"8900": "ETC_COMMUNICATION_ERROR",
	"9999": "ETC_ERROR",
	"0112": "EXCEEDING_LIMIT",
	"0119": "EXPIRED_CUSTOMER_NUMBER",
	"9005": "EXPIRES_SESSION_BY_WITHDRAWAL",
	"0100": "FAIL",
	"8001": "INACTIVE_API",
	"0107": "INSUFFICIENT_STORAGE_SPACE",
	"9010": "INVAILD_CSR",
	"0002": "INVALID_BODY",
	"0118": "INVALID_CUSTOMER_NUMBER",
	"0003": "INVALID_HEADER",
	"0301": "INVALID_PUSH_TOKEN",
	"0116": "INVALID_REQUEST_DATA_FOR_DIAGNOSIS",
	"0014": "MISMATCH_DEVICE_GROUP",
	"0114": "MISMATCH_LOGIN_SESSION",
	"0006": "MISMATCH_NONCE",
	"0115": "MISMATCH_REGISTRED_DEVICE",
	"9005": "MISSING_SERVER_SETTING_INFORMATION",
	"0110": "NOT_AGREED_TERMS",
	"0106": "NOT_CONNECTED_DEVICE",
	"0120": "NOT_CONTRACT_CUSTOMER_NUMBER",
	"0010": "NOT_EXIST_DATA",
	"0009": "NOT_EXIST_DEVICE",
	"0117": "NOT_EXIST_MODEL_JSON",
	"0121": "NOT_REGISTERED_SMART_CARE",
	"0012": "NOT_SUPPORTED_COMMAND",
	"8000": "NOT_SUPPORTED_COUNTRY",
	"0005": "NOT_SUPPORTED_SERVICE",
	"0109": "NO_INFORMATION_DR",
	"0108": "NO_INFORMATION_SLEEP_MODE",
	"0011": "NO_PERMISSION",
	"0113": "NO_PERMISION_MODIFY_RECIPE",
	"0101": "NO_REGISTERED_DEVICE",
	"9006": "NO_USER_INFORMATION"
]

def prefMain() {
	if (state.client_id == null)
		state.client_id = (UUID.randomUUID().toString()+UUID.randomUUID().toString()).replaceAll(/-/,"")
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
	
	if (result.startsWith("LG.OAUTH.EC")) {
		return returnErrorPage("Unable to validate OAuth Code ${result}. Click next to return to the main page and try again", "prefMain")
	}
	state.mqttServer = getMqttServer().mqttServer
	state.access_token = result.access_token
	state.refresh_token = result.refresh_token
	state.user_number = oauthDetails.user_number
	
	log.debug register()
	def certAndSubData = getCertAndSub()
	state.cert = certAndSubData.certificatePem
	state.subscriptions = certAndSubData.subscriptions

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

def returnErrorPage(message, nextPage) {
		return dynamicPage(name: "prefError", title: "Error Occurred",  nextPage: nextPage, uninstall:false, install: false) {
		section {
			paragraph message
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
		if (!getChildDevice("thinq:"+deviceDetails.id)) {
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

def getStandardHeaders() {
	return [
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
				"x-client-id": state.client_id,
				"x-language-code": "en-US",
				"Host": "aic-service.lgthinq.com:46030"
			]
}

def lgAPIGet(uri) {
	def result = null
	try
	{
		httpGet(
			[
				uri: uri,
				headers: getStandardHeaders(),
				requestContentType: "application/json"
			]
		) {
			resp ->
			result = resp.data?.result
		}
		return result
	}
	catch (Exception e) {
		def data = e.getResponse().data
		if (data != null) {
			log.error "Error calling ${uri}: " + responseCodeText[data.resultCode]
		}
	}
}

def getCountries() {
	return lgAPIGet("https://aic-service.lgthinq.com:46030/v1/service/application/country-language")
}

def getGatewayDetails() {
	return lgAPIGet(gatewayUrl)
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

def register() {
	def result
	def headers = getStandardHeaders()
	headers << ["x-emp-token": state.access_token]
	headers << ["x-user-no": state.user_number]
	httpPost(
		[
			uri: "https://route.lgthinq.com:46030/v1/service/users/client",
			headers: headers,
			requestContentType: "application/json"
			
		]
	) {
		resp ->
		result = resp.data?.result
	}
	return result
}

def getCertAndSub() {
	def result
	def headers = getStandardHeaders()
	headers << ["x-emp-token": state.access_token]
	headers << ["x-user-no": state.user_number]
	httpPost(
		[
			uri: "https://route.lgthinq.com:46030/v1/service/users/client/certificate",
			headers: headers,
			requestContentType: "application/json",
			body: [
				csr: csr
			]
			
		]
	) {
		resp ->
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
	try
	{
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
	}
	catch (Exception e) {
		def data = e.getResponse().data
		if (data != null) {
			log.error "OAuth error: ${data.error.code}: ${data.error.message}"
			return data.error.code
		}
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
				"x-client-id": state.client_id,
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

def retrieveMqttDetails() {
	def caCert = ""
	httpGet([
		uri: caCertUrl,
		textParser: true
	]) {
		resp ->
			caCert = resp.data.text

	}
	return [server: state.mqttServer, subscriptions: state.subscriptions, certificate: state.cert, privateKey: privateKey, caCertificate: caCert, clientId: state.client_id]
}

def logDebug(msg) {
	log.debug msg
}