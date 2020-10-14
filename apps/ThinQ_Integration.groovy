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

@Field List<String> LOG_LEVELS = ["error", "warn", "info", "debug", "trace"]
@Field String DEFAULT_LOG_LEVEL = LOG_LEVELS[2]
@Field int AUTH_RETRY_MAX = 3

definition(
  name: "ThinQ Integration",
  namespace: "dcm.thinq",
  author: "Dominick Meglio",
  description: "Integrate LG ThinQ smart devices with Hubitat.",
  category: "My Apps",
  iconUrl: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience.png",
  iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Convenience/Cat-Convenience@2x.png",
  documentationLink: "https://github.com/dcmeglio/hubitat-thinq")

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
    state.auth_retry_cnt = 0
  def countries = getCountries()

  if (countries == null)
    return returnErrorPage("Unable to connect to LG ThinQ at this time. Please try again later. Check the app logs for more details.", null)

  def countriesList = [:]
  countries.each { countriesList << ["${it.langCode}": it.description]}

  if (region != null) {
    state.langCode = region
    state.countryCode = countries.find { it.langCode == region}?.countryCode
    def apiGatewayResult = getGatewayDetails()
    if (apiGatewayResult == null)
      return returnErrorPage("Unable to connect to LG ThinQ at this time. Please try again later. Check the app logs for more details.", null)

    def mqttResult = getMqttServer()
    if (mqttResult == null)
      return returnErrorPage("Unable to connect to LG ThinQ at this time. Please try again later. Check the app logs for more details.", null)

    state.oauthUrl = apiGatewayResult.oauthUri
    state.empUrl = apiGatewayResult.empUri
    state.thinqUrl = apiGatewayResult.thinq2Uri
    state.thinq1Url = apiGatewayResult.thinq1Uri
    state.empSpxUri = apiGatewayResult.empSpxUri
    state.rtiUri = apiGatewayResult.rtiUri
    state.mqttServer = mqttResult.mqttServer
  }

  return dynamicPage(name: "prefMain", title: "LG ThinQ OAuth", nextPage: "prefCert", uninstall:false, install: false) {
    section {
      state.prevUrl = url
      input "logLevel", "enum", title: "Log Level", options: LOG_LEVELS, defaultValue: DEFAULT_LOG_LEVEL, required: false
      input "region", "enum", title: "Select your region", options: countriesList, required: true, submitOnChange: true
      if (state.countryCode != null && state.langCode != null) {
        def desc = ""
        if (!state.access_token) {
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
  if (url != state.prevUrl) {
    def oauthDetails = getOAuthDetailsFromUrl()
    state.oauth_url = oauthDetails.url[0..-2]
    def result = getAccessToken([code: oauthDetails.code, grant_type: "authorization_code", redirect_uri: "https://kr.m.lgaccount.com/login/iabClose"])

    if (result.toString().startsWith("LG.OAUTH.EC")) {
      return returnErrorPage("Unable to validate OAuth Code ${result}. Click next to return to the main page and try again", "prefMain")
    }
    state.user_number = oauthDetails.user_number
    state.access_token = result.access_token
    state.refresh_token = result.refresh_token
  }

  register()
  if (loginv1() == null)
    return returnErrorPage("Unable to login to LG ThinQ. Please check the logs for more details, verify your credentials, and follow the steps on the first screen and try again.", "prefMain")

  def certAndSubData = getCertAndSub()
  if (certAndSubData == null)
    return returnErrorPage("Unable to generate a certificate. Please check the logs for more details, verify your credentials, and follow the steps on the first screen and try again.", "prefMain")
  state.cert = certAndSubData.certificatePem
  state.subscriptions = certAndSubData.subscriptions

  def devices = getDevices()
  if (devices == null)
    return returnErrorPage("Unable to retrieve your devices. Please check the logs for more details, verify your credentials, and follow the steps on the first screen and try again.", "prefMain")

  def deviceList = [:]
  state.foundDevices = []
  devices.each {
    log.debug "MODEL JSON -- PLEASE POST IN THREAD ${it.modelJsonUri}"
    deviceList << ["${it.deviceId}":it.alias]
    state.foundDevices << [id: it.deviceId, name: it.alias, type: it.deviceType, version: it.platformType, modelJson: getModelJson(it.modelJsonUri)]
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
  logger("debug", "installed()")
  initialize()
}

def updated() {
  logger("debug", "updated()")
  unschedule()
  initialize()
}

def uninstalled() {
  logger("debug", "uninstalled()")
  unschedule()
  for (d in getChildDevices())
  {
    deleteChildDevice(d.deviceNetworkId)
  }
}

def initialize() {
  logger("debug", "initialize()")
  def hasV1Device = false
  cleanupChildDevices()
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
    if (!hasV1Device)
      hasV1Device = deviceDetails.version == "thinq1"
    if (!getChildDevice("thinq:"+deviceDetails.id)) {
      def child = addChildDevice("dcm.thinq", driverName, "thinq:" + deviceDetails.id, 1234, ["name": deviceDetails.name,isComponent: false])
      if (!findMasterDevice() && deviceDetails.version == "thinq2") {
        child.updateDataValue("master", "true")
        child.initialize()
      }
      else if (child.getDataValue("master") != "true") {
        child.updateDataValue("master", "false")
        child.initialize()
      }
    }
  }

  if (hasV1Device)
    schedule("0 */1 * * * ? *", refreshV1Devices)
}

def getStandardHeaders() {
  def headers = [
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
  if (state.access_token != null)
    headers << ["x-emp-token": state.access_token]
  if (state.user_number != null)
    headers << ["x-user-no": state.user_number]

  logger("debug", "getStandardHeaders() - headers: ${headers}")
  return headers
}

def getModelJson(url) {
  logger("debug", "getModelJson(${url})")

  def result = null
  try
  {
    httpGet(
      [
        uri: url
      ]
    ) {
      resp ->
      result = resp?.data
    }
    logger("trace", "getModelJson(${url}) - ${result}")
  }
  catch (Exception e) {
    logger("error", "getModelJson(${url}) - retrieving model json: ${e}")
  }
  return result
}

def lgAPIGet(uri) {
  logger("debug", "lgAPIGet(${uri})")

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
      if (resp.data.resultCode == "0000")
        result = resp.data?.result
      else {
        logger("error", "lgAPIGet(${uri}) - ${responseCodeText[resp.data.resultCode]}")
      }
    }
    logger("trace", "lgAPIGet(${uri}) - ${result}")
    return result
  }
  catch (Exception e) {
    def data = e?.getResponse()?.data
    if (data != null) {
      if (responseCodeText[data.resultCode] == "EMP_AUTHENTICATION_FAILED") {
        def refreshResult = getAccessToken([refresh_token: state.refresh_token, grant_type: "refresh_token"])
        if (refreshResult.toString().startsWith("LG.OAUTH.EC")) {
          state.access_token = null
          logger("error", "lgAPIGet(${uri}) - Refresh token failed ${refreshResult}")
        }
        else {
          state.auth_retry_cnt++
          state.access_token = refreshResult.access_token
          if (refreshResult.refresh_token)
            state.refresh_token = refreshResult.refresh_token
          if (state.access_token != null & state.auth_retry_cnt < AUTH_RETRY_MAX)
            return lgAPIGet(uri)
        }
      }
      else {
        logger("error", "lgAPIGet(${uri}) - ${responseCodeText[data.resultCode]}")
      }
    }
  }
}

def lgAPIPost(uri, body) {
  logger("debug", "lgAPIPost(${uri}, ${body})")
  def result = null
  def headers = getStandardHeaders()

  try
  {
    httpPost(
      [
        uri: uri,
        headers: headers,
        requestContentType: "application/json",
        body: body
      ]
    ) {
      resp ->
        if (resp?.data?.resultCode == "0000")
          result = resp.data?.result
        else
          logger("error", "lgAPIPost(${uri}, ${body}) - ${responseCodeText[resp.data.resultCode]}")
    }

    logger("trace", "lgAPIPost(${uri}, ${body}) - ${result}")
    return result
  }
  catch (Exception e) {
    def data = e?.getResponse()?.data
    logger("error", "lgAPIPost(${uri}, ${body}) - ${e} - ${data}")

    if (data != null) {
      if (responseCodeText[data.resultCode] == "EMP_AUTHENTICATION_FAILED") {
        def refreshResult = getAccessToken([refresh_token: state.refresh_token, grant_type: "refresh_token"])
        if (refreshResult.toString().startsWith("LG.OAUTH.EC")) {
          state.access_token = null
          logger("error", "Refresh token failed ${refreshResult}")
        }
        else
        {
          state.auth_retry_cnt++
          state.access_token = refreshResult.access_token
          if (refreshResult.refresh_token)
            state.refresh_token = refreshResult.refresh_token
          if (state.access_token != null & state.auth_retry_cnt < AUTH_RETRY_MAX)
            return lgAPIPost(uri, body)
        }
      }
      else
      {
        logger("error", "lgAPIPost(${uri}, ${body}) - ${responseCodeText[data.resultCode]}")
      }
    }
  }
}

def getCountries() {
  logger("debug", "getCountries()")
  return lgAPIGet("https://aic-service.lgthinq.com:46030/v1/service/application/country-language")
}

def getGatewayDetails() {
  logger("debug", "getGatewayDetails()")
  return lgAPIGet(gatewayUrl)
}

def getMqttServer() {
  logger("debug", "getMqttServer()")
  def result = null
  try
  {
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
      if (resp.data.resultCode == "0000")
        result = resp.data?.result
      else
        logger("error", "getMqttServer() - retrieving MQTT: ${responseCodeText[resp.data.resultCode]}")
    }
  }
  catch (Exception e) {
    def data = e?.getResponse()?.data
    if (data != null) {
      logger("error", "getMqttServer() - retrieving MQTT: ${responseCodeText[data.resultCode]}")
    }
  }
  logger("trace", "getMqttServer() - ${result}")
  return result
}

def register() {
  logger("debug", "register()")
  return lgAPIPost("https://route.lgthinq.com:46030/v1/service/users/client", null)
}

def getCertAndSub() {
  logger("debug", "getCertAndSub()")
  return lgAPIPost("https://route.lgthinq.com:46030/v1/service/users/client/certificate", [csr: csr])
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
  logger("debug", "getOAuthDetailsFromUrl() - ${result}")
  return result
}

def getTimestamp() {
    def date = new Date()
    return date.format("EEE, dd MMM yyyy HH:mm:ss '+0000'", TimeZone.getTimeZone('UTC'))
}

def getAccessToken(body) {
  logger("debug", "getAccessToken(${body})")
  def result
  try
  {
    httpPost([
      uri: state.oauth_url,
      path: "/oauth/1.0/oauth2/token",
      headers: [
        "Accept": "application/json",
        "x-lge-appkey": "LGAO221A02",
        "x-lge-oauth-date": getTimestamp()
      ],
      body: body
    ]) { resp ->
      result = resp.data
      logger("trace", "getAccessToken(${body}) - ${result}")
    }
  }
  catch (Exception e) {
    def data = e?.getResponse()?.data
    if (data != null) {
      logger("error", "getAccessToken(${body}) - OAuth error: ${data.error.code}: ${data.error.message}")
      return data.error.code
    }
  }

  return result
}

def loginv1() {
  logger("debug", "loginv1()")
  def data = lgEdmPost(state.thinq1Url + "/member/login", [
    countryCode: state.countryCode,
    langCode: state.langCode,
    loginType: "EMP",
    token: state.access_token
  ])
  if (data) {
    logger("trace", "loginv1() - ${data}")
    state.jsession = data.jsessionId
    return data
  }
}

def lgEdmPost(url, body, refresh = true) {
  logger("debug", "lgEdmPost(${url}, ${body}, ${refresh})")

  def result
  def headers = [
      "x-thinq-application-key": "wideq",
      "x-thinq-security-key": "nuts_securitykey",
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
  if (state.access_token)
    headers << ["x-thinq-token": state.access_token]
  if (state.jsession)
    headers << ["x-thinq-jsessionId": state.jsession]

  try {
    httpPost([
      uri: url,
      headers: headers,
      requestContentType: "application/json",
      body: [
        lgedmRoot: body
      ],
    ]) { resp ->
      logger("trace", "lgEdmPost(${url}) - ${resp?.data}")

      if (resp.data?.lgedmRoot.returnCd == "0000")
        result = resp.data?.lgedmRoot
      else if (responseCodeText[resp.data.lgedmRoot.returnCd] == "EMP_AUTHENTICATION_FAILED") {
        def refreshResult = getAccessToken([refresh_token: state.refresh_token, grant_type: "refresh_token"])
        if (refreshResult.toString().startsWith("LG.OAUTH.EC")) {
          state.access_token = null
          logger("error", "lgEdmPost(${url}, ${body}, ${refresh}) - Refresh token failed ${refreshResult}")
        }
        else {
          state.auth_retry_cnt++
          state.access_token = refreshResult.access_token
          if (refreshResult.refresh_token)
            state.refresh_token = refreshResult.refresh_token

          loginv1()
          if (state.access_token != null && refresh & state.auth_retry_cnt < AUTH_RETRY_MAX)
            return lgEdmPost(url, body, false)
        }
      }
      else {
        logger("error", "lgEdmPost(${url}, ${body}, ${refresh}) - ${responseCodeText[resp.data?.lgedmRoot.returnCd]}")
      }
    }
  }
  catch (Exception e) {
    def data = e?.getResponse()?.data
    if (data != null) {
        if (responseCodeText[data.lgedmRoot.returnCd] == "EMP_AUTHENTICATION_FAILED") {
        def refreshResult = getAccessToken([refresh_token: state.refresh_token, grant_type: "refresh_token"])
        if (refreshResult.toString().startsWith("LG.OAUTH.EC")) {
          state.access_token = null
          logger("error", "lgEdmPost(${url}, ${body}, ${refresh}) - Refresh token failed ${refreshResult}")
        }
        else {
          state.auth_retry_cnt++
          state.access_token = refreshResult.access_token
          if (refreshResult.refresh_token)
            state.refresh_token = refreshResult.refresh_token
          if (state.access_token != null & state.auth_retry_cnt < AUTH_RETRY_MAX)
            return lgEdmPost(url, body)
        }
      }
      else {
        logger("error", "lgEdmPost(${url}, ${body}, ${refresh}) - ${responseCodeText[data.lgedmRoot.returnCd]}")
      }
    }
  }

  return result
}

def oauthInitialize() {
  return "${state.empUrl}/spx/login/signIn?country=${state.countryCode}&language=${state.langCode}&svc_list=SVC202&client_id=LGAO221A02&division=ha&&state=xxx&show_thirdparty_login=GGL,AMZ,FBK&redirect_uri=${URLEncoder.encode("https://kr.m.lgaccount.com/login/iabClose")}"
}

def getDevices() {
  logger("debug", "getDevices()")
  def data = lgAPIGet("${state.thinqUrl}/service/application/dashboard")
  if (data) {
    def devices = data.item
    logger("info", "Found ${devices?.size()} devices")

    return devices.findAll { d -> supportedDeviceTypes.find { supported -> supported == d.deviceType } }
  }
  return null
}

def findMasterDevice() {
  logger("debug", "findMasterDevice()")
  return getChildDevices().find {
      it.hasCapability("Initialize") && it.getDataValue("master") == "true"
  }
}

def getDeviceThinQVersion(dev) {
  logger("debug", "getDeviceThinQVersion(${dev})")
  def thinqDeviceId = dev?.deviceNetworkId?.replace("thinq:", "")

  if (thinqDeviceId) {
    return state.foundDevices.find { it.id == thinqDeviceId}?.version
  }
  return null
}

// Common device state processing methods
def getParsedValue(value, param, modelInfo) {
  logger("debug", "getParsedValue(${value}, ${param}, ${modelInfo})")

  if (param == null)
    return value

  switch (param.type) {
    case "Serveral": // Typo in the API
    case "Bit":
      def result = []
      for (bit in param.option) {
        // Just a bit flag
        if (bit.length == 1) {
          if (value & (1<<bit.startbit))
            result << bit.value
        }
        // Sub byte value
        else {
          def bitValue = 0
          for (def i = bit.startbit; i < bit.startbit + bit.length; i++) {
            if (value & (1<<i))
                bitValue = bitValue + (value & (1<<i))
          }
          bitValue >>= bit.startbit
          result << ["${bit.value}": bitValue]
        }
      }
      return result
    case "Range":
      return value
    case "Enum":
      return param?.option[value.toString()] ?: value
    case "Reference":
      def refField = param.option[0]
      if (refField)
        return modelInfo."${refField}"."${value}"?._comment ?: value
      return value
    default:
      return value
  }
}

def getValueDefinition(name, values) {
  logger("debug", "getValueDefinition(${name}, ${values})")
  for (item in values.keySet()) {
    if (item == name)
      return values[item]
  }
  return null
}

// V1 device methods
def registerRTIMonitoring(dev) {
  logger("debug", "registerRTIMonitoring(${dev})")

  if (getDeviceThinQVersion(dev) == "thinq1") {
    def thinqDeviceId = dev?.deviceNetworkId?.replace("thinq:", "")
    def resultData = lgEdmPost("${state.thinq1Url}/rti/rtiMon", [
      "cmd": "Mon",
      "cmdOpt": "Start",
      "deviceId": thinqDeviceId,
      "workId": UUID.randomUUID().toString()
    ])
    if (resultData?.returnCd == "0000") {
      dev.updateDataValue("workId", resultData.workId)
      return resultData.workId
    }
    else
      return null
  }
}

def stopRTIMonitoring(dev) {
  logger("debug", "stopRTIMonitoring(${dev})")

  if (getDeviceThinQVersion(dev) == "thinq1" && dev.getDataValue("workId") != null) {
    def thinqDeviceId = dev?.deviceNetworkId?.replace("thinq:", "")
    def resultData = lgEdmPost("${state.thinq1Url}/rti/rtiMon", [
      "cmd": "Mon",
      "cmdOpt": "Stop",
      "deviceId": thinqDeviceId,
      "workId": dev.getDataValue("workId")
    ])
    dev.removeDataValue("workId")
  }
}

def getRTIData(workList) {
  logger("debug", "getRTIData(${workList})")

  def result = [:]
  def resultData = lgEdmPost("${state.thinq1Url}/rti/rtiResult", [
    "workList": workList
  ])

  // No data available (yet)
  if (resultData?.returnCd == null)
    return result
  else if (resultData.returnCd != "0000") {
    logger("error", "getRTIData(${workList}) - RTI Data: ${responseCodeText[resultData.returnCd]}")
  }
  else {
    for (workItem in resultData.workList) {
      def deviceId = workItem.deviceId
      def returnCode = workItem.returnCode
      def format = workItem.format
      def data = workItem.returnData?.decodeBase64()

      if (data != null) {
        def dev = getChildDevice("thinq:" + deviceId)
        if (dev != null) {
          modelInfo = state.foundDevices.find { it.id == deviceId }?.modelJson

          if (modelInfo) {
            if (modelInfo?.Monitoring?.type == "BINARY(BYTE)") {
              result[deviceId] = decodeBinaryRTIMessage(modelInfo.Monitoring.protocol, modelInfo, data)
            }
            else if (modelInfo?.Monitoring?.type == "THINQ2") {
              logger("error", "getRTIData(${workList}) - Received RTI Data for Thinq2 device ${deviceId} this shouldn't happen...")
            }
            else {
              // It's already JSON (I think?)
              result[deviceId] = data
            }
          }
        }
      }
    }
  }
  return result
}

def decodeBinaryRTIMessage(protocol, modelInfo, data) {
  logger("debug", "decodeBinaryRTIMessage(${protocol}, ${modelInfo}, ${data})")

  def output = [:]
  def values = modelInfo.Value
  for (parameter in protocol) {
    def start = parameter.startByte
    def end = parameter.startByte + parameter.length - 1
    def name = parameter.value
    def defaultValue = parameter.default

    output."$name" = defaultValue
    if (end < data?.size()) {
      def bytes = data[start..end]

      def value = 0
      for (def i = 0; i < bytes.size(); i++) {
        value = (value << 8) + bytes[i]
      }

      def paramDefinition = getValueDefinition(name, values)
      def parsedValue = getParsedValue(value, paramDefinition, modelInfo)
      output."$name" = parsedValue
    }
  }
  return output
}

def refreshV1Devices() {
  logger("debug", "refreshV1Devices()")
  def workList = []
  for (dev in getChildDevices()) {
    def thinqDeviceId = dev?.deviceNetworkId?.replace("thinq:", "")
    if (getDeviceThinQVersion(dev) == "thinq1" && dev.getDataValue("workId") != null) {
      workList << ["deviceId": thinqDeviceId, "workId": dev.getDataValue("workId")]
    }
  }
  if (workList.size() > 0) {
    def rtiData = getRTIData(workList)
    for (deviceId in rtiData.keySet()) {
      def childDevice = getChildDevice("thinq:" + deviceId)
      if (childDevice && rtiData[deviceId] != null)
        childDevice.processStateData(rtiData[deviceId])
    }
  }
}

// V2 device methods
def retrieveMqttDetails() {
  logger("debug", "retrieveMqttDetails()")
  def caCert = ""
  httpGet([
    uri: caCertUrl,
    textParser: true
  ]) {
    resp ->
      caCert = resp.data.text
  }
  logger("trace", "retrieveMqttDetails() - ${caCert}")
  return [server: state.mqttServer, subscriptions: state.subscriptions, certificate: state.cert, privateKey: privateKey, caCertificate: caCert, clientId: state.client_id]
}

def processMqttMessage(dev, payload) {
  logger("debug", "processMqttMessage(${dev}, ${payload})")

  switch (payload.type) {
    case "monitoring":
      def targetDevice = getChildDevice("thinq:" + payload.deviceId)
      return processDeviceMonitoring(targetDevice, payload)
    default:
      logger("debug", "processMqttMessage(${dev}, ${payload}) - Unknown MQTT Message")
  }
}

def processDeviceMonitoring(dev, payload) {
  logger("debug", "processDeviceMonitoring(${dev}, ${payload})")

  if (dev != null) {
    def deviceId = dev.deviceNetworkId.replace("thinq:", "")
    modelInfo = state.foundDevices.find { it.id == deviceId }?.modelJson
  /*	def stateData = decodeMQTTMessage(modelInfo.Monitoring.protocol, modelInfo, payload.data.state.reported)
    if (stateData != null)
      dev.processStateData(stateData)*/
  }
}

def decodeMQTTMessage(protocol, modelInfo, data) {
  logger("debug", "decodeMQTTMessage(${protocol}, ${modelInfo}, ${data})")

  def output = [:]
  def values = modelInfo.Value
  for (parameter in protocol) {
    def mqttName = parameter.superSet
    def name = parameter.value

    def value = data[mqttName]

    output."$name" = null

    if (value != null) {
      def paramDefinition = getValueDefinition(name, values)
      def parsedValue = getParsedValue(value, paramDefinition, modelInfo)
      output."$name" = parsedValue
    }
  }
  return output
}

def cleanupChildDevices() {
  logger("debug", "cleanupChildDevices()")

  for (d in getChildDevices()) {
    def deviceId = d.deviceNetworkId.replace("thinq:","")

    def deviceFound = false
    for (dev in thinqDevices) {
      if (dev == deviceId) {
        deviceFound = true
        break
      }
    }

    if (deviceFound == true)
      continue

    deleteChildDevice(d.deviceNetworkId)
  }
}


/**
* @param level Level to log at, see LOG_LEVELS for options
* @param msg Message to log
*/
private logger(level, msg) {
  if (level && msg) {
    Integer levelIdx = LOG_LEVELS.indexOf(level)
    Integer setLevelIdx = LOG_LEVELS.indexOf(logLevel)
    if (setLevelIdx < 0) {
      setLevelIdx = LOG_LEVELS.indexOf(DEFAULT_LOG_LEVEL)
    }
    if (levelIdx <= setLevelIdx) {
      log."${level}" "${app.name} ${msg}"
    }
  }
}
