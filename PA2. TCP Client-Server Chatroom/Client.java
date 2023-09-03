import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Client {
	
	private Socket chatSocket;
	private Socket fileSocket;
	private Scanner scan;
	private PrintWriter writer;
	private BufferedReader reader;
	private DataInputStream dataInputStream;
	private DataOutputStream dataOutputStream;
	
	public static void main(String[] args) throws Exception {
		
		InetAddress ip = InetAddress.getByName(args[0]);
		int port1 = Integer.parseInt(args[1]);
		int port2 = Integer.parseInt(args[2]);
		
		new Client(ip, port1, port2).start();
		
	}
	
	public Client(InetAddress ip, int port1, int port2) {
		try {
			chatSocket = new Socket(ip, port1);
			fileSocket = new Socket(ip, port2);
			
			scan = new Scanner(System.in);
			reader = new BufferedReader(new InputStreamReader(chatSocket.getInputStream()));
			writer = new PrintWriter(new OutputStreamWriter(chatSocket.getOutputStream()));
			
			dataInputStream = new DataInputStream(fileSocket.getInputStream());
			dataOutputStream = new DataOutputStream(fileSocket.getOutputStream());
			
		} catch (Exception e) { e.printStackTrace(); }
		
	}
	
	
	public void start() {
		// Send
		new Thread(new Runnable() {
			@Override
			public void run() {
				String message;
				while (true) {
					
					message = scan.nextLine();
					if (message.isBlank()) continue;
					
					writer.println(message);
					writer.flush();
					
					if (message.equals("#QUIT")) terminate();
					
					if (message.charAt(0) == '#') {
						String userArgs[] = message.split(" ");
						if (userArgs[0].equals("#PUT")) putFile(userArgs[1]);
						else if (userArgs[0].equals("#GET")) getFile(userArgs[1]);
					}
	
				}
			}
		}).start();
		
		// Receive
		new Thread(new Runnable() {
			@Override
			public void run() {
				String message;
				try {
					while (true) {
						message = reader.readLine();
						if (message == null) terminate();
						System.out.println(message);
					} 
				} catch (Exception e) { terminate(); }
			}
		}).start();
		
	}
	
	// push file to server
	public void putFile(String fileName) {
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				FileInputStream fileInputStream;
				try {
					fileInputStream = new FileInputStream(fileName);
				} catch (FileNotFoundException e) {
					System.out.println("### FILE <" + fileName + "> DOES NOT EXIST");
					try { dataOutputStream.writeInt(-1); } // notify server to not wait for file
					catch (Exception ee) { ee.printStackTrace(); }
					return;
				}
				try {
					byte[] fileBytes = fileInputStream.readAllBytes();
					
					int fileSize = fileBytes.length;
					
					dataOutputStream.writeInt(fileSize);
					
					int offset = 0;
					int stride = 64000; 
					
					System.out.println("\n### Uploading <" + fileName + "> (" + fileSize + " Bytes)...");
					System.out.print("### Progress : [");
					
					while (fileSize > 0) {
						int len = stride < fileSize ? stride : fileSize;
						dataOutputStream.write(fileBytes, offset, len);
						offset += stride;
						fileSize -= stride;
						System.out.print('#');
					}
					
					System.out.println("]\n### Successfully uploaded file <" + fileName +">\n");
					
					fileInputStream.close();
					
				} catch (Exception e) { e.printStackTrace(); }
			}
		}).start();
	}
	
	// pull file from server
	public void getFile(String fileName) {
		
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					int fileSize = dataInputStream.readInt();
					if (fileSize == -1) return;
					
					byte[] fileBytes = new byte[fileSize];
					
					// This creates a file by the name fileName
					FileOutputStream fileOutputStream = new FileOutputStream(fileName);
					
					int offset = 0;
					int stride = 64000;
					
					System.out.println("\n### Downloading <" + fileName + "> (" + fileSize + " Bytes)...");
					System.out.print("### Progress : [");
					
					while (fileSize > 0) {
						int len = stride < fileSize ? stride : fileSize;
						dataInputStream.read(fileBytes, offset, len); // store at fileBytes
						fileOutputStream.write(fileBytes, offset, len); // copy contents in fileBytes to file
						offset += stride;
						fileSize -= stride;
						System.out.print('#');
					}
					
					fileOutputStream.close();
					
					System.out.println("]\n### Successfully downloaded file <" + fileName + ">\n");
					
				} catch (Exception e) { e.printStackTrace(); }
			}
		}).start();
		
	}
	
	public void terminate() {
		try {
			if(scan != null) scan.close();
			
			if(reader != null) reader.close();
			if(writer != null) writer.close();
			
			if(dataInputStream != null) dataInputStream.close();
			if(dataOutputStream != null) dataOutputStream.close();
	
			System.exit(0);
		} catch (Exception e) { e.printStackTrace(); }
		
	}
	

}
