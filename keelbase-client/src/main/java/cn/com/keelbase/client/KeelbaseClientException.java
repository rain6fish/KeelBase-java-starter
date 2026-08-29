package cn.com.keelbase.client;

/** KeelbaseClient 运行时异常：委托 token 获取/验签/响应解析失败。 */
public class KeelbaseClientException extends RuntimeException {

    public KeelbaseClientException(String message) {
        super(message);
    }

    public KeelbaseClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
