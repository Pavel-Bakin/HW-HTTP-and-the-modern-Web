import java.util.List;
import java.util.Map;

class MyRequest {
    private final String path;
    private final String httpMethod;
    private final String httpVersion;
    private final Map<String, List<String>> queryParams;

    public MyRequest(String path, String httpMethod, String httpVersion, Map<String, List<String>> queryParams) {
        this.path = path;
        this.httpMethod = httpMethod;
        this.httpVersion = httpVersion;
        this.queryParams = queryParams;
    }

    public String getPath() {
        return path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getHttpVersion() {
        return httpVersion;
    }

    public Map<String, List<String>> getQueryParams() {
        return queryParams;
    }
}