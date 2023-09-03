
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.security.MessageDigest;
import java.util.Scanner;

public class Peer {
   
   private MulticastSocket mSocket;
   private InetAddress ip;
   private String username;
   private int portNum;
   private boolean inGroup;
   
   public static void main(String[] args) {
      
      Peer peer = new Peer(Integer.parseInt(args[0]));
      
      // Input & send
      new Thread(new Runnable() {
         @Override
         public void run() {
        	 
            Scanner scan = new Scanner(System.in);
            while(true) {
            	
               String input = scan.nextLine();
               
               if(input.isBlank()) continue;
               
               if(input.charAt(0) == '#') {
            	   
                  String[] userArgs = input.split(" ");
                  
                  if(userArgs[0].equals("#JOIN")) 
                     peer.join(userArgs[1], userArgs[2]);
                  
                  else if(userArgs[0].equals("#EXIT")) 
                     peer.leave();
                  
                  else
                     System.out.println("### INVALID COMMAND: "+userArgs[0].substring(1));
               }
               else {
                  peer.sendMessage(input, true);
               }
            }
         }
      }).start();
      
      // Receive
      new Thread(new Runnable() {
         @Override
         public void run() {
        	 
            while(true) 
            	peer.receiveMessage();
            
         }
         
      }).start();
     
   }
   
   public Peer(int portNum) {
      inGroup = false;
      try {
         mSocket = new MulticastSocket(portNum);
         this.portNum = portNum;
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
   
   public String hashAddress(String roomName) {
      try {
         MessageDigest mssgDigest = MessageDigest.getInstance("SHA-256");
         byte[] digest = mssgDigest.digest(roomName.getBytes());
         
         int byteLen = digest.length;
         int x = digest[byteLen - 3] & 0xff;
         int y = digest[byteLen - 2] & 0xff;
         int z = digest[byteLen - 1] & 0xff;
         
         return "225."+x+"."+y+"."+z;
         
      } catch (Exception e) {
         e.printStackTrace();
      }
      
      return null;
   }
   
   @SuppressWarnings("deprecation")
   public void join(String roomName, String username) {
      try {
         InetAddress newIp = InetAddress.getByName(hashAddress(roomName));
         if(newIp.equals(ip)) {
            System.out.println("### ALREADY IN " + roomName);
            return;
         }
         if(inGroup) {
            leave();
         }
         ip = newIp;
         mSocket.joinGroup(ip);
         inGroup = true;
         this.username = username;
         System.out.println("### Successfully joined "+roomName+"("+ip+")");
         sendMessage("has joined the room", false);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
   
   @SuppressWarnings("deprecation")
   public void leave() {
      if(!inGroup) {
         System.out.println("### NOT IN GROUP");
         return;
      }
      sendMessage("has left the room", false);
      try {
         mSocket.leaveGroup(ip);
         inGroup = false;
         ip = null;
      } catch (Exception e) {
         e.printStackTrace();
      }
      System.out.println("### You have left the room");
   }
      
   public void sendMessage(String message, boolean isMessage) {
	   
	   if(!inGroup) {
		   System.out.println("### YOU ARE NOT IN A ROOM");
		   return;
	   }
      
      if(message.length() > 512) {
         sendMessage(message.substring(0, 512), isMessage);
         sendMessage(message.substring(512), isMessage);
         return;
      }
      
      if(isMessage)
         message = username + ": " + message;
      else // notification
         message = "### " + username + " " + message;
      byte[] buffer = message.getBytes();
      
      try {
         DatagramPacket sendPacket = 
               new DatagramPacket(buffer, buffer.length, ip, portNum);
         mSocket.send(sendPacket);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }
   
   public void receiveMessage() {
      try {
         byte[] receive = new byte[512];
         
         DatagramPacket receivePacket =
               new DatagramPacket(receive, receive.length);
         mSocket.receive(receivePacket);
         
         String message = new String(receivePacket.getData()).trim();
         String temp = message;
         // notification
         if(temp.charAt(0) == '#') {
            temp = temp.substring(4);
         }
         
         // Only if different username UNSURE is this acceptable?
         if(!temp.split(":| ", 2)[0].equals(username)) {
            System.out.println(message);
         }
         
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

}