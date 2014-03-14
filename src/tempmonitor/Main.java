package tempmonitor;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import javax.measure.Quantity;
import javax.measure.quantity.Temperature;
import org.jivesoftware.smack.XMPPException;


/**
 * @author hansolo
 */
public class Main {
    private String      senderName;
    private String      senderPassword;
    private String      server;
    private String      resource;
    private int         port;
    private String      receiverJid;
    private String      receiverName;
    private String      receiverBuddyName;
    private SerialComm  serialComm;
    private XmppManager xmppManager;


    // ******************** Methods *******************************************
    private void initializeXmppConnection() {
        try {
            xmppManager.init();
            xmppManager.performLogin(senderName, senderPassword);
            xmppManager.setStatus(true, "Pi is online");
            xmppManager.createEntry(receiverName, receiverBuddyName);
            xmppManager.sendMessage("Hey " + receiverBuddyName + ", the Pi is now online.", receiverJid);

            // We initialize the serial port here because of timing reasons when autostart after autologin
            initializeSerialConnection();
        } catch (XMPPException exception) {
            System.out.println("Error connecting to XMPP: " + exception);
        } catch (Exception exception) {
            System.out.println("Error connecting to XMPP: " + exception);
        }
    }

    private void initializeSerialConnection() {
        try {
            serialComm.connect(SerialComm.PI_PORT);
            serialComm.addPropertyChangeListener(new PropertyChangeListener() {
            @Override public void propertyChange(final PropertyChangeEvent EVENT) {
                sendMessage((Quantity<Temperature>) EVENT.getNewValue(), receiverJid);
            }
        });
        } catch (NoSuchPortException | PortInUseException | UnsupportedCommOperationException | IOException exception) {
            System.out.println("Error connecting to serial port: " + exception);
        }
    }

    private void sendMessage(final Quantity<Temperature> temperature, final String JID) {
        if (xmppManager == null) return;
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    xmppManager.sendData(temperature, JID);
                } catch (XMPPException exception) {
                    System.out.println("Error sending data via xmpp: " + exception);
                }
            }
        }).start();
    }

    public void answerTempRequest(final String JID) {
        sendMessage(serialComm.getTemperature(), JID);
    }

    private Properties readProperties() {
        Properties properties = new Properties();
        try {
            properties.load(new FileInputStream("config.properties"));
        } catch (IOException exception) {
            properties.put("sender_name", "XMPP NAME FOR PI");
            properties.put("sender_password", "PASSWORD");
            properties.put("sender_server", "XMPP SERVER");
            properties.put("sender_resource", "YOUR_RESOURCE_HERE");
            properties.put("port", "5222");
            properties.put("receiver_jid", "RECEIVER JID");
            properties.put("receiver_name", "RECEIVER NAME");
            properties.put("receiver_buddy_name", "RECEIVER BUDDY NAME");
            try {
                properties.store(new FileOutputStream("config.properties"), null);
                System.out.println("Please adjust the settings in the config.properties file and restart.");
                System.exit(0);
            } catch (IOException ex) {
                System.out.println("Error writing properties file.");
            }
        }
        return properties;
    }

    public void init() {
        Properties properties = readProperties();
        senderName        = properties.getProperty("sender_name");
        senderPassword    = properties.getProperty("sender_password");
        server            = properties.getProperty("sender_server");
        resource          = properties.getProperty("sender_resource");
        port              = Integer.parseInt(properties.getProperty("port"));
        receiverJid       = properties.getProperty("receiver_jid");
        receiverName      = properties.getProperty("receiver_name");
        receiverBuddyName = properties.getProperty("receiver_buddy_name");

        serialComm  = new SerialComm();
        xmppManager = new XmppManager(this, server, resource, port);

        initializeXmppConnection();
    }

    public static void main(String[] args) {
        System.out.println("Temperature monitor started");
        Main app = new Main();
        app.init();
    }
}
