package tempmonitor;

import static org.unitsofmeasurement.impl.util.SI.CELSIUS;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.SerialPort;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.measure.Quantity;
import javax.measure.quantity.Temperature;
import org.unitsofmeasurement.impl.model.QuantityFactory;

public class SerialComm {
    public static final String    PI_PORT       = "/dev/ttyUSB0";
    private static final int      TIME_OUT      = 5000;
    private static final int      DATA_RATE     = 9600;
    private static final String   TEMP_PROPERTY = "TEMP";
    private PropertyChangeSupport propertyChangeSupport;
    private CommPort              commPort;
    private InputStream           inputStream;
    private Quantity<Temperature> temperature;
    private boolean               running;
    private final QuantityFactory<Temperature> tempFactory = 
                                 QuantityFactory.getInstance(Temperature.class);

    // ******************** Constructor ***************************************
    public SerialComm() {
        super();
        propertyChangeSupport = new PropertyChangeSupport(this);
        temperature           = null;
        running               = true;
    }


    // ******************** Methods *******************************************
    void connect(final String PORT_NAME) throws gnu.io.NoSuchPortException,
                                                gnu.io.PortInUseException,
                                                gnu.io.UnsupportedCommOperationException,
                                                java.io.IOException {
        CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(PORT_NAME);
        if (portIdentifier.isCurrentlyOwned()) {
            System.out.println("Error: Port is currently in use");
        } else {
            commPort = portIdentifier.open(getClass().getName(), TIME_OUT);

            if (commPort instanceof SerialPort) {
                SerialPort serialPort = (SerialPort) commPort;
                serialPort.setSerialPortParams(DATA_RATE,
                                               SerialPort.DATABITS_8,
                                               SerialPort.STOPBITS_1,
                                               SerialPort.PARITY_NONE);

                serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                serialPort.setDTR(true);

                // Enable blocking io
                serialPort.disableReceiveTimeout();
                serialPort.enableReceiveThreshold(1);

                inputStream = serialPort.getInputStream();

                Thread serialReaderTask = createSerialReaderThread();
                serialReaderTask.start();

            } else {
                System.out.println("Sorry, only support for serial ports.");
            }
        }
    }

    public void closePort() {
        if (commPort != null) {
            commPort.close();
        }
    }

    public Quantity<Temperature> getTemperature() {
        return temperature;
    }
    
    private void setTemperature(final Quantity<Temperature> temp) {
        final Quantity<Temperature> oldTemp = temperature;
        temperature    = temp;
        propertyChangeSupport.firePropertyChange(TEMP_PROPERTY, oldTemp, temp);
    }

    private Thread createSerialReaderThread() {
        Thread serialReaderTask = new Thread(new Runnable() {
            @Override public void run() {
                BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                while (running) {
                    try {
                        while((br.ready()) && (line = br.readLine()) != null) {
                            if (line.length() > 6 && line.endsWith("C")) {
                                try {
                                    double value = Double.parseDouble(line.substring(1, 6));
                                    setTemperature(tempFactory.create(value, CELSIUS));
                                } catch (NumberFormatException exception) {
                                    // TODO react here
                                }
                            }
                        }
                    } catch (IOException exception) {
                        System.out.println("Error reading data from serial port: " + exception);
                    }
                }
            }
        });
        return serialReaderTask;
    }

    public void addPropertyChangeListener(final PropertyChangeListener LISTENER) {
        propertyChangeSupport.addPropertyChangeListener(LISTENER);
    }

    public void removePropertyChangeListener(final PropertyChangeListener LISTENER) {
        propertyChangeSupport.removePropertyChangeListener(LISTENER);
    }
}
