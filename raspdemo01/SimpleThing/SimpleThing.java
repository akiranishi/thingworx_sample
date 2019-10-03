import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.thingworx.communications.client.ConnectedThingClient;
import com.thingworx.communications.client.things.VirtualThing;
import com.thingworx.metadata.PropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinition;
import com.thingworx.metadata.annotations.ThingworxPropertyDefinitions;
import com.thingworx.metadata.annotations.ThingworxServiceDefinition;
import com.thingworx.metadata.annotations.ThingworxServiceParameter;
import com.thingworx.metadata.annotations.ThingworxServiceResult;
import com.thingworx.types.primitives.IPrimitiveType;

/*
*Modified by Akira Nishi (anishi@ptc.com) in 2018/10/05
Copyright (c) 2018 PTC Inc. */


@ThingworxPropertyDefinitions(properties = {

        // This property is setup for collecting time series data. Each value
        // that is collected will be pushed to the platfrom from within the
        // processScanRequest() method.

        @ThingworxPropertyDefinition(name = "Prop_Humidity", description = "A Current humidity value from a AM2302 sensor.",
                baseType = "NUMBER",
                aspects = { "dataChangeType:ALWAYS", "dataChangeThreshold:0", "cacheTime:0",
                        "isPersistent:FALSE", "isReadOnly:TRUE", "pushType:ALWAYS",
                        "isFolded:FALSE", "defaultValue:0" }),
        
        @ThingworxPropertyDefinition(name = "Prop_Temperature", description = "A Current temperature and humidity value from a DHT22 sensor.",
                baseType = "NUMBER",
                aspects = { "dataChangeType:ALWAYS", "dataChangeThreshold:0", "cacheTime:0",
                        "isPersistent:FALSE", "isReadOnly:TRUE", "pushType:VALUE",
                        "isFolded:FALSE", "defaultValue:0" }),
        
        @ThingworxPropertyDefinition(
                name = "Prop_LED_number", description = "A property for operating LED.",
                baseType = "NUMBER",
                aspects = { "dataChangeType:NEVER", "dataChangeThreshold:0", "cacheTime:-1",
                        "isPersistent:TRUE", "isReadOnly:FALSE", "pushType:NEVER",
                        "isFolded:FALSE", "defaultValue:0" })
        }
)

/**
 * A very basic VirtualThing with two properties and a service implementation. It also implements
 * processScanRequest to handle periodic actions.
 */
public class SimpleThing extends VirtualThing {

    private static final Logger LOG = LoggerFactory.getLogger(SimpleThing.class);
    // private static final String PI_HOME = "/home/pi/Thingworx/demo01/";
    private static final String PI_HOME = "./";
    
    public static final String  PATH_TO_TEMP_HUMID_COMMAND  = "AdafruitDHT.py";
    public static final String CMD_LED_ON  = "writeLED.py Y";
    public static final String CMD_LED_OFF = "writeLED.py N";


    private final String simulated;
    
    /**
     * A custom constructor. We implement this so we can call initializeFromAnnotations, which
     * processes all of the VirtualThing's annotations and applies them to the object.
     * 
     * @param name The name of the thing.
     * @param description A description of the thing.
     * @param client The client that this thing is associated with.
     */
	public SimpleThing(String name, String description, ConnectedThingClient client, String simulated)
            throws Exception {

        super(name, description, client);
        this.initializeFromAnnotations();
        this.simulated = simulated;

        try {
            setDefaultPropertyValue("Prop_Temperature");
            setDefaultPropertyValue("Prop_Humidity");
            setDefaultPropertyValue("Prop_LED_number");
            setLEDStatus((double)getProperty("Prop_LED_number").getPropertyDefinition().getDefaultValue().getValue());
            
        } catch (Exception e) {
            LOG.warn("Could not ser default value for SetPoint");
        }
    }

    /**
     * This method provides a common interface amongst VirtualThings for processing periodic
     * requests. It is an opportunity to access data sources, update property values, push new
     * values to the server, and take other actions.
     */
    @Override
    public void processScanRequest() {

        // We'll use this to generate a random temperature and humidity value.
        // On an actual system you would access a sensor or some other data source.

        try {

            // Here we set the thing's internal property values to the new values
            // that we accessed above. This does not update the server. It simply
            // sets the new property value in memory.

            Double currentTemperature = getTemperature();
            Double currentHumidity = getHumidity();
            LOG.debug("Prop_Temperature" + "=" + currentTemperature);
            LOG.debug("Prop_Humidity" + "=" + currentHumidity);
            setProperty("Prop_Temperature", currentTemperature);
            setProperty("Prop_Humidity", currentHumidity);

            
            // This call evaluates all properties and determines if they should be pushed
            // to the server, based on their pushType aspect. A pushType of ALWAYS means the
            // property will always be sent to the server when this method is called. A
            // setting of VALUE means it will be pushed if has changed since the last
            // push. A setting of NEVER means it will never be pushed.
            //
            // Our Temperature property is set to ALWAYS, so its value will be pushed
            // every time processScanRequest is called. This allows the platform to get
            // periodic updates and store the time series data. Humidity is set to
            // VALUE, so it will only be pushed if it changed.
            this.updateSubscribedProperties(10000);

        } catch (Exception e) {
            // This will occur if we provide an unknown property name. We'll ignore
            // the exception in this case and just log it.
            LOG.error("Exception occurred while updating properties.", e);
        }
    }

    /**
     * This is where we handle property writes from the server. The only property we want to update
     * is the SetPoint. Temperature and Humidity write requests should be rejected, since their
     * values are controlled from within this application.
     * 
     * @see VirtualThing#processPropertyWrite(PropertyDefinition, IPrimitiveType)
     */
    @Override
    public void processPropertyWrite(PropertyDefinition property,
            @SuppressWarnings("rawtypes") IPrimitiveType value) throws Exception {

        // Find out which property is being updated
        String propName = property.getName();

        if (!"Prop_LED_number".equals(propName)) {
            throw new Exception("The property " + propName + " is read only on the simple device.");
        }
        this.setPropertyValue(propName, value);
        setLEDStatus((double)value.getValue());
    }

    // The following annotation allows you to make a method available to the
    // ThingWorx Server for remote invocation. The annotation includes the
    // name of the server, the name and base types for its parameters, and
    // the base type of its result.
    @ThingworxServiceDefinition(name = "conv2F", description = "conversion to Fahrenheit")
    @ThingworxServiceResult(name = "result", description = "Fahrenheit Temperature",
            baseType = "NUMBER")
    public Double conv2F(
            @ThingworxServiceParameter(name = "TempC",
                    description = "Celsius Temperature",
                    baseType = "NUMBER") Double TempC)
            throws Exception {

        LOG.info("Convert the TempC to TempF {} ", TempC);
        return (TempC * (9/5) + 32);
    }
    
    /**
     * Sets the current value of a property to the default value provided in its annotation.
     * @param propertyName
     * @throws Exception
     */
    protected void setDefaultPropertyValue(String propertyName) throws Exception {
        setProperty(propertyName, getProperty(propertyName).getPropertyDefinition().getDefaultValue().getValue());
    }

    private void setLEDStatus(double value) {
        int status = 0;
//        double value = 0.0;
//      value = (double)getProperty("Prop_LED_number").getValue().getValue();
        status = (int)value;
        
        
        if (simulated!=null&&simulated.equals("simulated")) {
            if (status == 0) {
                System.out.println("LED turn OFF\n");
            }else if (status==9){
                System.out.println("LED turn ON\n");
            }
        } else {
            if (status == 0) {
                getCommandResults("sudo python " + PI_HOME + CMD_LED_OFF);
            }else if (status==9){
                getCommandResults("sudo python " + PI_HOME + CMD_LED_ON);
            }
        }
        return;
    }

    private Double getTemperature() {
        String consoleOutput;
        if (simulated!=null&&simulated.equals("simulated")) {
            consoleOutput = getSimulatedConsoleOutput();
        } else {
            consoleOutput = getCommandResults("sudo python " + PI_HOME + PATH_TO_TEMP_HUMID_COMMAND + " 2302 4");
        }
        Double temperature = parseTemperatureFromString(consoleOutput);

        return temperature;

    }

    private Double getHumidity() {
        String consoleOutput;
        if (simulated!=null&&simulated.equals("simulated")) {
            consoleOutput = getSimulatedConsoleOutput();
        } else {
            consoleOutput = getCommandResults("sudo python " + PI_HOME + PATH_TO_TEMP_HUMID_COMMAND + " 2302 4");
        }
        Double humidity = parseHumidityFromString(consoleOutput);

        return humidity;

    }

    Double parseTemperatureFromString(String consoleOutput) {
        String[] tempHumidParts = consoleOutput.split(" +");
        String[] tempPart = tempHumidParts[0].split("=");
        String theTemp="0";
        if(tempPart.length>0) {
            theTemp = tempPart[1];
            theTemp = theTemp.replace("*", "");
        }
        return Double.parseDouble(theTemp);
    }

    Double parseHumidityFromString(String consoleOutput) {
        String[] tempHumidParts = consoleOutput.split(" +");
        String[] humidityPart = tempHumidParts[1].split("=");
        String theHumidity="0";
        if(humidityPart.length>0) {
            theHumidity = humidityPart[1];
            theHumidity = theHumidity.replace("%", "");
        }
        return Double.parseDouble(theHumidity);
    } 

    /**
     * Run a shell command and get the results back as a string.
     *
     * @param commandLine Note: Assume there is no existing PATH environment variable.
     * @return the console output of the command.
     */
    protected String getCommandResults(String commandLine) {
        String s = null;
        StringBuffer retBuff = new StringBuffer();
        try {

            // using the Runtime exec method:
            Process p = Runtime.getRuntime().exec(commandLine);

            BufferedReader stdInput = new BufferedReader(new
                    InputStreamReader(p.getInputStream()));

            BufferedReader stdError = new BufferedReader(new
                    InputStreamReader(p.getErrorStream()));

            // read the output from the command
            System.out.println("Here is the standard output of the command:\n");
            while ((s = stdInput.readLine()) != null) {
                System.out.println("stdout>" + s);
                retBuff.append(s);
            }

            // read any errors from the attempted command
            System.out.println("Here is the standard error of the command (if any):\n");
            while ((s = stdError.readLine()) != null) {
                System.out.println("stderr>" + s);
                retBuff.append(s);
            }
        } catch (IOException e) {
            LOG.error("An exception occurred while running an external script. ", e);
        }
        return retBuff.toString();
    }

    private String getSimulatedConsoleOutput() {

        float randHumid = (10 + (int) (Math.random() * 1000))/10;
        float randTemp = (200 + (int) (Math.random() * 2000))/10;
        return String.format("Temp=%.1f* Humidity=%.1f%%", randTemp,randHumid);
    }

}


    
