/**
 *  ControlArt Piso Alamo - GW 
 *
 *  Copyright 2025 VH 
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *
 *            --- Driver para GW Piso Alamo
 *           v.1  26/03/2025 - BETA. 
*/

metadata {
    definition(name: "Álamo V3 Gateway", namespace: "VH", author: "VH") {
        capability "TemperatureMeasurement"
        capability "Thermostat"
        capability "Refresh"

        attribute "supportedThermostatFanModes", "JSON_OBJECT"
	    attribute "supportedThermostatModes", "JSON_OBJECT"
	    attribute "supportedFanSpeeds", "JSON_OBJECT"
        attribute "speed", "JSON_OBJECT"        
        attribute "floorTemperature", "number"
        attribute "heatingStatus", "string"
        attribute "lockStatus", "string"
        
        command "setHeatingSetpoint", [[name:"temperature", type:"NUMBER", description:"Target temperature"]]
        command "setThermostatMode", [[name:"mode", type:"STRING", description:"Heating mode"]]
        command "lock"
        command "unlock"
        command "updateDeviceStatus", [[name:"mac", type:"STRING"]]
        command "setdefaults" 
    }
    
    preferences {
        input name: "gatewayIP", type: "text", title: "Gateway IP Address", required: true
        input name: "gatewayPort", type: "text", title: "Gateway Port", defaultValue: "5000", required: true
        input name: "thermostatMAC", type: "text", title: "Thermostat MAC Address", required: true
        input name: "refreshInterval", type: "enum", title: "Status Refresh Interval", 
              options: [[1:"1 minute"],[5:"5 minutes"],[10:"10 minutes"],[15:"15 minutes"],[30:"30 minutes"],[60:"1 hour"]], 
              defaultValue: 5, required: true
        input name: "enableFeedback", type: "bool", title: "Enable Command Feedback", defaultValue: true
        input name: "feedbackDelay", type: "number", title: "Feedback Delay (seconds)", 
              range: "1..60", defaultValue: 3, required: true
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

def installed() {
    log.info "Álamo V3 Gateway driver installed"
    initialize()
}

def updated() {
    log.info "Álamo V3 Gateway driver updated"
    initialize()
    setdefaults()
}

def initialize() {
    state.requestCount = 0
    state.lastRequestTime = 0
    unschedule()
    
    // Schedule regular refreshes based on user preference
    def interval = settings.refreshInterval ? settings.refreshInterval.toInteger() : 5
    if (interval < 1) interval = 1 // Ensure minimum 1 minute
    
    if (logEnable) log.debug "Scheduling refresh every ${interval} minutes"
    "runEvery${interval}Minutes"("refresh")
    
    // Initial refresh
    refresh()
}

def setdefaults() {

    
    //events << [name:"setSupportedThermostatFanModes", value:JsonOutput.toJson(["auto","circulate","on"]), descriptionText:text]
    //events << [name:"setSupportedThermostatModes", value:JsonOutput.toJson(["auto", "cool", "emergency heat", "heat", "off"]), descriptionText:text]
    def fanModes = ["auto", "cool", "emergency heat", "heat", "off"]
    def modes = ["auto","circulate","on"]
    def fanspeeds = ["low","medium-low","medium","medium-high","high","on","off","auto"]
    sendEvent(name: "thermostatSetpoint", value: "20", descriptionText: "Thermostat thermostatSetpoint set to 20")
    sendEvent(name: "coolingSetpoint", value: "20", descriptionText: "Thermostat coolingSetpoint set to 20") 
    sendEvent(name: "heatingSetpoint", value: "20", descriptionText: "Thermostat heatingSetpoint set to 20")     
    sendEvent(name: "temperature", value: convertTemperatureIfNeeded(68.0,"F",1))    
    sendEvent(name: "thermostatOperatingState", value: "idle", descriptionText: "Set thermostatOperatingState to Idle")     
    sendEvent(name: "thermostatFanMode", value: "auto", descriptionText: "Set thermostatFanMode auto")     
    sendEvent(name: "setHeatingSetpoint", value: "15", descriptionText: "Set setHeatingSetpoint to 15")     
	sendEvent(name: "supportedThermostatFanModes", value: fanModes, descriptionText: "supportedThermostatFanModes set")    
	sendEvent(name: "supportedThermostatModes", value: modes, descriptionText: "supportedThermostatModes set ")
	sendEvent(name: "supportedFanSpeeds", value: fanspeeds , descriptionText: "supportedThermostatModes set ")
	sendEvent(name: "speed", value: "auto", descriptionText: "speed set ")
   
}

def setThermostatFanMode(modo) {
   varmodo = modo
    sendEvent(name: "setThermostatFanMode", value: varmodo)
    def ircodetemp = 1
    valormodo = " "
    switch(varmodo) {
		case "off" : 
            off() 
            break
		case "heat" : 
            heat() 
            break        
        default: 
            logDebug("push: Botão inválido.")
            break   
    }
   
    log.info "Enviado o commando de set Thermostat Fan Mode =   " + varmodo 
   
}


def parse(String description) {
    if (logEnable) log.debug "Raw response: ${description}"
}

def refresh() {
    if (logEnable) log.debug "Refreshing thermostat status"
    updateDeviceStatus(thermostatMAC)
}

def updateDeviceStatus(mac) {
    checkRateLimit()
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: mac],
        contentType: "application/json",
        timeout: 10
    ]
    
    if (logEnable) log.debug "Requesting status with params: ${params}"
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Response data: ${resp.data}"
                handleStatusResponse(resp.data)
            } else {
                log.error "HTTP error: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to get thermostat status: ${e.message}"
    }
}

def handleStatusResponse(data) {
    if (data) {
        sendEvent(name: "temperature", value: data.temp, unit: "°C")
        sendEvent(name: "thermostatSetpoint", value: data.stemp, unit: "°C")
        sendEvent(name: "thermostatMode", value: data.power ? "heat" : "off")
        sendEvent(name: "thermostatOperatingState", value: data.ativo ? "heating" : "idle")
        sendEvent(name: "floorTemperature", value: data.sensor, unit: "°C")
        sendEvent(name: "heatingStatus", value: data.ativo ? "heating" : "idle")
        sendEvent(name: "lockStatus", value: data.bloqueio ? "locked" : "unlocked")
    }
}

def setHeatingSetpoint(temperature) {
    if (logEnable) log.debug "Setting heating setpoint to ${temperature}°C"
    checkRateLimit()
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: thermostatMAC, stemp: temperature],
        contentType: "application/json",
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Setpoint updated successfully"
                if (settings.enableFeedback) {
                    runIn(settings.feedbackDelay.toInteger(), "refresh")
                }
            } else {
                log.error "Failed to update setpoint: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to set heating setpoint: ${e.message}"
    }
}

def heat() {
 setThermostatMode(heat)
    
}
def setThermostatMode(mode) {
    if (logEnable) log.debug "Setting thermostat mode to ${mode}"
    checkRateLimit()
    
    def power = mode == "heat" ? 1 : 0
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: thermostatMAC, power: power],
        contentType: "application/json",
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Thermostat mode updated successfully"
                if (settings.enableFeedback) {
                    runIn(settings.feedbackDelay.toInteger(), "refresh")
                }
            } else {
                log.error "Failed to update thermostat mode: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to set thermostat mode: ${e.message}"
    }
}

def off() {
    if (logEnable) log.debug "Turning Off thermostat"
    checkRateLimit()
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: thermostatMAC, power: 0],
        contentType: "application/json",
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Thermostat Turned Off successfully"
                if (settings.enableFeedback) {
                    runIn(settings.feedbackDelay.toInteger(), "refresh")
                }
            } else {
                log.error "Failed to Turn OFF thermostat: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to Turn OFF thermostat: ${e.message}"
    }
}


def lock() {
    if (logEnable) log.debug "Locking thermostat"
    checkRateLimit()
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: thermostatMAC, bloqueio: 1],
        contentType: "application/json",
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Thermostat locked successfully"
                if (settings.enableFeedback) {
                    runIn(settings.feedbackDelay.toInteger(), "refresh")
                }
            } else {
                log.error "Failed to lock thermostat: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to lock thermostat: ${e.message}"
    }
}

def unlock() {
    if (logEnable) log.debug "Unlocking thermostat"
    checkRateLimit()
    
    def params = [
        uri: "http://${gatewayIP}:${gatewayPort}",
        path: "/acao",
        query: [mac: thermostatMAC, bloqueio: 0],
        contentType: "application/json",
        timeout: 10
    ]
    
    try {
        httpGet(params) { resp ->
            if (resp.status == 200) {
                if (logEnable) log.debug "Thermostat unlocked successfully"
                if (settings.enableFeedback) {
                    runIn(settings.feedbackDelay.toInteger(), "refresh")
                }
            } else {
                log.error "Failed to unlock thermostat: ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "Failed to unlock thermostat: ${e.message}"
    }
}

def checkRateLimit() {
    def now = now()
    if (state.lastRequestTime && (now - state.lastRequestTime) < 6000) { // 6 seconds between requests (10 per minute)
        state.requestCount = (state.requestCount ?: 0) + 1
        if (state.requestCount > 10) {
            log.warn "Rate limit exceeded - waiting before next request"
            pauseExecution(6000)
            state.requestCount = 0
        }
    } else {
        state.requestCount = 0
    }
    state.lastRequestTime = now
}
