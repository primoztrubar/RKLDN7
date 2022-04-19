import java.io.*;
import java.net.*;
import java.util.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;

public class ChatServer {

	protected int serverPort = 1234;
	protected List<Socket> clients = new ArrayList<Socket>(); // list of clients
	public Map<String, Socket> names = new HashMap<>();

	public static void main(String[] args) throws Exception {
		new ChatServer();
	}

	public ChatServer() {
		ServerSocket serverSocket = null;

		// create socket
		try {
			serverSocket = new ServerSocket(this.serverPort); // create the ServerSocket
		} catch (Exception e) {
			System.err.println("[system] could not create socket on port " + this.serverPort);
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// start listening for new connections
		System.out.println("[system] listening ...");
		try {
			while (true) {
				Socket newClientSocket = serverSocket.accept(); // wait for a new client connection
				synchronized(this) {
					clients.add(newClientSocket); // add client to the list of clients
				}
				ChatServerConnector conn = new ChatServerConnector(this, newClientSocket); // create a new thread for communication with the new client
				conn.start(); // run the new thread
			}
		} catch (Exception e) {
			System.err.println("[error] Accept failed.");
			e.printStackTrace(System.err);
			System.exit(1);
		}

		// close socket
		System.out.println("[system] closing server socket ...");
		try {
			serverSocket.close();
		} catch (IOException e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}

	// send a message to all clients connected to the server
	public void sendToAllClients(String message) throws Exception {
		Iterator<Socket> i = clients.iterator();
		while (i.hasNext()) { // iterate through the client list
			Socket socket = (Socket) i.next(); // get the socket for communicating with this client
			try {
				DataOutputStream out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages to the client
				out.writeUTF(message); // send message to the client
			} catch (Exception e) {
				System.err.println("[system] could not send message to a client");
				e.printStackTrace(System.err);
			}
		}
	}

	public void sporociNapako(Socket socket, String napaka) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("napaka", napaka);
		String json = jsonObject.toJSONString();
		try {
			DataOutputStream out = new DataOutputStream(socket.getOutputStream());
			out.writeUTF(json);
		}
		catch (Exception e) {
			e.printStackTrace(System.err);
			System.err.println("[system] prišlo je do napake med pošiljanjem napake");
		}
	}

	public void posljiPrivatnoSporocilo(Socket s, String od, String za, String sporocilo) {
		JSONObject jsonObject = new JSONObject();	
		try {
			jsonObject.put("tipSporocila", "privatno");
			jsonObject.put("od", od);
			jsonObject.put("sporocilo", sporocilo);
			if (!names.containsKey(za)) {
				sporociNapako(s, "Prejemnik privatnega sporočila ne obstaja");
				return;
			}
			DataOutputStream out = new DataOutputStream(names.get(za).getOutputStream());
			out.writeUTF(jsonObject.toJSONString());
		} catch (Exception e) {
			System.err.println("[system] pošiljanje privatnega sporočila je spodletelo");
			e.printStackTrace(System.err);
		}
	}

	public void posljiJavnoSporocilo(String od, String sporocilo) {
		JSONObject jsonObject = new JSONObject();
		try {
			jsonObject.put("tipSporocila", "javno");
			jsonObject.put("od", od);
			jsonObject.put("sporocilo", sporocilo);
			sendToAllClients(jsonObject.toJSONString());
		} catch (Exception e) {
			System.err.println("[system] pošiljanje javnega sporočila je spodletelo");
			e.printStackTrace(System.err);
		}
	}

	public void removeClient(Socket socket, String name) {
		synchronized(this) {
			clients.remove(socket);
			if (name != null && names.containsKey(name)) {
				names.remove(name);
			}
		}
	}
}

class ChatServerConnector extends Thread {
	private ChatServer server;
	private Socket socket;
	private JSONParser parser;
	private String name;

	public ChatServerConnector(ChatServer server, Socket socket) {
		this.server = server;
		this.socket = socket;
		this.parser = new JSONParser();
	}

	public void run() {
		System.out.println("[system] connected with " + this.socket.getInetAddress().getHostName() + ":" + this.socket.getPort());

		DataInputStream in;
		try {
			in = new DataInputStream(this.socket.getInputStream()); // create input stream for listening for incoming messages
		} catch (IOException e) {
			System.err.println("[system] could not open input stream!");
			e.printStackTrace(System.err);
			this.server.removeClient(socket, null);
			return;
		}

		// Dokler ni prijave
		while (true) {
			String msg_received;
			try {
				msg_received = in.readUTF();
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket, null);
				return;
			}
			if (msg_received.length() == 0) // invalid message
				continue;
			
			JSONObject jsonObject;
			try {
				jsonObject = (JSONObject) parser.parse(msg_received);
			}
			catch (Exception e) {
				e.printStackTrace(System.err);
				System.out.println("Neveljavno sporočilo.");
				server.sporociNapako(socket, "Neveljavno sporočilo.");
				continue;
			}
			if (jsonObject.containsKey("prijava")) {
				name = (String) jsonObject.get("prijava");
				if (server.names.containsKey(name)) {
					server.sporociNapako(socket, "to ime že obstaja");
					continue;
				}
				server.names.put(name, socket);
				System.out.println("nova prijava: " + name);
				break;
			}
			else {
				server.sporociNapako(socket, "prijava je potrebna");
			}
		}		

		while (true) { // infinite loop in which this thread waits for incoming messages and processes them
			String msg_received;
			try {
				msg_received = in.readUTF(); // read the message from the client
			} catch (Exception e) {
				System.err.println("[system] there was a problem while reading message client on port " + this.socket.getPort() + ", removing client");
				e.printStackTrace(System.err);
				this.server.removeClient(this.socket, name);
				return;
			}
			if (msg_received.length() == 0) // invalid message
				continue;
			
			JSONObject jsonObject;
			try {
				jsonObject = (JSONObject) parser.parse(msg_received);
				if (!jsonObject.containsKey("tipSporocila") || !jsonObject.containsKey("sporocilo")) {
					throw new Exception("Nujno potrebni podatki v sporočilu manjkajo");
				}
			}
			catch (Exception e) {
				e.printStackTrace(System.err);
				System.out.println("Neveljavno sporočilo.");
				server.sporociNapako(socket, "Neveljavno sporočilo.");
				continue;
			}

			try {
				String tip = jsonObject.get("tipSporocila").toString();
				String sporocilo = jsonObject.get("sporocilo").toString();
				if (tip.equals("privatno")) {
					if (!jsonObject.containsKey("za")) {
						server.sporociNapako(socket, "manjka prejemnik");
						continue;
					}
					String za = jsonObject.get("za").toString();
					server.posljiPrivatnoSporocilo(socket, name, za, sporocilo);
					System.out.println("pošiljanje privatnega sporočila od " + name);
					continue;
				}
				else if (tip.equals("javno")) {
					server.posljiJavnoSporocilo(name, sporocilo);
					System.out.println(name + " => " + sporocilo);
				}
				else {
						server.sporociNapako(socket, "napačen tip sporočila");
						System.out.println(name + ": <napačen tip sporočila>");
						continue;
				}
			}
			catch (Exception e) {
				System.out.println("Prišlo je do napake");
				e.printStackTrace(System.err);
			}
		}
	}
}
