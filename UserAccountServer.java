import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Represents a server managing user accounts and handling client interactions.
 */
public class UserAccountServer {

    private static final String USER_ACCOUNTS_FP = "UserAccounts.txt";
    private static final String USAGE = "Usage: java UserAccountServer [port]";
    private static final Integer THREAD_COUNT = 20;
    private static Map<String, User> userAccounts;
    private static Set<String> loggedInUsers;

    /**
     * Static initializer block to load user accounts from file.
     */
    static {
        loadUserAccounts();
    }

    /**
     * Loads user accounts from the file into the userAccounts map.
     */
    private static void loadUserAccounts() {
        userAccounts = new HashMap<>();
        loggedInUsers = new HashSet<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(USER_ACCOUNTS_FP))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length == 2) {
                    String username = parts[0].trim();
                    int lifetimeWins = Integer.parseInt(parts[1].trim());
                    userAccounts.put(username, new User(username, lifetimeWins));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves user account information to the file.
     * @param user - The User object containing account information.
     */
    private static void saveUserAccount(User user) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(USER_ACCOUNTS_FP, true))) {
            writer.println(user.getUsername() + "," + user.getLifetimeWins());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Checks if a user is already registered
     * @param username - The username to check for registration
     * @return - 1 if the user is registered and not currently logged in, 2 if the user is not registered and
     * not logged in, and 0 if the user is not logged in and not registered.
     */
    public static synchronized int login(String username) {
        if (userAccounts.containsKey(username.trim()) && !loggedInUsers.contains(username.trim())) {
            loggedInUsers.add(username);
            return 1;
        } else if (!userAccounts.containsKey(username.trim()) && !loggedInUsers.contains(username.trim())) {
            createUser(username);
            loggedInUsers.add(username);
            return 2;
        } else {
            return 0;
        }
    }

    /**
     * Creates a new user and updates userAccounts map and file.
     * @param username - The username of the new user.
     */
    private static void createUser(String username) {
        User newUser = new User(username, 0);
        userAccounts.put(username, newUser);
        saveUserAccount(newUser);
    }

    private static synchronized int logout(String username) {
        if (loggedInUsers.contains(username.trim())) {
            User user = userAccounts.get(username);
            saveUserAccount(user);
            loggedInUsers.remove(username.trim());
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Handles communication with a connected client.
     * @param clientSocket - The Socket representing the connection to the client.
     */
    public static void handleClient(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()))) {
            messageClient(clientSocket, "Welcome to the server! Please enter your username: ");
            String username = in.readLine().trim();
            if (username.isEmpty()) {
                messageClient(clientSocket, "invalid username, please try again.");
                handleClient(clientSocket);
            }
            if (login(username) == 1) {
                messageClient(clientSocket, "Logging in as: " + username);
            }
            messageClient(clientSocket, "Welcome to the server " + username + "!");
            messageClient(clientSocket, "Enter 'q' to quit.");
            String clientMessage;
            while ((clientMessage = in.readLine()) != null) {
                //this area is for back and forth communication with client
                if ("q".equalsIgnoreCase(clientMessage)) {
                    messageClient(clientSocket, "Ending communication");
                    logout(username);
                    break;
                } else {
                    messageClient(clientSocket, "Server received: " + clientMessage);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void messageClient(Socket clientSocket, String message) {
        try {
            clientSocket.getOutputStream().write((message + "\n").getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Main entry point for running the UserAccountServer.
     * @param args - Command-line arguments, expects a single argument representing the port number to use.
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println(USAGE);
            System.exit(1);
        }

        int port = 0;
        ServerSocket server = null;

        try {
            port = Integer.parseInt(args[0]);
            server = new ServerSocket(port);
            System.out.println("The UserAccount server is running...");
            ExecutorService fixedThreadPool = Executors.newFixedThreadPool(THREAD_COUNT);
            while (true) {
                Socket clientSocket = server.accept();
                System.out.println("Connection from " + clientSocket.getInetAddress());

                if (((ThreadPoolExecutor) fixedThreadPool).getActiveCount() == THREAD_COUNT) {
                    messageClient(clientSocket, "The server is currently full; try again later.");
                    clientSocket.close();
                } else {
                    fixedThreadPool.execute(() -> handleClient(clientSocket));
                }
            }
        } catch (IOException e) {
            System.out.println(
                    "Exception caught when trying to listen on port " + port + " or listening for a connection");
            System.out.println(e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (server != null) {
                    server.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
