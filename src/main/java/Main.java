import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

public class Main {
    public static void main(String[] args) {
        final var validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html", "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");

        try (final var serverSocket = new ServerSocket(9998)) {
            // Создаем пул потоков для обработки подключений
            ExecutorService threadPool = Executors.newFixedThreadPool(64);
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

    // Метод для извлечения параметра из Query String
    private static String getQueryParam(String queryString, String name) {
        List<NameValuePair> params = URLEncodedUtils.parse(queryString, java.nio.charset.StandardCharsets.UTF_8);
        for (NameValuePair param : params) {
            if (param.getName().equals(name)) {
                return param.getValue();
            }
        }
        return null; // Если параметр не найден
    }

    // Метод для извлечения всех параметров из Query String
    private static List<NameValuePair> getQueryParams(String queryString) {
        return URLEncodedUtils.parse(queryString, java.nio.charset.StandardCharsets.UTF_8);
    }

    // Вынесенный метод для обработки подключения
    private static void handleConnection(Socket socket, List<String> validPaths) {
        try (
                final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                final var out = new BufferedOutputStream(socket.getOutputStream());
        ) {
            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            if (parts.length != 3) {
                // just close socket
                socket.close();
                return;
            }

            final var path = parts[1];
            String modifiedPath = path;
            // Извлекаем Query String из пути запроса
            String queryString = "";
            if (path.contains("?")) {
                int index = path.indexOf("?");
                queryString = path.substring(index + 1);
                modifiedPath = path.substring(0, index);
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

            // Пример использования getQueryParam для получения значения параметра
            String paramName = "paramName";
            String paramValue = getQueryParam(queryString, paramName);

            // Пример использования getQueryParams для получения всех параметров
            List<NameValuePair> queryParams = getQueryParams(queryString);
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
}