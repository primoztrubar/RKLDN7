import java.io.*;
import java.net.*;
import org.json.simple.*;
import org.json.simple.parser.JSONParser;


public class ChatClient extends Thread
{
	protected int serverPort = 1234;
	protected static BufferedReader std_in;

	public static void main(String[] args) throws Exception {
		std_in = new BufferedReader(new InputStreamReader(System.in));
		System.out.println("Za povezavo s strežnikom se morate prijaviti z imenom:");
		while (true) {
			String ime = std_in.readLine();
			if (ime == null || ime.length() == 0) {
				System.out.println("Vpišite ime:");
			}
			else {
				new ChatClient(ime);
				break;
			}
		}
	}

	public ChatClient(String ime) throws Exception {
		Socket socket = null;
		DataInputStream in = null;
		DataOutputStream out = null;

		JSONObject prijava = new JSONObject();
		prijava.put("prijava", ime);

		// connect to the chat server
		try {
			System.out.println("[system] connecting to chat server ...");
			socket = new Socket("localhost", serverPort); // create socket connection
			in = new DataInputStream(socket.getInputStream()); // create input stream for listening for incoming messages
			out = new DataOutputStream(socket.getOutputStream()); // create output stream for sending messages
			System.out.println("[system] connected");

			ChatClientMessageReceiver message_receiver = new ChatClientMessageReceiver(in); // create a separate thread for listening to messages from the chat server
			message_receiver.start(); // run the new thread
		} catch (Exception e) {
			e.printStackTrace(System.err);
			System.exit(1);
		}
		sendMessage(prijava.toJSONString(), out);
		String userInput;
		// read from STDIN and send messages to the chat server
		while (true) {
			System.out.println("javno ali privatno sporočilo?");
			userInput = std_in.readLine();
			if (userInput == null) {
				System.out.println("napaka: vhod je null");
				break;
			}
			if (userInput.equals("javno")) {
				System.out.println("napišite sporočilo:");
				userInput = std_in.readLine();
				if (userInput == null) {
					System.out.println("napaka: vhod je null");
					break;
				}
				javnoSporocilo(userInput, out);
			}
			else if (userInput.equals("privatno")) {
				System.out.println("Vpišite prejemnika:");
				String prejemnik = std_in.readLine();
				if (prejemnik == null) {
					System.out.println("napaka: vhod je null");
					break;
				}

				System.out.println("napišite sporočilo:");
				userInput = std_in.readLine();
				if (userInput == null) {
					System.out.println("napaka: vhod je null");
					break;
				}
				privatnoSporocilo(userInput, prejemnik, out);
			}
		}

		// cleanup
		out.close();
		in.close();
		std_in.close();
		socket.close();
	}

	private void javnoSporocilo(String sporocilo, DataOutputStream out) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("tipSporocila", "javno");
		jsonObject.put("sporocilo", sporocilo);
		sendMessage(jsonObject.toJSONString(), out);
	}

	private void privatnoSporocilo(String sporocilo, String za, DataOutputStream out) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("tipSporocila", "privatno");
		jsonObject.put("za", za);
		jsonObject.put("sporocilo", sporocilo);
		sendMessage(jsonObject.toJSONString(), out);
	}

	private void sendMessage(String message, DataOutputStream out) {
		try {
			out.writeUTF(message); // send the message to the chat server
			out.flush(); // ensure the message has been sent
		} catch (IOException e) {
			System.err.println("[system] could not send message");
			e.printStackTrace(System.err);
		}
	}
}

// wait for messages from the chat server and print the out
class ChatClientMessageReceiver extends Thread {
	private DataInputStream in;
	private JSONParser parser;

	public ChatClientMessageReceiver(DataInputStream in) {
		this.in = in;
		parser = new JSONParser();
	}

	public void run() {
		try {
			String message;
			while ((message = this.in.readUTF()) != null) { // read new message
				JSONObject jsonObject = (JSONObject)parser.parse(message);
				if (jsonObject.containsKey("napaka")) {
					System.out.println("napaka: " + jsonObject.get("napaka"));
					continue;
				}
				if (!jsonObject.containsKey("tipSporocila") || !jsonObject.containsKey("od") || !jsonObject.containsKey("sporocilo")) {
					System.out.println("napaka: manjkajoči podatki v sporočilu strežnika");
					continue;
				}
				System.out.println("[RKchat:" + jsonObject.get("tipSporocila") +"] " + jsonObject.get("od") + " => " + jsonObject.get("sporocilo")); // print the message to the console
			}
		} catch (Exception e) {
			System.err.println("[system] could not read message");
			e.printStackTrace(System.err);
			System.exit(1);
		}
	}
}
