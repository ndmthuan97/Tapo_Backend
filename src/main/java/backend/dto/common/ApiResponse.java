package backend.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    
    @JsonProperty("statusCode")
    private int statusCode;
    
    @JsonProperty("data")
    private T data;
    
    @JsonProperty("message")
    private String message = "";
    
    @JsonProperty("errors")
    private List<String> errors;

    public ApiResponse() {}

    public ApiResponse(int statusCode, T data, String message, List<String> errors) {
        this.statusCode = statusCode;
        this.data = data;
        this.message = message != null ? message : "";
        this.errors = errors;
    }

    public int getStatusCode() { return statusCode; }
    public void setStatusCode(int statusCode) { this.statusCode = statusCode; }

    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, data, "", null);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(200, data, message, null);
    }

    public static <T> ApiResponse<T> error(int statusCode, String message) {
        return new ApiResponse<>(statusCode, null, message, null);
    }

    public static <T> ApiResponse<T> error(int statusCode, String message, List<String> errors) {
        return new ApiResponse<>(statusCode, null, message, errors);
    }
}
