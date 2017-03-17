import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A multithreaded chat room server.  When a client connects the
 * server requests a screen name by sending the client the
 * text "SUBMITNAME", and keeps requesting a name until
 * a unique one is received.  After a client submits a unique
 * name, the server acknowledges with "NAMEACCEPTED".  Then
 * all messages from that client will be broadcast to all other
 * clients that have submitted a unique screen name.  The
 * broadcast messages are prefixed with "MESSAGE ".
 *
 * Because this is just a teaching example to illustrate a simple
 * chat server, there are a few features that have been left out.
 * Two are very useful and belong in production code:
 *
 *     1. The protocol should be enhanced so that the client can
 *        send clean disconnect messages to the server.
 *
 *     2. The server should do some logging.
 */
public class ChatServer {

    /**
     * The port that the server listens on.
     */
    private static final int PORT = 9001;

    /**
     * The set of all names of clients in the chat room.  Maintained
     * so that we can check that new clients are not registering name
     * already in use.
     */
    private static HashSet<String> names = new HashSet<String>();

    /**
     * The set of all the print writers for all the clients.  This
     * set is kept so we can easily broadcast messages.
     */
    private static HashSet<PrintWriter> writers = new HashSet<PrintWriter>();

    /**
     * The set of printers stored along with their name as the key.
     */
    private static Map<String,PrintWriter> userWriter = new HashMap<>();

    /**
     * The appplication main method, which just listens on a port and
     * spawns handler threads.
     */
    public static void main(String[] args) throws Exception {
        System.out.println("The chat server is running.");
        ServerSocket listener = new ServerSocket(PORT);
        try {
            while (true) {
                new Handler(listener.accept()).start();
            }
        } finally {
            listener.close();
        }
    }

    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for a dealing with a single client
     * and broadcasting its messages.
     */
    private static class Handler extends Thread {
        private String name;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        /**
         * Constructs a handler thread, squirreling away the socket.
         * All the interesting work is done in the run method.
         */
        public Handler(Socket socket) {
            this.socket = socket;
        }

        /**
         * Services this thread's client by repeatedly requesting a
         * screen name until a unique one has been submitted, then
         * acknowledges the name and registers the output stream for
         * the client in a global set, then repeatedly gets inputs and
         * broadcasts them.
         */
        public void run() {
            try {

                // Create character streams for the socket.
                in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                // Request a name from this client.  Keep requesting until
                // a name is submitted that is not already used.  Note that
                // checking for the existence of a name and adding the name
                // must be done while locking the set of names.
                while (true) {
                    out.println("SUBMITNAME");
                    name = in.readLine();
                    if (name == null) {
                        return;
                    }
                    synchronized (names) {
                        if (!names.contains(name)) {
                            names.add(name);
                            break;
                        }
                    }
                }

                // Now that a successful name has been chosen, add the
                // socket's print writer to the set of all writers so
                // this client can receive broadcast messages.
                out.println("NAMEACCEPTED");
                writers.add(out);

                //Adding the user name as the key and printwriter as the value.
                if(!userWriter.containsKey(name))
                    userWriter.put(name,out);

                // Write the available users to all connected clients
                broadcastOnlineUsers();

                // Accept messages from this client and broadcast them.
                // Ignore other clients that cannot be broadcasted to.
                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        return;
                    }

                    // Identifying if the input is meant to be sent to a specific user
                    // Check if it matches the following pattern eg: 'Nimal>>Hi'
                    Pattern p1 = Pattern.compile("^(.*)>>(.*)$");
                    Matcher m1 = p1.matcher(input);

                    // Identifying if the input is meant to be sent to multiple users
                    // Check if it matches the following pattern eg: '[Nimal,Sunil]>>>Hi'
                    Pattern p2 = Pattern.compile("^(.*)>>>(.*)$");
                    Matcher m2 = p2.matcher(input);

                    if(m2.find()){
                        String multiUserString = m2.group(1);
                        String[] multipleUsers = multiUserString.substring(1,multiUserString.length()-1).split(", ");
                        for (String user: multipleUsers) {
                            if(names.contains(user)) {
                                // Write the message in the specified user's writer.
                                userWriter.get(user).println("MESSAGE " + name + ": " + m2.group(2));
                            }
                        }
                        // Write the message in the sender's writer.
                        userWriter.get(name).println("MESSAGE " + name + ": " + m2.group(2));
                    }
                    else if (m1.find()) {
                        String specificUser = m1.group(1);
                        // Check if the name extracted exists in our user name set.
                        if(names.contains(specificUser)) {
                            // Write the message in the specified user's writer.
                            userWriter.get(specificUser).println("MESSAGE " + name + ": " + m1.group(2));
                            // Write the message in the sender's writer.
                            userWriter.get(name).println("MESSAGE " + name + ": " + m1.group(2));
                        }
                    }
                    else {
                        // If it doesn't exist, broadcast the message
                        for (PrintWriter writer : writers) {
                            writer.println("MESSAGE " + name + ": " + input);
                        }
                    }
                }
            } catch (IOException e) {
                System.out.println(e);
            } finally {
                // This client is going down!  Remove its name and its print
                // writer from the sets, and close its socket.
                if (name != null) {
                    names.remove(name);
                }
                if (out != null) {
                    writers.remove(out);
                    broadcastOnlineUsers();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                }
            }
        }

        /**
         * Removes the user himself from the set and writes the set of available users
         */
        private void broadcastOnlineUsers(){
            userWriter.entrySet().forEach(entries->{
                HashSet<String> onlineUsers = new HashSet<>();
                onlineUsers.addAll(names);
                onlineUsers.remove(entries.getKey());
                entries.getValue().println("USERS " + onlineUsers);
            });
        }
    }
}