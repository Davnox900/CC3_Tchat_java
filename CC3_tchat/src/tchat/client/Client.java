package tchat.client;

import tchat.ITchat;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.io.*;
import java.util.Set;
import java.nio.charset.Charset;
import java.util.Iterator;


/**
 * Client de tchat
 */
public class Client extends Thread implements ITchat {

    private ClientUI clientUI;
    private String serverHostname;
    private int serverPort;
    public String clientNickname;
    private SocketChannel socketChannel;
    private Selector selector;
    private Charset charset = Charset.forName("UTF-8");

    public Client(ClientUI clientUI, String serverHostname, int serverPort, String clientNickname) {
      this.clientUI = clientUI;
      this.serverHostname = serverHostname;
      this.serverPort = serverPort;
      this.clientNickname = clientNickname;
      try{
        selector = Selector.open();
        this.socketChannel = SocketChannel.open(new InetSocketAddress(serverHostname, serverPort));
        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
      }
      catch(IOException e){
        System.out.println("Exception dans le constructeur de la classe Client");
        e.printStackTrace();
      }
    }

    /**
     * Ajoute un message et passe le channel en
     * mode écriture
     */
    public void sendMessage(String message) {
      try {
        
        byte[] messageBytes = message.getBytes();
        ByteBuffer buffer = ByteBuffer.wrap(messageBytes);
        buffer.clear();
        socketChannel.register(selector, SelectionKey.OP_WRITE);
        buffer.put(messageBytes);
        buffer.flip();
        socketChannel.write(buffer);
        
      } catch(Exception e){
        System.out.println("Exception levée dans la méthode sendMessage de la classe Client");
        e.printStackTrace();
      }
    }

    /**
     * Processus principal du thread
     * on écoute
     */
    public void run() {
      try
      {
          while(clientUI.isRunning()) {
              int readyChannels = selector.select();
              if(readyChannels == 0) continue; 
              Set<SelectionKey> selectedKeys = selector.selectedKeys();
              Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
              while(keyIterator.hasNext()) {
                   SelectionKey key = keyIterator.next();
                   key.interestOps(SelectionKey.OP_READ);
                   keyIterator.remove();
                   readSelectionKey(key);
              }
          }
      }
      catch (IOException io)
      {}
    }

    private void readSelectionKey(SelectionKey key) throws IOException {
      if(key.isReadable()){
          SocketChannel channel = (SocketChannel) key.channel();

          ByteBuffer buffer = ByteBuffer.allocate(1024);
          String message = "";
          while(channel.read(buffer) > 0)
          {
            buffer.flip();
            message = message + charset.decode(buffer);
          }
          clientUI.appendMessage("" + message + "\n");
      }
  }
}
