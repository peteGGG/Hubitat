/**
 *  	Hubitat - Child Driver - MyAir MyZone (has sensor)
 *
 *  	This driver utilises the MyAir API documented by AdvantageAir https://advantageair.proboards.com/thread/2/sticky-aircon-api-document 
 *
 *  	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  	in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  	for the specific language governing permissions and limitations under the License.
 * 
 * 		Requires:
 *  	- [User Driver] MyAir Controller (Parent Driver)
 * 
 *  	Dashboard Tips:
 *  		- When adding this Child Device to a Dashboard, Use the "Dimmer" Template to receive functionality of the commands
 *
 * 		Changelog
 * 		-------------------------------------------------------------------------
 * 		Date			|	Author 				| Description
 * 		-------------------------------------------------------------------------
 * 		15/04/2022 		| 	peteGGG	 			| Inital Release - Version 0.1.0
 * 		-------------------------------------------------------------------------
 *
 */
public static String version() { return "0.1.0" }

import groovy.json.JsonSlurper
import groovy.transform.Field

// Create Maps required for Different Modes and Fan Levels
@Field final Map MyZone_MODES = [
	"off": 			"Off",
    "on":    		"On",
    "myzone":    	"myZone"
]

@Field final Map MyAir_FANLEVELS = [
    "low":    	"Low",
    "medium":   "Medium",
    "high":    	"High"
]

// MyAir MyZone Device Definition
metadata {
  definition (name: "MyAir MyZone", namespace: "AdvantageAir", author: "peteGGG") {
	capability "Thermostat"
	capability "Switch"

	attribute "switch", "ENUM", ["on", "off"]
	attribute "temperature", "number"
	attribute "thermostatSetpoint", "number"
	
	// Standard Attributes for device capability	
	attribute "thermostatMode", "string"
	attribute "thermostatFanMode", "string"
	attribute "thermostatSetpoint", "number"	
	attribute "thermostatZoneSetTemp", "number"	
	attribute "thermostatOperatingState", "string"
	
	// Standard commands for device capability
	command "setThermostatFanMode", [[name: "Fan speed*",type:"ENUM", description:"Fan speed to set", constraints: MyAir_FANLEVELS.collect {k,v -> k}]]
	command "setThermostatMode", [[name: "My Air Mode*",type:"ENUM", description:"MyAir Mode to set", constraints: MyZone_MODES.collect {k,v -> k}]]
		
	preferences {
        input (name: "debugLogging", type: "bool", defaultValue: false, submitOnChange: true, title: "Enable debug logging\n<b>CAUTION:</b> a lot of log entries will be recorded!")			                    
    }
  }
}

def updated() {

	logDebug "Updated with settings: ${settings}"
    // Prevent function from running twice on save
    if (!state.updated || now() >= state.updated + 5000){
    }
    state.updated = now()
	
	// this only need to be set once - supportedThermostatFanModes is what is displayed in the dashboard.
	sendEvent(name: "supportedThermostatFanModes", value: ["Low","Medium","High"], displayed: false)
	
	// this only need to be set once - supportedThermostatModes is what is displayed in the dashboard.
	sendEvent(name: "supportedThermostatModes", value: ["Off","On","MyZone"], displayed: false)
}

// Standard Capapability Command - Turn Zone Off
def off()
{
	// Sets the state of the Zone to close	
	// As per API format of HTTP GET is  - /setAircon?json={ac1:{"zones":{"z2":{"state":"close"}}}}
	
	def acRef = parent.getAcRef()
	def zoneRef = getZoneRef()	
	logDebug "Executing 'off' for ${zoneRef}"
	
	def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"state\":\"close\"}}}}","UTF-8")			
	def apiCommandwithQuery = "/setAircon?${httpQuery}"		

	sendEvent(name: "switch", value: "off")
	sendEvent(name: "thermostatOperatingState", value: "vent closed")
	
	// Call the Parent API Function
	parent.getMyAirAPI(apiCommandwithQuery)	
}

// Standard Capapability Command - Turn Zone On
def on()
{
	// Sets the state of the Zone to close
	// As per API format of HTTP GET is  - /setAircon?json={ac1:{"zones":{"z2":{"state":"open"}}}}
	
	def acRef = parent.getAcRef()
	def zoneRef = getZoneRef()
	logDebug "Executing 'on' for ${zoneRef}"
	
	def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"state\":\"open\"}}}}","UTF-8")			
	def apiCommandwithQuery = "/setAircon?${httpQuery}"		

	sendEvent(name: "switch", value: "on")
	sendEvent(name: "thermostatOperatingState", value: "vent open")
	
	// Call the Parent API Function
	parent.getMyAirAPI(apiCommandwithQuery)
}

// Standard Capability Command - setThermostatMode
def setThermostatMode(String newThermostatMode)
{
	def acRef = parent.getAcRef()
	def zoneRef = getZoneRef()
	logDebug "Executing 'setThermostatMode' to Mode '${newThermostatMode}' for ${zoneRef}"
	
	def currentThermostatOperatingState = device.currentValue("thermostatOperatingState")
	
	if(currentThermostatOperatingState.contains("myZone"))
	{
		logWarn "Cannot change Thermostat Mode for ${zoneRef} when set to MyZone"
	}
	else
	{
		def parentThermostatMode = parent.getThermostatMode()
		
		switch((newThermostatMode.toLowerCase())) {
			case "off":
				def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"state\":\"close\"}}}}","UTF-8")			
				def apiCommandwithQuery = "/setAircon?${httpQuery}"		

				sendEvent(name: "switch", value: "off")
				sendEvent(name: "thermostatOperatingState", value: "idle")
				sendEvent(name: "thermostatMode", value: "off")
				
				// Call the Parent API Function
				parent.getMyAirAPI(apiCommandwithQuery)
				break
			case "on":
				def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"state\":\"open\"}}}}","UTF-8")			
				def apiCommandwithQuery = "/setAircon?${httpQuery}"
				
				sendEvent(name: "switch", value: "on")
				switch(parentThermostatMode){
					case "cool":
						sendEvent(name: "thermostatOperatingState",  value: "cooling")
						sendEvent(name: "thermostatMode", value: "cool")
						break
					case "heat":
						sendEvent(name: "thermostatOperatingState",  value: "heating")
						sendEvent(name: "thermostatMode", value: "heat")
						break
					case "fan":
						sendEvent(name: "thermostatOperatingState", value: "fan only")
						sendEvent(name: "thermostatMode", value: "fan")
						break
					case "dry":
						sendEvent(name: "thermostatOperatingState", value: "fan only")
						sendEvent(name: "thermostatMode", value: "dry")
						break
				}
				// Call the Parent API Function
				parent.getMyAirAPI(apiCommandwithQuery)
				
				break
			case "myzone":
				
				switch(parentThermostatMode){
					case "cool":
						sendEvent(name: "thermostatOperatingState",  value: "cooling (myZone)")
						sendEvent(name: "thermostatMode", value: "cool")
						break
					case "heat":
						sendEvent(name: "thermostatOperatingState",  value: "heating (myZone)")
						sendEvent(name: "thermostatMode", value: "heat")
						break
					case "fan":
						sendEvent(name: "thermostatOperatingState", value: "fan only")
						sendEvent(name: "thermostatMode", value: "fan")
						break
					case "dry":
						sendEvent(name: "thermostatOperatingState", value: "fan only")
						sendEvent(name: "thermostatMode", value: "dry")
						break
				}
				
				// Call the Parent API Function
				parent.setMyZone(state.zoneNumber)

				// pauseExecution(5000)
				
				if(parentMyZoneNumber != state.zoneNumber)
				{
					logWarn "Failed to set myZone to ${state.zoneNumber}"
				}							
				break
		}
	}
	
	//
}
def setCoolingSetpoint(Number newTemp){
	logDebug "Executing 'setCoolingSetpoint' to '${newTemp}'"
	
	def zoneRef = getZoneRef()
	parent.setZoneTemperaturePoint(newTemp,state.zoneNumber)
}

def setHeatingSetpoint(Number newTemp){
	logDebug "Executing 'setHeatingSetpoint' with '${newTemp}'"
	
	def zoneRef = getZoneRef()
	parent.setZoneTemperaturePoint(newTemp,state.zoneNumber)
}

def setThermostatFanMode(String newFanMode){
	logDebug "Executing 'setThermostatFanMode' to '${newFanMode}'"
	
	sendEvent(name: "thermostatFanMode", value: newFanMode)

	// Just call the Parent Function
	parent.setThermostatFanMode(newFanMode)
}


// Standard Parse Command
def parse(String description) {
    //logDebug "Parsed Response: ${description}"
		
	// Parse myAir response (this comes from the parent, so we are only going to process a subset of the Parent's functionality relating to the success of the API call)
    def msg = parseLanMessage(description)
    def body = msg.body
	logDebug "Executing 'parse()' - HTTP Body: ${body}"
		
	// Time to process some JSON
	def slurper = new JsonSlurper()
	Map SystemData = slurper.parseText(body)
	
	// perform actions for the setAircon API call
	if(body.contains("ack") && body.contains("setAircon"))
	{
		// setAirCon Command sent in API GET request
		logDebug "setAircon API returned ${body}"
		
		if(SystemData.ack)
			{
				logDebug "setAirCon API success"
				def ackText = SystemData.ack
				sendEvent(name: "setAirConResponse", value: ackText)
				parent.refresh()
			}
			else
			{
				def APIreason = SystemData.reason
				logWarn "setAirCon API Failed - Reason: '${APIreason}'"
				sendEvent(name: "setAirConResponse", value: ackText)	
				parent.refresh()
			}		
	}
}

def updateFromParent(Map zoneDetail){
	
	// Function called for each zone to update/refresh 
	logDebug "Function 'updateFromParent' ${zoneDetail.number} - ${zoneDetail.name}"
	
	// Get the rest of the Zone Detail
	def zoneState = zoneDetail.state
	def zoneMeasuredTemp = zoneDetail.measuredTemp
	def zoneSetTemp = zoneDetail.setTemp
	def zoneNumber = zoneDetail.number
	
	def zoneOperatingState = null
	def isMyZone = null
	
	def parentMyZoneNumber = parent.getMyZoneNumber()
	def parentFanSpeed = parent.getFanSpeed()
	def parentThermostatMode = parent.getThermostatMode()
	
	sendEvent(name: "switch", value: zoneState)
	
	if(zoneState == "open")
	{		
		switch(parentThermostatMode){
			case "cool":
				zoneOperatingState = "cooling"
				sendEvent(name: "coolingSetpoint", value: zoneSetTemp)
				sendEvent(name: "thermostatMode", value: "cool")
				break
			case "heat":
				zoneOperatingState = "heating"
				sendEvent(name: "heatingSetpoint", value: zoneSetTemp)
				sendEvent(name: "thermostatMode", value: "heat")
				break
			case "fan":
				zoneOperatingState = "fan only"
				sendEvent(name: "thermostatMode", value: "fan")
				break
			case "dry":
				zoneOperatingState = "fan only (dry)"
				sendEvent(name: "thermostatMode", value: "dry")
				break
			case "off":
				zoneOperatingState = "idle"
				sendEvent(name: "thermostatMode", value: "off")
				break
		}
	
		if(parentMyZoneNumber == zoneNumber)
		{
			sendEvent(name: "thermostatOperatingState",  value: "${zoneOperatingState} (myZone)")
		}
		else
		{
			sendEvent(name: "thermostatOperatingState",  value: zoneOperatingState)
		}
		
		sendEvent(name: "thermostatFanMode", value: parentFanSpeed)
		sendEvent(name: "temperature", value: zoneMeasuredTemp)
		sendEvent(name: "thermostatSetpoint", value: zoneSetTemp)
	}
	else
	{
		sendEvent(name: "thermostatFanMode", value: parentFanSpeed)
		sendEvent(name: "thermostatOperatingState",  value: "idle")
		sendEvent(name: "thermostatMode", value: "off")
		sendEvent(name: "temperature", value: zoneMeasuredTemp)
	}
	
	state.zoneNumber = zoneNumber
}

private getZoneRef(){
	
	// As the Device Zone is built into the DNID (assuming that this has not been manually tamped with), get this
	def DNID = device.deviceNetworkId
	
	def zoneRef = DNID.split("\\|")[1]
	
	return zoneRef	
}

// Logging

def logDebug (message) { if (debugLogging) log.debug (message) }
def logInfo  (message) { log.info (message) }
def logWarn  (message) { log.warn (message) }

