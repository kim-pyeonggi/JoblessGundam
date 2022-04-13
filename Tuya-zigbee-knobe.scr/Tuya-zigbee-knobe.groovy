/**
 *  Copyright 2018 SmartThings
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
 */

import physicalgraph.zigbee.zcl.DataType

metadata {
	definition (name: "Tuya Smart Knob", namespace: "JoblessGundam", author: "JoblessGundam", ocfDeviceType: "x.com.st.d.remotecontroller") {
		capability "Actuator"
		capability "Color Temperature"
		capability "Battery"
		capability "Switch"
		capability "Button"
		capability "Switch Level"
		capability "Configuration"
		capability "Health Check"
        
        attribute "colorName", "string"
        

	    fingerprint profileId: "0104", inClusters: "0000 0001 0003 0004 0006 1000", outClusters: "0019 000A 0003 0004 0005 0006 0008 1000", manufacturer: "_TZ3000_4fhiwweb", model: "TS004F", deviceJoinName: "Tuya Smart Knob" 
		fingerprint profileId: "0104", manufacturer: "_TZ3000_4fjiwweb", model: "TS004F", deviceJoinName: "Tuya Smart Knob" // Tuya smart knob *
	}

	tiles(scale: 2) {
		multiAttributeTile(name:"switch", type: "lighting", width: 6, height: 4, canChangeIcon: true){
			tileAttribute ("device.switch", key: "PRIMARY_CONTROL") {
				attributeState "on", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "off", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
				attributeState "turningOn", label:'${name}', action:"switch.off", icon:"st.switches.light.on", backgroundColor:"#00A0DC", nextState:"turningOff"
				attributeState "turningOff", label:'${name}', action:"switch.on", icon:"st.switches.light.off", backgroundColor:"#ffffff", nextState:"turningOn"
			}
			tileAttribute ("device.level", key: "SLIDER_CONTROL") {
				attributeState "level", action:"switch level.setLevel"
			}
			tileAttribute ("device.battery", key: "SECONDARY_CONTROL") {
				attributeState "battery", label: 'battery ${currentValue}%', unit: "%"
			}
		}

		controlTile("colorTempSliderControl", "device.colorTemperature", "slider", width: 4, height: 2, inactiveLabel: false, range: "(2000..6500)") {
			state "colorTemperature", action: "color temperature.setColorTemperature"
		}
		valueTile("colorName", "device.colorName", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "colorName", label: '${currentValue}'
		}

		main "switch"
		details(["switch", "colorTempSliderControl", "colorName", "refresh"])
	}
}

// Globals
private getMOVE_TO_COLOR_TEMPERATURE_COMMAND() { 0x0A }

private getCOLOR_CONTROL_CLUSTER() { 0x0300 }

private getATTRIBUTE_COLOR_TEMPERATURE() { 0x0007 }

def getcolorTemperature() {2200}
def getSTEP() {5}
def getSTEP2() {100}
def getBATTERY_VOLTAGE_ATTR() { 0x0020 }
def getBATTERY_PERCENT_ATTR() { 0x0021 }

// Parse incoming device messages to generate events
def parse(String description) {
	log.debug "description is $description"

	def event = zigbee.getEvent(description)
	if (event) {
		if (event.name=="level" && event.value==0) {}
		else {
			if (event.name == "colorTemperature") {
				setGenericName(event.value)
			}
			sendEvent(event)
		}
	} else {
		def cluster = zigbee.parse(description)
		def descMap = zigbee.parseDescriptionAsMap(description)
		if (cluster && cluster.clusterId == 0x0006 && cluster.command == 0x07) {
			if (cluster.data[0] == 0x00) {
				//log.debug "ON/OFF REPORTING CONFIG RESPONSE: " + cluster
				sendEvent(name: "checkInterval", value: 60 * 12, displayed: false, data: [protocol: "zigbee", hubHardwareId: device.hub.hardwareID])
			} else {
				//log.warn "ON/OFF REPORTING CONFIG FAILED- error code:${cluster.data[0]}"
			}
		} else {
			//log.warn "DID NOT PARSE MESSAGE for description : $description"
			//log.debug "${cluster}"
		}
		if (descMap && descMap.clusterInt == 0x0300) {
			def currentcolorTemperature = device.currentValue("colorTemperature") as Integer ?: 2000         
            int delta2 = Integer.parseInt(descMap.data[1],16) / 11 * STEP2
            log.debug "move to ${descMap.data}"               
			if (descMap.commandInt == 0x4C) {
				if (descMap.data[0] == "01") {
					log.debug "pushed move up"
                   	def value = Math.min(currentcolorTemperature + delta2, 6500)
					sendEvent(name: "colorTemperature", value: value)
				} else if (descMap.data[0] == "03") {
					log.debug "pushed move down"
					def value = Math.max(currentcolorTemperature - delta2, 2000)
					sendEvent(name: "colorTemperature", value: value)
				}
                }
		
		} else if (descMap && descMap.clusterInt == 0x0008) {
        		def currentLevel = device.currentValue("level") as Integer ?: 0
                int delta = Integer.parseInt(descMap.data[1],16) / 12 * STEP
                log.debug "move to ${descMap.data}"
				if (descMap.data[0] == "00") {
                    def value = Math.min(currentLevel + delta, 100)
					sendEvent(name: "switch", value: "on")
					sendEvent(name: "level", value: value)
                    log.debug "move up"
                    
				} else if (descMap.data[0] == "01") {
					log.debug "move down"
					def value = Math.max(currentLevel - delta, 0)
                    sendEvent(name: "level", value: value)
					// don't change level if switch will be turning off
					if (value == 0) {
						sendEvent(name: "switch", value: "off")
					}
				}
		} else if (descMap && descMap.clusterInt == 0x0006) {
        	log.debug "pushed"
			sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], isStateChange: true)
		} else {
			log.warn "DID NOT PARSE MESSAGE for description : $description"
			log.debug "${descMap}"
		}
	}
}

def handleBatteryEvents(descMap) {
	def results = []

	if (descMap.value) {
		def rawValue = zigbee.convertHexToInt(descMap.value)
		def batteryValue = null

		if (rawValue == 0xFF) {
			// Log invalid readings to info for analytics and skip sending an event.
			// This would be a good thing to watch for and form some sort of device health alert if too many come in.
			log.info "Invalid battery reading returned"
		} else if (descMap.attrInt == BATTERY_VOLTAGE_ATTR && !isIkeaDimmer()) { // Ignore from IKEA Dimmer if it sends this since it is probably 0
			def minVolts = 2.1
			def maxVolts = 3.2
			def batteryValueVoltage = rawValue / 10

			batteryValue = Math.round(((batteryValueVoltage - minVolts) / (maxVolts - minVolts)) * 100)
		} else if (descMap.attrInt == BATTERY_PERCENT_ATTR) {
			// The IKEA dimmer is sending us full percents, but the spec tells us these are half percents, so account for this
			batteryValue = Math.round(rawValue / (isIkeaDimmer() ? 1 : 2))
		}

		if (batteryValue != null) {
			batteryValue = Math.min(100, Math.max(0, batteryValue))

			results << createEvent(name: "battery", value: batteryValue, unit: "%", descriptionText: "{{ device.displayName }} battery was {{ value }}%", translatable: true)
		}
	}

	return results
}



def off() {
	sendEvent(name: "switch", value: "off", isStateChange: true)
}

def on() {
	sendEvent(name: "switch", value: "on", isStateChange: true)
}

def refresh() {
	zigbee.onOffRefresh() +
			zigbee.levelRefresh() +
			zigbee.colorTemperatureRefresh() +
			zigbee.onOffConfig(0, 300) +
			zigbee.levelConfig()
}

def setLevel(value, rate = null) {
	if (value == 0) {
		sendEvent(name: "switch", value: "off")
		// OneApp expects a level event when the dimmer value is changed
		value = device.currentValue("level")
	} else {
		sendEvent(name: "switch", value: "on")
	}
	runIn(1, delayedSend, [data: createEvent(name: "level", value: value), overwrite: true])
}


def setColorTemperature(value) {
	value = value as Integer
	def tempInMired = Math.round(1000000 / value)
	def finalHex = zigbee.swapEndianHex(zigbee.convertToHexString(tempInMired, 4))

	List cmds = []
	cmds << zigbee.readAttribute(COLOR_CONTROL_CLUSTER, ATTRIBUTE_COLOR_TEMPERATURE)
	cmds
}

//Naming based on the wiki article here: http://en.wikipedia.org/wiki/Color_temperature
def setGenericName(value) {
	if (value != null) {
		def genericName = "White"
		if (value < 3300) {
			genericName = "Soft White"
		} else if (value < 4150) {
			genericName = "Moonlight"
		} else if (value <= 5000) {
			genericName = "Cool White"
		} else if (value >= 5000) {
			genericName = "Daylight"
		}
		sendEvent(name: "colorName", value: genericName)
	}
}

def delayedSend(data) {
	sendEvent(data)
}

def ping() {
	if (isCentraliteSwitch()) {
		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_VOLTAGE_ATTR)
	} else {
		zigbee.readAttribute(zigbee.POWER_CONFIGURATION_CLUSTER, BATTERY_PERCENT_ATTR)
	}
}

def installed() {
	sendEvent(name: "switch", value: "on", displayed: false)
	sendEvent(name: "level", value: 100, displayed: false)
	sendEvent(name: "button", value: "pushed", data: [buttonNumber: 1], displayed: false)
}


def configure() {
	sendEvent(name: "DeviceWatch-Enroll", value: [protocol: "zigbee", scheme:"untracked"].encodeAsJson(), displayed: false)
	//these are necessary to have the device report when its buttons are pushed
	zigbee.addBinding(zigbee.ONOFF_CLUSTER) + zigbee.addBinding(zigbee.LEVEL_CONTROL_CLUSTER) + zigbee.addBinding(0x0005)
}
