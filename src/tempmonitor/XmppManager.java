package tempmonitor;

import static org.unitsofmeasurement.impl.util.SI.SECOND;
import static org.unitsofmeasurement.impl.util.SIPrefix.MILLI;
import static org.unitsofmeasurement.impl.util.US.FAHRENHEIT;

import javax.measure.Measurement;
import javax.measure.Quantity;
import javax.measure.quantity.Temperature;
import javax.measure.quantity.Time;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManager;
import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.Roster;
import org.jivesoftware.smack.SASLAuthentication;
import org.jivesoftware.smack.SmackConfiguration;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.PacketFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.Presence;
import org.unitsofmeasurement.impl.model.QuantityFactory;


public class XmppManager {
    private static final QuantityFactory<Time>  TIME_FACTORY = 
            QuantityFactory.getInstance(Time.class);
    private static final Quantity<Time> PACKET_REPLY_TIMEOUT = 
            TIME_FACTORY.create(3000, MILLI(SECOND)); 
            // a bit of an overkill but a typesafe representation of 3s;-)
    
    private Main                    main;
    private String                  server;
    private String                  resource;
    private int                     port;
    private ConnectionConfiguration config;
    private XMPPConnection          connection;
    private ChatManager             chatManager;
    private MessageListener         messageListener;
    private PacketListener          packetListener;
    private PacketFilter            messageFilter;


    // ******************* Constructors ***************************************
    public XmppManager(final Main MAIN, final String SERVER, final String RESOURCE, final int PORT) {
        main     = MAIN;
        server   = SERVER;
        resource = RESOURCE;
        port     = PORT;
    }


    // ******************* Initialization *************************************
    public void init() throws XMPPException {
        SmackConfiguration.setPacketReplyTimeout(
                PACKET_REPLY_TIMEOUT.getValue().intValue());

        config = new ConnectionConfiguration(server, port);
        SASLAuthentication.supportSASLMechanism("PLAIN");
        config.setSecurityMode(ConnectionConfiguration.SecurityMode.disabled);

        connection = new XMPPConnection(config);
        connection.connect();

        System.out.println("Connected: " + connection.isConnected());

        chatManager     = connection.getChatManager();
        messageListener = new XmppMessageListener();
        packetListener  = new XmppPacketListener();
        messageFilter   = new PacketFilter() {
            @Override public boolean accept(Packet packet) {
                return Message.class.equals(packet.getClass());
            }
        };
    }


    // ******************* Methods ********************************************
    public void performLogin(final String USERNAME, final String PASSWORD) throws XMPPException {
        if (connection != null && connection.isConnected()) {
            connection.login(USERNAME, PASSWORD, resource);
            System.out.println("XMPP connection established as user " + USERNAME);
            setStatus(true, "Measuring data...");
            connection.getRoster().setSubscriptionMode(Roster.SubscriptionMode.accept_all);
            connection.addPacketListener(packetListener, messageFilter);
        } else {
            System.out.println("XMPP login failed...!");
        }
    }

    public void setStatus(final boolean AVAILABLE, final String STATUS) {
        Presence.Type type = AVAILABLE? Presence.Type.available: Presence.Type.unavailable;
        Presence presence  = new Presence(type, STATUS, 1, Presence.Mode.available);
        presence.setFrom(connection.getUser());
        connection.sendPacket(presence);
    }

    public boolean isConnected() {
        return connection.isConnected();
    }

    public void destroy() {
        if (connection!=null && connection.isConnected()) {
            connection.disconnect();
        }
    }

    public void sendData(Quantity<Temperature> temperature, String receiverJID) throws XMPPException {
        Message message = new Message();
        message.setProperty("celsius", temperature);
        final Measurement<Temperature, Number> fahrenheit = 
                         temperature.to(FAHRENHEIT);
        message.setProperty("fahrenheit", fahrenheit);
        message.setBody("Current temperature: \n" +
                         temperature + "\n" +
                         fahrenheit);
        sendMessage(message, receiverJID);
    }

    public void sendMessage(Message message, String receiverJID) throws XMPPException {
        if (connection.isConnected()) {
            Chat chat = chatManager.createChat(receiverJID, messageListener);
            chat.sendMessage(message);
        }
    }

    public void sendMessage(String message, String receiverJID) throws XMPPException {
        if (connection.isConnected()) {
            Chat chat = chatManager.createChat(receiverJID, messageListener);
            chat.sendMessage(message);
        }
    }

    public void createEntry(String user, String name) throws Exception {
        Roster roster = connection.getRoster();
        roster.createEntry(user, name, null);
    }


    // ******************* Inner classes **************************************
    private class XmppMessageListener implements MessageListener {
        @Override public void processMessage(Chat chat, Message message) {

        }
    }

    private class XmppPacketListener implements PacketListener {
        @Override public void processPacket(Packet packet) {
                String from = ((Message) packet).getFrom();
                String body = ((Message) packet).getBody();
                if (body.toLowerCase().equals("temp?")) {
                    main.answerTempRequest(from);
                }
        }
    }
}
