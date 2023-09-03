import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.HashMap;

public class Server {
	
	private ServerSocket chatSocket;
	private ServerSocket fileSocket;
	private HashMap<String, ArrayList<ConnectionThread>> chatrooms;
	private HashMap<String, byte[]> files;
	
	public static void main(String[] args) {
		
		int port1 = Integer.parseInt(args[0]);
		int port2 = Integer.parseInt(args[1]);
		
		new Server(port1, port2).acceptClients();
		
	}
	
	public Server(int port1, int port2) {
		chatrooms = new HashMap<>();
		files = new HashMap<>();
		try { 
			chatSocket = new ServerSocket(port1);
			fileSocket = new ServerSocket(port2);
		} catch (Exception e) { e.printStackTrace(); }
	
	}
	
	public void acceptClients() {
		try {
			while (true) {
				Socket clientChatSocket = chatSocket.accept();
				Socket clientFileSocket = fileSocket.accept();
				
				new ConnectionThread(clientChatSocket, clientFileSocket).start();
			}
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	private class ConnectionThread extends Thread {
		
		private ArrayList<ConnectionThread> members;
		private BufferedReader reader;
		private PrintWriter writer;
		private DataInputStream dataInputStream;
		private DataOutputStream dataOutputStream;
		private String roomName;
		private String username;
		
		public ConnectionThread(Socket clientChatSocket, Socket clientFileSocket) {
			try {
				reader = new BufferedReader(new InputStreamReader(clientChatSocket.getInputStream()));
				writer = new PrintWriter(new OutputStreamWriter(clientChatSocket.getOutputStream()));
				
				dataInputStream = new DataInputStream(clientFileSocket.getInputStream());
				dataOutputStream = new DataOutputStream(clientFileSocket.getOutputStream());
				
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		@Override // Listen for client input
		public void run() {
			String input;
			try {
				while (true) {
					
					input = reader.readLine();
					
					if (input.charAt(0) == '#') {
						String[] userArgs = input.split(" ");
						
						if (userArgs[0].equals("#JOIN")) joinRoom(userArgs[1], userArgs[2]);
						else if (userArgs[0].equals("#CREATE")) createRoom(userArgs[1], userArgs[2]);
						
						else if (userArgs[0].equals("#PUT")) getFileFromClient(userArgs[1]);
						else if (userArgs[0].equals("#GET")) pushFileToClient(userArgs[1]);
						
						else if (userArgs[0].equals("#STATUS")) showStatus();
						else if (userArgs[0].equals("#EXIT")) leaveRoom();
						else if (userArgs[0].equals("#QUIT")) { terminate(); return; }
						
						else sendMessageToClient("### INVALID COMMAND: " + userArgs[0].substring(1));
						
					}
					else sendMessageToMembers("FROM " + username + ": " + input);
				}
			} catch (Exception e) { terminate(); return; }
		}
		
		
		
		
		public void sendMessageToClient(String message) {
			try {
				writer.println(message);
				writer.flush();
			} catch (Exception e) { e.printStackTrace(); }
		}
		
		
		public void sendMessageToMembers(String message) {
			if (members == null) {
				sendMessageToClient("### YOU ARE NOT IN A ROOM");
				return;
			}
			
			for (ConnectionThread member : members)
				if (member != this) member.sendMessageToClient(message);
		}
		
		
		public void joinRoom(String newRoomName, String newUsername) {
			
			ArrayList<ConnectionThread> newRoom = chatrooms.get(newRoomName);
			
			if (newRoom == null) {
				sendMessageToClient("### CHATROOM <" + newRoomName + "> DOES NOT EXIST");
				return;
			}
			
			if (members != null) leaveRoom();
			
			roomName = newRoomName;
			username = newUsername;
			
			members = newRoom;
			members.add(this);
			
			sendMessageToMembers("### " + username + " has joined the room.");
			sendMessageToClient("### Successfully joined room " + roomName);
			
		}
		
		
		public void createRoom(String newRoomName, String newUsername) {
			
			if (chatrooms.containsKey(newRoomName)) {
				sendMessageToClient("### CHATROOM <" + newRoomName + "> ALREADY EXISTS");
				return;
			}
			
			if (members != null) leaveRoom();
			
			roomName = newRoomName;
			username = newUsername;
		
			members = new ArrayList<ConnectionThread>();
			members.add(this);
			
			chatrooms.put(newRoomName, members);
			
			sendMessageToClient("### Successfully created room " + roomName);
			
		}
		
		
		public void leaveRoom() {
			
			if (members == null) {
				sendMessageToClient("### YOU ARE NOT IN A ROOM");
				return;
			}
			
			members.remove(this);
			sendMessageToMembers("### " + username + " has left the room.");
			members = null;
			sendMessageToClient("### Successfully left room " + roomName);
		}
		
		
		public void showStatus() {
			
			if (members == null) {
				sendMessageToClient("### YOU ARE NOT IN A ROOM");
				return;
			}
			
			String status = "\n"
					+ "### In Chatroom: <" + roomName + ">\n"
					+ "### With <" + members.size() + "> total members\n"
					+ "### Joined as <" + username + ">\n";
			
			for (ConnectionThread member : members) {
				status += "### " + member.username;
				if (member == this) status += " (YOU)";
				status += "\n";
			}
			
			sendMessageToClient(status);
		
		}
		
		// #PUT
		// Receive file from client, and store it in files;
		public void getFileFromClient(String fileName) {
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						int fileSize = dataInputStream.readInt();
						if (fileSize == -1)	return;
						
						byte[] fileBytes = new byte[fileSize];
						
						int offset = 0;
						int stride = 64000;
						
						while (fileSize > 0) {
							int len = stride < fileSize ? stride : fileSize;
							dataInputStream.read(fileBytes, offset, len);
							offset += stride;
							fileSize -= stride;
						}
						
						files.put(fileName, fileBytes);
						
					} catch (Exception e) { e.printStackTrace(); }
				}
			}).start();
			
		}
		
		public void pushFileToClient(String fileName) {
			
			
			new Thread(new Runnable() {
				@Override
				public void run() {
					
					byte[] fileBytes = files.get(fileName);
					
					try {
						if (fileBytes == null) {
							dataOutputStream.writeInt(-1);
							sendMessageToClient("### FILE <" + fileName + "> DOES NOT EXIST");
							return;
						}
						
						int fileSize = fileBytes.length;
						dataOutputStream.writeInt(fileSize);
						
						int offset = 0;
						int stride = 64000;
						
						while (fileSize > 0) {
							int len = stride < fileSize ? stride : fileSize;
							dataOutputStream.write(fileBytes, offset, len);
							offset += stride;
							fileSize -= stride;
						}
						
					} catch (Exception e) { e.printStackTrace(); }
					
				}
			}).start();
		
		}
		
		public void terminate() {
			if (members != null) leaveRoom();
			
			try {
				if(reader != null) reader.close();
				if(writer != null) writer.close();
				
				if(dataInputStream != null) dataInputStream.close();
				if(dataOutputStream != null) dataOutputStream.close();
				
			} catch (Exception e) { e.printStackTrace(); }
		
		}
		
	}

		
}
