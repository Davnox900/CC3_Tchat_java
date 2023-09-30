package tchat.server;
import tchat.ITchat;

import javafx.application.Platform;
import java.io.IOException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Channel;
import java.nio.charset.Charset;

/**
 * Processus serveur qui écoute les connexions entrantes,
 * les messages entrants et les rediffuse aux clients connectés
 *
 * @author mathieu.fabre
 */
public class Server extends Thread implements ITchat {

    /**
     * Interface graphique du serveur
     */
    private ServerUI serverUI;

    private Charset charset = Charset.forName("UTF-8");
    private String serverIP;
    private int serverPort;
    private ServerSocketChannel serverSocketChannel;
    private Selector selector;
    private static String messageDelimiter = "__";

    /**
     * Constructeur
     * Lien avec l'interface graphique
     * Création du sélecteur et du socket serveur
     *
     * @param serverUI
     */
    public Server(ServerUI serverUI, String serverIP, int serverPort) {

        this.serverIP = serverIP;
        this.serverPort = serverPort;
        this.serverUI = serverUI;

        try {
            this.selector = Selector.open();
            this.serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.configureBlocking(false);
            InetSocketAddress inetSocketAddress = new InetSocketAddress(serverIP, serverPort);
            serverSocketChannel.bind(inetSocketAddress);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            sendLogToUI("Serveur créé");
        } catch (IOException e) {
            System.out.println("Exception levée dans le constructeur de la classe Server");
            e.printStackTrace();
        }

    }

    /**
     * Envoie un message de log à l'IHM
     */
    public void sendLogToUI(String message) {
        Platform.runLater(() -> serverUI.log(message));
    }

    /**
     * Processus principal du serveur
     */
    public void run() {
        try {
            while (serverUI.isRunning()) {
                int readyChannels = selector.select();
                if (readyChannels == 0) continue;
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();
                    handleSelectionKey(serverSocketChannel, key);
                }
            }
        } catch (Exception e) {
            System.out.println("Exception levée dans la méthode run de la classe Server");
            e.printStackTrace();
        }
    }

    public void handleSelectionKey(ServerSocketChannel serverSocketChannel, SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            // On accepte la connexion
            SocketChannel clientSocketChannel = serverSocketChannel.accept();
            clientSocketChannel.configureBlocking(false);
            clientSocketChannel.register(selector, SelectionKey.OP_READ);

            key.interestOps(SelectionKey.OP_ACCEPT);
            sendLogToUI("Connexion du client sur :" + clientSocketChannel.getRemoteAddress());
        }
        if (key.isReadable()) {
            SocketChannel clientSocketChannel = (SocketChannel) key.channel();
            ByteBuffer buffer = ByteBuffer.allocate(1024);
            StringBuilder message = new StringBuilder();
            try {
                while (clientSocketChannel.read(buffer) > 0) {
                    buffer.flip();
                    message.append(charset.decode(buffer));
                }
                key.interestOps(SelectionKey.OP_READ);
            } catch (IOException io) {
                key.cancel();
                clientSocketChannel.close();
            }
            if (message.length() > 0) {
                String[] messageParts = message.toString().split(messageDelimiter);
                String finalMessage = messageParts[0];
                broadcast(finalMessage, selector);
            }
        }
    }

    // Envoie du message à tous les clients connectés
    public void broadcast(String message, Selector selector) throws IOException {
        // Pour chaque clé utilisée par le sélecteur, soit chaque client connecté au serveur
        for (SelectionKey key : selector.keys()) {
            Channel targetChannel = key.channel();
            if (targetChannel instanceof SocketChannel && targetChannel != null) {
                SocketChannel destination = (SocketChannel) targetChannel;
                destination.write(charset.encode(message));
            }
        }
    }
}
