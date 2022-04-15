/**
 *  	Hubitat - Driver - MyAir Controller
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
 *  	Pre-requisites:
 *  	- [User Driver] MyAir Zone Driver (child devices require this)
 *  	- [User Driver] MyAir MyZone Driver (child devices require this)
 *  	- My Air System will require a Static IP address - New Application in development to perform discovery and manage the IP Address
 * 
 *		Installation instructions
 *  	- Install MyAir-Controller, MyAir-MyZone and MyAir-Zone to the Hubitat User Drivers
 *  	- Create a New Virtual Device 
 *  	- Provide a Meaningful Device Name such as MyAir, then set the Type to "MyAir Controller"
 *  	- Press Save Device
 *  	- Provide the IP Address of the My Air System (avaiable within the Setup -> Advanced Info and is listed as TSP IP), then press Save Preferences
 *  	- Upon the first save, a discovery will occur against the IP address
 *  	- Child Devices will be created for Each Zone and MyZone (those with a temperature sensor) allowing you to turn on/off each zone individually or set a thermostat Mode.
 * 		- Profit!
 * 
 *  	Dashboard Creation
 *  	- To allow for effective control of your My Air System, a new dashboard will need to be created that includes the MyAir Controller (The device you just created) and the Zones (Child devices) that are in format <DeviceName>-<ZoneName>.
 *  	- Dashboard Items Required:
 *    		1. Controller - Type "Thermostat" with recommended minimum width of 2. 
 *       		This would be considered the master On/Off Switch, allows you to set the Mode and fan speed to be set for the system as well as adjusting the thermostat level for the selected myAir.  
 *    		2. MyAir Zones (one Dashboard Item per MyZone) - Type "Thermostat" 
 *       		This is where Myzone Devices can be configured On/Off or as a the MyZone.  This control will allow for on/off or setting the target Temperature for the zone.  Adjusting Fan control in this item on the dashboard will adjust the Controller (master)
 *    		3. Standard Zones (one dashboard Item per Zone) - Type "Dimmer" - Change the Icon to ac_unit
 *       		This is the where standard Zones can be configured On/Off and set a percentage of the zone enabled.
 *
 *  	Known issues (Development in progress)
 *  	- If the IP address changes of the device, the Hubitat Driver will stop working.  it is recommended setting a static IP on the MyAir Tablet or creating a DHCP reservation for the device in your router.  Future release of a Hubitat User Application will manage the IP address of the device.
 *  	- there is a Hard-coding currently for ac1 stored in state.acRef (within updated() function).  Future release of Hubitat User Application will be manage this attribute.
 *  	- If a Standard Zone is changed from a MyZone or vise-versa, the Child device does not update the type.  This currently requires manual adjustment, or deletion of the affected child device (it will re-create upon next refresh)
 * 
 *  	Changelog
 * 		-------------------------------------------------------------------------
 * 		Date			|	Author 				| Description
 * 		-------------------------------------------------------------------------
 * 		15/04/2022 		| 	peteGGG	 			| Inital Release - Version 0.1.0
 * 		-------------------------------------------------------------------------
 *
 */
public static String version() { return "0.1.0" }

import groovy.transform.Field
import groovy.json.JsonSlurper
import groovy.json.JsonOutput

// Create Maps required for Different Modes and Fan Levels
@Field final Map MyAir_MODES = [
	"off": 		"Off",
    "cool":    	"Cool",
    "heat":    	"Heat",
    "fan":    	"Fan",
	"dry":    	"Dry"
]

@Field final Map MyAir_FANLEVELS = [
    "low":    	"Low",
    "medium":   "Medium",
    "high":    	"High"
]

// MyAir Controller Device Definition
metadata {
  definition (name: "MyAir Controller", namespace: "AdvantageAir", author: "peteGGG") {
	capability "Switch"
	capability "Refresh"
	capability "Polling"
	capability "Thermostat"
	
	// MyAir Attributes - Make it more friendly to read
	attribute "state", "string"
	attribute "mode", "string"
	attribute "countDownToOn", "number"
	attribute "countDownToOff", "number"
	attribute "thermostatZone", "number"
	attribute "setAirConResponse", "string"
	
	// Standard Attributes for device capability	
	attribute "thermostatMode", "string"
	attribute "thermostatFanMode", "string"
	attribute "thermostatSetpoint", "number"	
	attribute "thermostatZoneSetTemp", "number"	
	attribute "thermostatOperatingState", "string"

	// MyAir Custom Commands
	command "setCountDownToOff", [[name: "Sleep Timer", type: "NUMBER", description: "Set a sleep timer (Minutes)"]]
	command "setCountDownToOn", [[name: "Wake Timer", type: "NUMBER", description: "Set a wake timer (Minutes)"]]
	command "setMyZone", [[Name: "Zone Number", type: "NUMBER", description: "Set the Zone which the Thermostat is set to"]]
	command "dry"
	//, [[Name:"Set to Dry Mode", description: "Changes the mode of MyAir to Dry"]]
	
	// Standard commands for device capability
	command "setThermostatFanMode", [[name: "Fan speed*",type:"ENUM", description:"Fan speed to set", constraints: MyAir_FANLEVELS.collect {k,v -> k}]]
	command "setThermostatMode", [[name: "My Air Mode*",type:"ENUM", description:"MyAir Mode to set", constraints: MyAir_MODES.collect {k,v -> k}]]
	command "setCoolingSetpoint", [[name: "Set Cooling Point*", type: "NUMBER"]]
	command "setHeatingSetpoint", [[name: "Set Heating Point*", type: "NUMBER"]]
	command "fanOn", [[name: "Fan speed",type:"ENUM", description:"Fan speed to set", constraints: MyAir_FANLEVELS.collect {k,v -> k}]]
	
	preferences {
        input("ipAddress", "string", title:"myAir IP Address", required:true, displayDuringSetup:true)
        input("ipPort", "string", title:"myAir TCP Port (default: 2025)", defaultValue:2025, required:true, displayDuringSetup:true)		
		input("refreshInterval", "enum", title: "Refresh Interval in minutes", defaultValue: "10", required:true, displayDuringSetup:true, options: ["1","5","10","15","30"])
		input("defaultFanSpeed", "enum", title: "Default Fan setting", defaultValue: "low", required:false, displayDuringSetup:true, options: ["low","medium","high"])
        input (name: "debugLogging", type: "bool", defaultValue: false, submitOnChange: true, title: "Enable debug logging\n<b>CAUTION:</b> a lot of log entries will be recorded!")
    }
  }
}

// Built-In Callback Methods
def installed(){
	// This method is called when the device is first created and can be used to initialize any device specific configuration and setup.
	logInfo "Installing MyAir Controller Parent Device"
	updated()
}

def uninstalled(){
	// This method is called when the device is removed to allow for any necessary cleanup.
	logInfo "Uninstalling MyAir Controller Parent Device"
}

def updated() {
	// This method is called when the preferences of a device are updated.

    logDebug "Updated with settings: ${settings}"
    // Prevent function from running twice on save
    if (!state.updated || now() >= state.updated + 5000){
        // Unschedule existing tasks
        unschedule()
        runIn(5, 'refresh')
        // Start scheduled task
        startScheduledRefresh()
    }
	
	// Initialise the myZones Device State
	if (state.myZones == null) {
		state.myZones = [].toSet()
	}
	
	// this only need to be set once - supportedThermostatFanModes is what is displayed in the dashboard.
	sendEvent(name: "supportedThermostatFanModes", value: ["Low","Medium","High"], displayed: false)
	
	// this only need to be set once - supportedThermostatModes is what is displayed in the dashboard.
	sendEvent(name: "supportedThermostatModes", value: ["Off","Cool","Heat","Fan","Dry"], displayed: false)
	
	state.acRef = "ac1"
	state.updated = now()
}

def parse(String description) {
	// This method is called in response to a message received by the device driver.
	
    // Parse myAir response
    def msg = parseLanMessage(description)
    def body = msg.body
	
	def acRef = getAcRef()
	
	// logDebug "Executing 'parse()' - HTTP Body: ${body}"
	
	// The myAir API will typically respond in 3 ways
	// 1 - if a bad path is sent, the body response will return "Advantage Air v#######"
	// 2 - using the /getSystemData will return all the info for the system
	// 3 - using the /setAircon will return an ack and the request type of setAircon
	
	if(null != body && body.contains("Advantage Air"))
	{	
		// a Bad response has been made
		logWarn "A bad API call has been made"	
	}
	else
	{
		// Time to process some JSON
		def slurper = new JsonSlurper()
		Map SystemData = slurper.parseText(body)
		
		Map targetACData = [targetAC: "null"]
		
		// logDebug "SystemData: ${SystemData}"
		
		// perform actions for the getSystemData API call
		if(body.contains(acRef) && body.contains("hasAircons"))
		{
			def AirConSysType = SystemData.system.sysType
		
			SystemData?.aircons?.each {key, it ->
				if(key == acRef)
				{
					logDebug "getSystemData API called for ${AirConSysType} & AC Ref: ${acRef}"
					
					def infoJSON = JsonOutput.toJson(it)
					targetACData = slurper.parseText(infoJSON)
					
					//logDebug "targetACData: ${targetACData}"
				}
				else
				{
					logDebug "Not Found ${acRef}"
				}				
			}
			
			if(!targetACData.info.state)
			{
				logWarn "API did not return data for ${acRef}"
			}
			else
			{		
				// Process the acRef using targetACData
				logInfo "${acRef} Basic Info: State: '${targetACData.info.state}', Mode: '${targetACData.info.mode}'"
				
				def parsedAirConState = targetACData.info.state
				def parsedAirConMode = (targetACData.info.mode).replace('vent','fan')
				def parsedAirConFan = targetACData.info.fan
				def parsedAirConSleepTimer = targetACData.info.countDownToOff
				def parsedAirConWakeTimer = targetACData.info.countDownToOn
				def parsedAirConMyZone = targetACData.info.myZone
				
				def AirConZones = targetACData.info.noOfZones
				
				sendEvent(name: "state", value: parsedAirConState)
				sendEvent(name: "mode", value: parsedAirConMode)

				if(parsedAirConState == "on")
				{
					sendEvent(name: "thermostatMode", value: parsedAirConMode)
					switch(parsedAirConMode) {
						case "cool":
							sendEvent(name: "thermostatOperatingState", value: "cooling")
							break
						case "fan":
							sendEvent(name: "thermostatOperatingState", value: "fan only")
							break
						case "heat":
							sendEvent(name: "thermostatOperatingState", value: "heating")
							break
						case "dry":
							sendEvent(name: "thermostatOperatingState", value: "dry")
							break
					}
				}
				else
				{
					sendEvent(name: "thermostatMode", value: "off")
					sendEvent(name: "thermostatOperatingState", value: "idle")
				}
				
				sendEvent(name: "thermostatFanMode", value: parsedAirConFan)
				sendEvent(name: "countDownToOff", value: parsedAirConSleepTimer)
				sendEvent(name: "countDownToOn", value: parsedAirConWakeTimer)
				sendEvent(name: "thermostatZone", value: parsedAirConMyZone)
				
				// Parse the JSON for the thermostatZone so that we can set the thermostatSetpoint & temperature 
				def myZoneRef = "z" + (parsedAirConMyZone.toString()).padLeft(2,"0")
						
				// Parse the JSON for the Zones
				SystemData?.aircons?.ac1?.zones?.each {key, it ->
					
					// Update the thermostatSetpoint & temperature of the parsedAirConMyZone
					if(key == myZoneRef)
					{
						def parsedthermostatSetpoint = it.setTemp
						def parsedtemperature = it.measuredTemp
						
						sendEvent(name: "thermostatSetpoint", value: parsedthermostatSetpoint)
						sendEvent(name: "temperature", value: parsedtemperature)
						
						switch(parsedAirConMode) {
							case "cool":
								sendEvent(name: "coolingSetpoint", value: parsedthermostatSetpoint)
								break
							case "heat":
								sendEvent(name: "heatingSetpoint", value: parsedthermostatSetpoint)
								break
						}				
					}
					
					// As we are refreshing all the Zones, we should update the state of the Device to track the MyZone Temperature Zones (i.e. the ones with sensors)
					if(it.type == 1)
					{
						if(isNewMyZone(key))
						{
							logDebug "Registering MyZone device ${key}"
							state.myZones.add(key)
						}
					}
					else
					{
						if(!state.myZones?.contains(key))
						{
							state.myZones.remove(key)
						}
					}				
					//Create a Child Device for each Zone if needed and update the data from within that zone
					updateChildDevice(key, it)
				}
			}
		}
		else
		{
			// Did not contain acRef or hasAircons
			if(body.contains("hasAircons"))
			{
				logWarn "API did not return data for ${acRef}"
			}
		}
			
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
			}
			else
			{
				def APIreason = SystemData.reason
				logWarn "setAirCon API Failed - Reason: '${APIreason}'"
				sendEvent(name: "setAirConResponse", value: ackText)				
			}
			// Even though we have only just sent an API Request, send a refresh
			refresh()
		}
	}
	
	return events	
	
}
// Custom Functions

def setCountDownToOff(Number newTimer)
{
	// API Details  - "countDownToOff" - Number of minutes before the aircon unit switches off (0 - disabled, max 720 minutes)
	logDebug "Executing 'setCountDownToOff'"
	logDebug "Device State: ${device.currentValue("state")} - Current Sleep Timer: ${device.currentValue("countDownToOff")} - New Sleep Timer: ${newTimer}"
	
	def acRef = getAcRef()
	
	if(device.currentValue("state") == "on")
	{
		if(newTimer >= 0)
		{
			// as the API expects whole numbers - round the value		
			
			double newTimerDouble = Double.parseDouble("${newTimer}")
			newTimerRoundedDouble = Math.round(newTimerDouble)
			
			int newTimerValue = Integer.parseInt("${newTimerRoundedDouble}")
			
			if(newTimerValue > 720)
			{	
				newTimerValue = 720
			}
			def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"countDownToOff\":\"", "UTF-8") + newTimerValue + URLEncoder.encode("\"}}}","UTF-8")	
			def apiCommandwithQuery = "/setAircon?${httpQuery}"		
			
			if(newTimerValue == 0)
			{
				sendEvent(name: "hubactionMode", value: "Disabling Sleep Timer")
			}
			else 
			{
				sendEvent(name: "hubactionMode", value: "Setting Sleep Timer to ${newTimerValue}")
			}
			
			logDebug "Command 'setCountDownToOff' : calling API : ${apiCommandwithQuery}"		
			runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
			
			// create a refresh command after the new time comes around			
			int refreshDelay = (newTimerValue * 60) + 1			
			runIn(refreshDelay, 'refresh')
		}
		else
		{
			logDebug "Invalid command parameter - please enter a positive integer"
		}
	}
	else
	{
		logDebug "Sleep time can only be set when Device is Powered On."
	}
}

def setCountDownToOn(Number newTimer)
{
	// API Details  - "countDownToOn" - Number of minutes before the aircon unit switches off (0 - disabled, max 720 minutes)
	logDebug "Executing 'setCountDownToOn'"
	logDebug "Device State: ${device.currentValue("state")} - Current Wake Timer: ${device.currentValue("countDownToOn")} - New Wake Timer: ${newTimer}"
	
	def acRef = getAcRef()
	
	if(device.currentValue("state") == "off")
	{
		if(newTimer >= 0)
		{
			// as the API expects whole numbers - round the value		
			
			double newTimerDouble = Double.parseDouble("${newTimer}")
			newTimerRoundedDouble = Math.round(newTimerDouble)
			
			int newTimerValue = Integer.parseInt("${newTimerRoundedDouble}")
			
			if(newTimerValue > 720)
			{	
				newTimerValue = 720
			}
			def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"countDownToOn\":\"", "UTF-8") + newTimerValue + URLEncoder.encode("\"}}}","UTF-8")	
			def apiCommandwithQuery = "/setAircon?${httpQuery}"		
			
			if(newTimerValue == 0)
			{
				sendEvent(name: "hubactionMode", value: "Disabling Wake Timer")
			}
			else 
			{
				sendEvent(name: "hubactionMode", value: "Setting Wake Timer to ${newTimerValue}")
			}
			
			logDebug "Command 'setCountDownToOff' : calling API : ${apiCommandwithQuery}"		
			runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
			
			// create a refresh command after the new time comes around			
			int refreshDelay = (newTimerValue * 60) + 1			
			runIn(refreshDelay, 'refresh')			
			
		}
		else
		{
			logDebug "Invalid command parameter - please enter a positive integer"
		}
	}
	else
	{
		logDebug "Wake time can only be set when Device is Powered Off."
	}
}



// Built-in Capability Functions ----
def poll() {
    logDebug "Executing poll(), unscheduling existing"
    refresh()
}

def refresh() {
    logDebug "Refreshing"
	runIn(1, 'getMyAirAPI', [data:"/getSystemData"])
}

def on(){
    logDebug "Executing 'on'"
	
	if(device.currentValue("state") == "off")
	{		
		// Send a command to turn On the AC
		
		def acRef = getAcRef()
		
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\"}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Powering On")
		
		//logDebug "Command 'on' : calling API : ${apiCommandwithQuery}"		
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])		
		runIn(5, 'testOn')
	}
	else
	{
		logDebug "MyAir Device is already powered on"
	}
    
}

def off() {
    logDebug "Executing 'off'"
	if(device.currentValue("state") == "on")
	{
		// Send a command to turn off the AC
		def acRef = getAcRef()
		
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"off\"}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Powering Off")
	
		logDebug "Command 'off' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])		
		runIn(5, 'testOff')
	}
	else
	{
		logDebug "MyAir Device is already powered off"
	}
}

def cool()
{
	logDebug "Executing 'cool'"
	
	// Get the current state of the AC
	def currentMode = device.currentValue("thermostatMode")
	def currentState = device.currentValue("state")
	def targetMode = "cool"
	
	if((currentMode != targetMode) || (currentState == "off"))
	{
		// Send a command to switch mode of the AC - this will also turn it on if it is already off.
		def acRef = getAcRef()
		
		// API Example - /setAircon?json={"ac1":{"info":{"state":"on","mode":"cool"}}}
			
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\",\"mode\":\"cool\"}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Switching Mode to Cool")
		
		logDebug "Command 'cool' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])	
		runIn(6, 'testMode', [data:targetMode])
	}
}

def heat()
{
	logDebug "Executing 'heat'"
	
	// Get the current state of the AC
	def currentMode = device.currentValue("thermostatMode")
	def currentState = device.currentValue("state")
	def targetMode = "heat"
	
	if((currentMode != targetMode) || (currentState == "off"))
	{
		// Send a command to switch mode of the AC - this will also turn it on if it is already off.
		def acRef = getAcRef()
		
		// API Example - /setAircon?json={"ac1":{"info":{"state":"on","mode":"cool"}}}
			
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\",\"mode\":\"heat\"}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Switching Mode to heat")
		
		logDebug "Command 'heat' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])	
		runIn(6, 'testMode', [data:targetMode])
	}
}

def fanOn(String newFanMode)
{
	if(!newFanMode)
	{	
		logDebug "Executing 'fanOn'" 
	}
	else
	{	
		logDebug "Executing 'fanOn' to speed '${newFanMode}'"
	}
		
	// Get the current state of the AC
	def currentMode = device.currentValue("thermostatMode")
	def currentState = device.currentValue("state")
	def currentFanMode = device.currentValue("thermostatFanMode")
	def targetMode = "fan"
	
	if((currentMode != targetMode) || (currentState == "off") || (newFanMode != currentFanMode))
	{
		// Send a command to switch mode of the AC - this will also turn it on if it is already off.
		def acRef = getAcRef()
		
		// API Example - /setAircon?json={"ac1":{"info":{"state":"on","mode":"cool"}}}
		
		def httpQuery = null
			
		if(!newFanMode)
		{	
			httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\",\"mode\":\"vent\"}}}","UTF-8")	
		}
		else
		{
			httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\",\"mode\":\"vent\",\"fan\":\"${newFanMode}\"}}}","UTF-8")	
		}
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Switching Mode to fan")
		
		logDebug "Command 'fanOn' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])	
		runIn(6, 'testMode', [data:targetMode])
	}
}
def fanAuto()
{
	fanOn(settings.defaultFanSpeed)
}
def fanCirculate()
{
	fanOn(settings.defaultFanSpeed)
}

def dry()
{
	logDebug "Executing 'dry'"
	
	// Get the current state of the AC
	def currentMode = device.currentValue("thermostatMode")
	def currentState = device.currentValue("state")
	def targetMode = "dry"
	
	if((currentMode != targetMode) || (currentState == "off"))
	{
		// Send a command to switch mode of the AC - this will also turn it on if it is already off.
		def acRef = getAcRef()
		
		// API Example - /setAircon?json={"ac1":{"info":{"state":"on","mode":"dry"}}}
			
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"state\":\"on\",\"mode\":\"dry\"}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Switching Mode to dry")
		
		logDebug "Command 'dry' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])			
		runIn(6, 'testMode', [data:targetMode])
	}
}

def setCoolingSetpoint(Number newTemp)
{
	// API Details  - setTemp - Set temperature of the zone (min 16 - max 32) - only valid when Zone type > 0.

	def currentSetPoint = device.currentValue("coolingSetpoint")
	def zoneRef = "z" + ((device.currentValue("thermostatZone")).toString()).padLeft(2,"0")
	
	logDebug "Executing 'setCoolingSetpoint' on Zone: ${zoneRef} to ${newTemp}"
	
	if(currentSetPoint != newTemp && newTemp >= 16 && newTemp <= 32)
	{
		// as the API expects whole numbers - round the value		
			
		double newTempDouble = Double.parseDouble("${newTemp}")
		newTempRoundedDouble = Math.round(newTempDouble)
			
		int newTempValue = Integer.parseInt("${newTempRoundedDouble}")
		
		// As per API format of HTTP GET is - /setAircon?json={ac1:{"zones":{"z02":{"setTemp":24}}}
		def acRef = getAcRef()
		
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"setTemp\":\"${newTempValue}\"}}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"	
		
		sendEvent(name: "hubactionMode", value: "Setting Cool point to ${newTempValue}")
		
		logDebug "Command 'setCoolingSetpoint' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
	}
}
def setHeatingSetpoint(Number newTemp)
{
	// API Details  - setTemp - Set temperature of the zone (min 16 - max 32) - only valid when Zone type > 0.

	def currentSetPoint = device.currentValue("heatingSetpoint")
	def zoneRef = "z" + ((device.currentValue("thermostatZone")).toString()).padLeft(2,"0")
	
	logDebug "Executing 'setHeatingSetpoint' on Zone: ${zoneRef} to ${newTemp}"
		
	if(currentSetPoint != newTemp && newTemp >= 16 && newTemp <= 32)
	{
		// as the API expects whole numbers - round the value		
			
		double newTempDouble = Double.parseDouble("${newTemp}")
		newTempRoundedDouble = Math.round(newTempDouble)
			
		int newTempValue = Integer.parseInt("${newTempRoundedDouble}")
		
		// As per API format of HTTP GET is - /setAircon?json={ac1:{"zones":{"z02":{"setTemp":24}}}
		def acRef = getAcRef()
		
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"setTemp\":\"${newTempValue}\"}}}}","UTF-8")			
		def apiCommandwithQuery = "/setAircon?${httpQuery}"	
		
		sendEvent(name: "hubactionMode", value: "Setting Heating point to ${newTemp}")
		
		logDebug "Command 'setHeatingSetpoint' : Calling API : ${apiCommandwithQuery}"
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
	}
}
def setThermostatFanMode(String newFanMode)
{
	logDebug "Executing 'setThermostatFanMode'"
	logDebug "Current Fan Speed: ${device.currentValue("thermostatFanMode")} - New Fan Speed: ${newFanMode}"
	
	// the dashboard commands present leading spaces even when the supportedThermostatFanModes is set without leading spaces.  
	def newFanModeValue = (newFanMode.replace(' ','')).toLowerCase()
	
	if(device.currentValue("thermostatFanMode") != newFanModeValue)
	{
		def acRef = getAcRef()
		def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"fan\":\"", "UTF-8") + newFanModeValue + URLEncoder.encode("\"}}}","UTF-8")	
		def apiCommandwithQuery = "/setAircon?${httpQuery}"		
		
		sendEvent(name: "hubactionMode", value: "Setting Fan Mode to ${newFanModeValue}")
		
		logDebug "Command 'setThermostatFanMode' : calling API : ${apiCommandwithQuery}"		
		runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
	}
}

def setThermostatMode(String newThermostatMode){
	logDebug "Executing 'setThermostatMode' to Mode '${newThermostatMode}'"
	
	switch((newThermostatMode.toLowerCase())) {
		case "off":
			off()
			break
		case "fan":
			fanOn()					
			break
		case "cool":
			cool()
			break
		case "heat":
			heat()
			break
		case "dry":
			dry()
			break
	}
}


def setMyZone(Number newMyZoneNumber)
{
	logDebug "Executing 'setMyZone' to Zone '${newMyZoneNumber}'"
	
	// Sets MyZone to a new zone
	// As per API format of HTTP GET is  - /setAircon?json={ac1:{"info":{"myZone":"2"}}}
	
	def acRef = getAcRef()
	def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"info\":{\"myZone\":", "UTF-8") + newMyZoneNumber + URLEncoder.encode("}}}","UTF-8")	
	def apiCommandwithQuery = "/setAircon?${httpQuery}"
	
	sendEvent(name: "hubactionMode", value: "Setting myZone to ${newMyZoneNumber}")
		
	logDebug "Command 'setHeatingSetpoint' : Calling API : ${apiCommandwithQuery}"
	runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
}


// Generic Private Functions -------

private getHostAddress() {
    def ip = settings.ipAddress
    def port = settings.ipPort
    return ip + ":" + port
}


private setMyAirAPI(def json) {
    def ip = settings.ipAddress
    def port = settings.ipPort
	def DeviceIpAndPort = "${ip}:${port}"
	def url = "http://" + getHostAddress() + "/setAircon?json%3F" + json
	logDebug "Executing HTTPGet on ${url}"

	try {
		httpGet(url){ resp -> 
            logDebug "HTTP Response : " + resp.getData()
            def respValues = new JsonSlurper().parseText(resp.data.toString().trim())
			}
	}
	catch(Exception e) {
    	 logDebug "error occured calling httpget ${e}"
    }
}


private getMyAirAPI(def apiCommand) {
    def ip = settings.ipAddress
    def port = settings.ipPort
	def DeviceIpAndPort = "${ip}:${port}"
	logDebug "Executing 'getMyAirAPI()' on " + getHostAddress() + apiCommand

	sendEvent(name: "hubactionMode", value: "Calling API")
	
		def hubAction = new hubitat.device.HubAction(
			method: "GET",
			path: apiCommand,
			headers: [Host:getHostAddress()]
		)
 	return hubAction
}

// Functions called by Child Devices

def setZoneTemperaturePoint(Number newTemp, Number zoneNumber)
{
	// API Details  - setTemp - Set temperature of the zone (min 16 - max 32) - only valid when Zone type > 0.

	def currentSetPoint = device.currentValue("coolingSetpoint")
	def zoneRef = "z" + ((zoneNumber).toString()).padLeft(2,"0")
	
	logDebug "Executing 'setZoneTemperaturePoint' on Zone: ${zoneRef} to ${newTemp}"
	
	if(!state.myZones?.contains(zoneRef))
	{
		logWarn "Zone: ${zoneRef} does not support temperature set points"
	}
	else
	{	
		if(newTemp >= 16 && newTemp <= 32)
		{
			// as the API expects whole numbers - round the value		
				
			double newTempDouble = Double.parseDouble("${newTemp}")
			newTempRoundedDouble = Math.round(newTempDouble)
				
			int newTempValue = Integer.parseInt("${newTempRoundedDouble}")
			
			// As per API format of HTTP GET is - /setAircon?json={ac1:{"zones":{"z02":{"setTemp":24}}}
			
			def acRef = getAcRef()
			
			def httpQuery = "json=" + URLEncoder.encode("{\"${acRef}\":{\"zones\":{\"${zoneRef}\":{\"setTemp\":\"${newTempValue}\"}}}}","UTF-8")			
			def apiCommandwithQuery = "/setAircon?${httpQuery}"	
			
			sendEvent(name: "hubactionMode", value: "Setting ${zoneRef} Temperature point to ${newTempValue}")
			
			logDebug "Command 'setZoneTemperaturePoint' : Calling API : ${apiCommandwithQuery}"
			runIn(0, 'getMyAirAPI', [data:apiCommandwithQuery])
			
			// runIn(3, 'refresh')
		}
	}
}


// Utility Functions -------
def getMyZoneNumber(){
	def myZoneNumber = device.currentValue("thermostatZone")
	return myZoneNumber
}

def getFanSpeed(){
	def fanSpeed = device.currentValue("thermostatFanMode")
	return fanSpeed
}

def getThermostatMode(){
	def thermostatMode = device.currentValue("thermostatMode")
	return thermostatMode
}

def getAcRef(){
	def acRef = state.acRef
	return acRef
}

def testMode(String mode)
{
	def currentThermostatMode = device.currentValue("thermostatMode")
	

	if((device.currentValue("thermostatMode") == mode) && (device.currentValue("state") == "on"))
	{		
		logInfo "Successfully set mode to '${mode}'"
	}
	else
	{	
		logDebug "thermostatMode: '${device.currentValue("thermostatMode")}'"
		logDebug "state: '${device.currentValue("state")}'"
		logWarn "Failed to change mode to '${mode}'"
	}
}

def testOn()
{
	if((device.currentValue("thermostatMode") != "off") && (device.currentValue("state") == "on"))
	{		
		logInfo "MyAir System is turned On"
	}
	else
	{
		logDebug "thermostatMode: '${device.currentValue("thermostatMode")}'"
		logDebug "state: '${device.currentValue("state")}'"
		logWarn "MyAir System Failed to turn On"
	}
}

def testOff()
{
	if((device.currentValue("thermostatMode") == "off") && (device.currentValue("state") == "off"))
	{		
		logInfo "MyAir System is turned Off "
	}
	else
	{
		logDebug "thermostatMode: '${device.currentValue("thermostatMode")}'"
		logDebug "state: '${device.currentValue("state")}'"
		logWarn "MyAir System Failed to turn Off"
	}
}


private startScheduledRefresh() {
    logDebug "startScheduledRefresh()"
    // Get minutes from settings
    def minutes = settings.refreshInterval?.toInteger()
    if (!minutes) {
        log.warn "Using default refresh interval: 10"
        minutes = 10
    }
    logDebug "Scheduling polling task for every '${minutes}' minutes"
    if (minutes == 1){
        runEvery1Minute(refresh)
    } else {
        "runEvery${minutes}Minutes"(refresh)
    }
}


def updateChildDevice(String zoneRef, Map zoneDetail)
{
	// Function called for each zone to update/refresh 
	logDebug "updateChildDevice ${zoneRef} - ${zoneDetail.name}"
	
	//Try to find existing child device
    def child = getChild(zoneRef)
	
	// Get the Zone type
	// 0 - Standard Zone without a Temperature Sensor
	// 1 - MyZone Zone with Temperature Sensor
	def zoneType = zoneDetail.type
	
	// Get the rest of the Zone Detail
	def zoneNumber = zoneDetail.number
	def zoneName = zoneDetail.name
	def zoneState = zoneDetail.state
	def zoneMeasuredTemp = zoneDetail.measuredTemp
	def zoneSetTemp = zoneDetail.setTemp
	def zoneValue = zoneDetail.value
	
	//If child does not exist, create it
    if(child == null) {
        if (zoneRef != null) {
            logDebug "child with ${zoneRef} does not exist."
			if (zoneType == 1)
			{	
				def childType = "MyAir MyZone" 
				createChildDevice(zoneRef, zoneName, childType)
			}
			else
			{
				def childType = "MyAir Zone" 
				createChildDevice(zoneRef, zoneName, childType)
			}
			
            child = getChild(zoneRef)

        } 
        else {
            log.error "Cannot create child device for ${zoneRef} due to missing 'zoneRef'"
        }
    } 
    else {
        //log.trace "child with zoneRef=${zoneRef} exists already."
    }
	
	if(child != null)
	{
		// Call the Child to Update from the Parent Data
		child.updateFromParent(zoneDetail)
	}			
}

private def getChild(String zoneRef)
{
    logDebug "Searching for child device with network id: ${device.deviceNetworkId}|${zoneRef}"
    def result = null
    try {
        childDevices.each{ it ->
            // logDebug "child: ${it.deviceNetworkId}"
            if(it.deviceNetworkId == "${device.deviceNetworkId}|${zoneRef}")
            {
                result = it;
            }
        }
		// logDebug "Found Child Device: ${result}"
        return result;
    } 
    catch(e) {
        log.error "Failed to find child without exception: ${e}";
        return null;
    }
}

private void createChildDevice(String zoneRef, String zoneName, String type) {
    log.trace "Attempting to create child with zoneRef = ${zoneRef}, zoneName = ${zoneName}, type = ${type}"
    
    try {
        addChildDevice("${type}", "${device.deviceNetworkId}|${zoneRef}",
            [label: "${device.displayName}-${zoneRef}", 
             isComponent: false, name: "MyAir ${zoneName}"])
        log.trace "Created child device with network id: ${device.deviceNetworkId}|${zoneRef}"
    } 
    catch(e) {
        log.error "Failed to create child device with error = ${e}"
    }
}

Boolean isNewMyZone(zoneRef) {
  return !state.myZones?.contains(zoneRef)
}

// Logging

def logDebug (message) { if (debugLogging) log.debug (message) }
def logInfo  (message) { log.info (message) }
def logWarn  (message) { log.warn (message) }

