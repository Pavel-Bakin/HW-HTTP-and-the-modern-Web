import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

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

            // Создаем объект для хранения параметров из тела запроса
            Map<String, List<String>> postParams = new HashMap<>();

            // Если это POST-запрос и тип контента x-www-form-urlencoded, читаем тело запроса
            if (parts[0].equals("POST")) {
                String contentType = "";
                // Ищем заголовок "Content-Type" в запросе
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.isEmpty()) {
                        break;  // Заголовки закончились
                    }
                    if (line.startsWith("Content-Type:")) {
                        contentType = line.substring("Content-Type:".length()).trim();
                    }
                }
                // Если тип контента соответствует x-www-form-urlencoded
                if ("application/x-www-form-urlencoded".equals(contentType)) {
                    // Читаем тело запроса
                    StringBuilder requestBody = new StringBuilder();
                    int contentLength = 0;
                    while ((line = in.readLine()) != null) {
                        requestBody.append(line);
                        contentLength += line.length();
                    }
                    // Парсим параметры из тела запроса
                    List<NameValuePair> params = URLEncodedUtils.parse(requestBody.toString(), java.nio.charset.StandardCharsets.UTF_8);
                    for (NameValuePair param : params) {
                        String paramName = param.getName();
                        String paramValue = param.getValue();
                        // Добавляем параметры в объект postParams
                        postParams.computeIfAbsent(paramName, k -> new ArrayList<>()).add(paramValue);
                    }
                }
            }

            // Обработка параметров из тела запроса
            String postParamValue = getPostParam(postParams, "paramName");
            List<String> postParamValues = getPostParams(postParams, "paramName");

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

    // Метод для получения значения параметра из x-www-form-urlencoded запроса
    private static String getPostParam(Map<String, List<String>> postParams, String name) {
        List<String> values = postParams.get(name);
        if (values != null && !values.isEmpty()) {
            return values.get(0);
        }
        return null; // Если параметр не найден
    }

    // Метод для получения всех значений параметра из x-www-form-urlencoded запроса
    private static List<String> getPostParams(Map<String, List<String>> postParams, String name) {
        return postParams.get(name);
    }
}