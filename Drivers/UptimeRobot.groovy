// My first attempt at using Grovy to write a driver for Hubitat. Parts of this were shamelessly borrowed from other drivers. Thank-you to those authors, and sorry I forgot to make a note of where each idea came from!

// v1.0 - Added version number and removed option to not show all preferences as there are only a few.

metadata {
    definition (
    name: "Uptime Robot",
    namespace: "org.kram",
    author: "Mark Turner <mark@kram.org>" )
    {
        capability "Refresh"
    }
    preferences{
		section{
            input( type: "string", name: "APIKey", title: "<b>API key (ideally the read-only one)</b>", required: true )
            input( type: "enum", name: "LogType", title: "<b>Enable Logging?</b>", required: false, defaultValue: "2", multiple: false, options: [ [ "1" : "None" ], [ "2" : "Info" ], [ "3" : "Debug" ], [ "4" : "Trace" ] ] )
		}
	}

}

def refresh() {
    log.debug("Uptime Robot: Polling...")
    ( sendSyncCmd() )
    runIn(60, "refresh")
}

private sendSyncCmd(){
    def Params = [
        uri: "https://api.uptimerobot.com/v2/getMonitors?format=json",
        requestContentType: 'application/json',
        body: ["api_key": "${APIKey}"],
        timeout: 5
    ]
	asynchttpPost( "handleResponse", Params)
}

def handleResponse( resp, data ){
	switch( resp.getStatus() ){
		case 200:
        
            //log.debug "resp.data = " + resp.data
        
            // Expecting a JSON response
			json = parseJson( resp.data )
        
            // Pick out stat and make sure it's "ok"
            def stat = json.stat
            if (stat == "ok") {
                // Update state
                if (ourStatus != "connected") {
                    ourStatus = "connected"
                }
                // Iterate through monitors
                json.monitors?.each { monitor ->
                    //log.debug "monitor = " + monitor
                    def monitorId = monitor.id
                    def monitorName = monitor.friendly_name
                    def monitorStatus = monitor.status
                    
                    //log.debug "Found ${monitorId}, ${monitorName}, ${monitorStatus}"
                    
                    // Does child device exist?
                    def childDevice = findChildDevice(monitorId, "Contact Sensor")
                    if (childDevice == null) {
                        createSensor(monitorId, monitorName, "Contact Sensor")
                        childDevice = findChildDevice(monitorId, "Contact Sensor")
                    }
                    if (childDevice == null) {
                        log.debug "Uptime Robot: Failed to find or create child device for " + monitorName
                    } else {
                        // Got a child device, so see if status needs updating
                        def monitorContactStatus
                        def monitorUpOrDown
                        switch (monitorStatus) {
                            default:
                                monitorContactStatus = "open"
                                monitorUpOrDown = "down"
                                break;
                            case "2":
                                monitorContactStatus = "closed"
                                monitorUpOrDown = "up"
                                break;
                        }
                        def currentValue = childDevice.currentValue("contact")
                        if (currentValue == monitorContactStatus) {
                            log.debug "Uptime Robot: ${monitorName} is still ${monitorContactStatus} (${monitorUpOrDown})"
                        } else {
                            log.debug "Uptime Robot: ${monitorName} changed from ${currentValue} to ${monitorContactStatus} (${monitorUpOrDown})"
                            childDevice.sendEvent(name: "contact", value: monitorContactStatus)
                        }
                        
                    }
                }
            } else {
                log.debug "Uptime Robot: Giving up because stat = " + stat
            }
			break
		default:
			log.debug "Uptime Robot: Failed to poll ${ resp.status }"
            if (ourStatus != "not connected") {
                ourStatus = "not connected"
            }
			break
	}
}

def findChildDevice(sensorId, sensorType) {
	getChildDevices()?.find { it.deviceNetworkId == deriveSensorDNI(sensorId, sensorType)}
}

def deriveSensorDNI(sensorId, sensorType) {
    return "${device.deviceNetworkId}-id${sensorId}-type${sensorType}"
}

def createSensor(sensorId, sensorName, sensorType) {
    log.debug("Uptime Robot: Creating sensor: ${sensorId}, ${sensorName}, ${sensorType}")
    def childDevice = findChildDevice(sensorId, sensorType)
    if (childDevice == null) {
        childDevice = addChildDevice("hubitat", "Generic Component ${sensorType}", deriveSensorDNI(sensorId, sensorType), [label: "${device.displayName} - ${sensorName}", isComponent: false])
    } else {
      log.debug("Uptime Robot: Child device ${childDevice.deviceNetworkId} already exists")
    }
}




