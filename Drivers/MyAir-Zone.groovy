/**
 *  	Hubitat - Child Driver - MyAir Zone (no sensor)
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
 *  	- When adding this Child Device to a Dashboard, Use the "Dimmer" Template to receive functionality of the commands
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

metadata {
  definition (name: "MyAir Zone", namespace: "AdvantageAir", author: "peteGGG") {
	capability "SwitchLevel"
	capability "Switch"

	attribute "level", "number"
	attribute "switch", "ENUM", ["on", "off"]
		
	command "setLevel", [[Name: "Set Zone Level", type: "NUMBER", description: "Level to set (0 to 100)"]]
		
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
}


// Standard Capapability Command - presetLevel
def setLevel(Number newLevel)
{
	// Sets the Zone Level to a new level.
	logDebug "Executing 'presetLevel' for ${zoneRef} to ${newLevel}"
	
	// as Per API value - Percentage value of zone (min 5 - max 100, increments of 5)- only valid when Zone type = 0.
	
	// as the API expects whole number and increments of 5, round the value and correct values that are above/below 0-100.
			
	double newLevelDouble = Double.parseDouble("${newLevel}")
	
	if(newLevelDouble >= 97)
	{
		newLevelRoundedDouble = 5*Math.ceil(newLevelDouble/5)
		logDebug "Rounded newLevel to ${newLevelRoundedDouble}"
	}
	else
	{
		newLevelRoundedDouble = 5*Math.round(newLevelDouble/5)
		
	}
	logDebug "Rounded newLevel to ${newLevelRoundedDouble}"
	
	if(newLevelRoundedDouble < 0)
	{
		newLevelRoundedDouble = 0
		logDebug "newLevel below 0 - setting to 0"
	}
	if(newLevelRoundedDouble > 100)
	{
		newLevelRoundedDouble = 100
		logDebug "newLevel above 100 - setting to 100"
	}
		
	// As per API format of HTTP GET is - /setAircon?json={ac1:{"zones":{"z2":{"value":80}}}
	
	def zoneRef = getZoneRef()	
	
	def httpQuery = "json=" + URLEncoder.encode("{\"ac1\":{\"zones\":{\"${zoneRef}\":{\"value\":\"${newLevelRoundedDouble}\"}}}}","UTF-8")			
	def apiCommandwithQuery = "/setAircon?${httpQuery}"		

	sendEvent(name: "level", value: "${newLevelRoundedDouble}")
	
	parent.getMyAirAPI(apiCommandwithQuery)
}

// Standard Capapability Command - Turn Zone Off
def off()
{
	// Sets the state of the Zone to close	
	// As per API format of HTTP GET is  - /setAircon?json={ac1:{"zones":{"z2":{"state":"close"}}}}
	
	def zoneRef = getZoneRef()	
	logDebug "Executing 'off' for ${zoneRef}"
	
	def httpQuery = "json=" + URLEncoder.encode("{\"ac1\":{\"zones\":{\"${zoneRef}\":{\"state\":\"close\"}}}}","UTF-8")			
	def apiCommandwithQuery = "/setAircon?${httpQuery}"		

	sendEvent(name: "switch", value: "off")
	
	// Call the Parent API Function
	parent.getMyAirAPI(apiCommandwithQuery)	
}

// Standard Capapability Command - Turn Zone On
def on()
{
	// Sets the state of the Zone to close
	// As per API format of HTTP GET is  - /setAircon?json={ac1:{"zones":{"z2":{"state":"open"}}}}
	
	def zoneRef = getZoneRef()
	logDebug "Executing 'on' for ${zoneRef}"
	
	def httpQuery = "json=" + URLEncoder.encode("{\"ac1\":{\"zones\":{\"${zoneRef}\":{\"state\":\"open\"}}}}","UTF-8")			
	def apiCommandwithQuery = "/setAircon?${httpQuery}"		

	sendEvent(name: "switch", value: "on")
	
	// Call the Parent API Function
	parent.getMyAirAPI(apiCommandwithQuery)
}

// Standard Parse Command
def parse(String description) {
    //logDebug "Parsed Response: ${description}"
	
	// Parse myAir response (this comes from the parent, so we are only going to process a subset of the Parent's functionality relating to the success of the API call)
    def msg = parseLanMessage(description)
    def body = msg.body
	
	// Custom definitions which will perform logging and update the attributes within the device
	def events = []
	
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
			events.add(createEvent(name: "setAirConResponse", value: ackText))
		}		
	}
}

def updateFromParent(Map zoneDetail){
	
	// Function called for each zone to update/refresh 
	logDebug "Function 'updateFromParent' ${zoneDetail.number} - ${zoneDetail.name}"
	
	// Get the rest of the Zone Detail
	def zoneState = zoneDetail.state
	def zoneValue = zoneDetail.value
	
	switch(zoneState){
		case "open":
			zoneState = "on"
			logDebug "ZoneState: ${zoneState}"
			break
		case "close":
			zoneState = "off"
			logDebug "ZoneState: ${zoneState}"
            break
	}	
	sendEvent(name: "level", value: zoneValue)
	sendEvent(name: "switch", value: zoneState)
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

