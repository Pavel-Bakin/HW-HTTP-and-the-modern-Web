import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashMap;

public class Server {
    private int threadPoolSize;

    public Server(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
    }

    public void start(int port) {
        final var validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

        try (final var serverSocket = new ServerSocket(port)) {
            // Создаем пул потоков для обработки подключений
            ExecutorService threadPool = Executors.newFixedThreadPool(threadPoolSize);
            while (true) {
                try {
                    final var socket = serverSocket.accept();
                    // Запускаем обработку подключения в отдельном потоке из пула
                    threadPool.execute(() -> handleConnection(socket, validPaths));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleConnection(Socket socket, List<String> validPaths) {
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            final var requestLine = in.readLine();
            Request request = parseRequest(requestLine);

            if (request == null) {
                // Invalid request, just close the socket
                socket.close();
                return;
            }

            String modifiedPath = request.getPath();
            Map<String, String> queryParams = request.getQueryParams();

            System.out.println("Request Path: " + modifiedPath);
            System.out.println("Query Parameters: " + queryParams);

            // Остальная часть обработки запроса
            if (!validPaths.contains(modifiedPath)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
                System.out.println("Request processed with 404 Not Found");
                return;
            }

            final var filePath = Path.of(".", "public", modifiedPath);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (modifiedPath.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
            } else {
                final var length = Files.size(filePath);
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                Files.copy(filePath, out);
                out.flush();
            }

            System.out.println("Request processed successfully");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Request parseRequest(String requestLine) {
        final var parts = requestLine.split(" ");

        if (parts.length != 3) {
            // Invalid request, just close the socket
            return null;
        }

        final var path = parts[1];
        String modifiedPath = path;
        Map<String, String> queryParams = new HashMap<>();

        // Извлекаем Query String из пути запроса
        if (path.contains("?")) {
            int index = path.indexOf("?");
            modifiedPath = path.substring(0, index);
            String queryString = path.substring(index + 1);
            String[] params = queryString.split("&");

            for (String param : params) {
                String[] keyValue = param.split("=");
                if (keyValue.length == 2) {
                    queryParams.put(keyValue[0], keyValue[1]);
                }
            }
        }

        return new Request(modifiedPath, queryParams);
    }
}
