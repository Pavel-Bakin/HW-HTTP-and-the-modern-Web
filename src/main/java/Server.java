import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class Server {
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
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                socket.close();
                return;
            }

            final var path = parts[1];
            String modifiedPath = path;
            String queryString = "";
            if (modifiedPath.contains("?")) {
                int index = modifiedPath.indexOf("?");
                queryString = modifiedPath.substring(index + 1);
                modifiedPath = modifiedPath.substring(0, index);
            }

            if (!validPaths.contains(modifiedPath)) {
                out.write((
                        "HTTP/1.1 404 Not Found\r\n" +
                                "Content-Length: 0\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.flush();
                socket.close();
                return;
            }

            Map<String, List<String>> queryParams = parseQueryParams(queryString);
            MyRequest request = new MyRequest(modifiedPath, parts[0], parts[2], queryParams);
            processRequest(request, out);

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

    private void processRequest(MyRequest request, BufferedOutputStream out) throws IOException {
        String path = request.getPath();
        String httpMethod = request.getHttpMethod();
        String httpVersion = request.getHttpVersion();
        Map<String, List<String>> queryParams = request.getQueryParams();

        final var filePath = Path.of(".", "public", path);
        final var mimeType = Files.probeContentType(filePath);

        if (path.equals("/classic.html")) {
            final var template = Files.readString(filePath);
            final var content = template.replace(
                    "{time}",
                    LocalDateTime.now().toString()
            ).getBytes();
            out.write((
                    httpVersion + " 200 OK\r\n" +
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
                    httpVersion + " 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();
        }
    }

    private Map<String, List<String>> parseQueryParams(String queryString) {
        Map<String, List<String>> queryParams = new HashMap<>();
        if (!queryString.isEmpty()) {
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = keyValue[0];
                String value = keyValue.length > 1 ? keyValue[1] : "";
                queryParams.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
            }
        }
        return queryParams;
    }
}