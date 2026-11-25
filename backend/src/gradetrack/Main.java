package gradetrack;

import gradetrack.model.GradeBook;
import gradetrack.server.ApiServer;
import gradetrack.service.GradeService;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.net.BindException;
import java.net.URI;

/**
 * Starts GradeTrack: loads the saved data, then runs the Java web server that serves the
 * website and its JSON API.
 *
 * Options (all optional):
 *   --port 8080         port number
 *   --data FILE         data file (default data/gradetrack-data.txt)
 *   --web FOLDER        folder that contains index.html (default: here, or the parent folder)
 *   --no-browser        do not open the browser automatically
 */
public class Main {

    public static void main(String[] args) {
        int port = 8080;
        File dataFile = new File("data", "gradetrack-data.txt");
        File webRoot = null;
        boolean openBrowser = true;

        try {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--port") && i + 1 < args.length) {
                    port = Integer.parseInt(args[++i]);
                } else if (args[i].equals("--data") && i + 1 < args.length) {
                    dataFile = new File(args[++i]);
                } else if (args[i].equals("--web") && i + 1 < args.length) {
                    webRoot = new File(args[++i]);
                } else if (args[i].equals("--no-browser")) {
                    openBrowser = false;
                } else {
                    System.err.println("Unknown option: " + args[i]);
                    System.exit(2);
                }
            }
        } catch (NumberFormatException e) {
            System.err.println("The port must be a number, for example: --port 8080");
            System.exit(2);
        }

        if (webRoot == null) {
            webRoot = new File("index.html").isFile() ? new File(".") : new File("..");
        }
        if (!new File(webRoot, "index.html").isFile()) {
            System.err.println("Cannot find index.html in " + webRoot.getAbsolutePath()
                    + "\nStart the program from the backend folder, or use --web FOLDER.");
            System.exit(1);
        }

        GradeBook book = new GradeBook(dataFile);
        try {
            book.load();
        } catch (IOException e) {
            System.err.println("The saved data file could not be read, so sample data is used: " + e.getMessage());
        }

        try {
            ApiServer server = new ApiServer(port, new GradeService(book), webRoot);
            server.start();
            String url = "http://localhost:" + server.getPort() + "/";
            System.out.println("GradeTrack is running (Java backend).");
            System.out.println("  Website : " + url);
            System.out.println("  Data    : " + dataFile.getAbsolutePath());
            System.out.println("Press Ctrl+C to stop.");
            if (openBrowser) {
                open(url);
            }
        } catch (BindException e) {
            System.err.println("Port " + port + " is already in use. Close the other program or use: --port 9090");
            System.exit(1);
        } catch (IOException e) {
            System.err.println("Could not start the server: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void open(String url) {
        try {
            if (!GraphicsEnvironment.isHeadless() && Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            }
        } catch (Exception e) {
            // the browser could not be opened automatically; the address is printed above
        }
    }
}
