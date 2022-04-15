# peteGGG's Hubitat Applications and Drivers
Custom Hubitat Applications and Drivers for interacting with other smart home products

<h3>Drivers in Beta</h3>
<ul>
  <li>Advantage Air - MyAir Controller</li>
  <li>Advantage Air - MyAir Zone (Child to MyAir Controller)</li>
  <li>Advantage Air - MyAir MyZone (Child to MyAir Controller)</li>
</ul>

To install the drivers within your Hubitat Hub: 
<ol>
  <li>Expand out "Developer" section from the menu</li>
  <li>Select "Drivers Code"</li>
  <li>Create a New driver</li>
  <li>From this Github Repository under the Drivers Folder Either copy the text from each respective groovy file from this or use the import raw text using the Raw text URL.  </li>
  <li>Press Save</li>
  <li>Repeat steps 2-5 for each Driver</li>
</ol>

Within the Groovy Files, are Installation Instructions to perform creation of device.

<h3>Applications Under Development</h3>
<ul>
  <li>Advantage Air - MyAir Integration (discovery Application)</li>
  <li>Advantage Air - MyAir Integration Device (Child to MyAir Integration that Manages the Device Creation, IP Address and AC unit reference) </li>
</ul>


<hr>
<h3>Sample MyAir Dashboard</h3>
The following is a sample dashboard created with a My Air System that contains 3 "MyZone" Air Conditioning Zones and 4 Standard Air Conditioning Zones.  <BR><BR>The Custom drivers create a Controller Device, considered as the Master On/off and mode selector for the My Air System.  Each "MyZone" Air Conditioner Zone has the ability to be a separate Thermostat Zone but the Controller is the Master over these zones.

<img src="https://raw.githubusercontent.com/peteGGG/Hubitat/main/Screenshots/Sample%20MyAir%20Dashboard.png">
