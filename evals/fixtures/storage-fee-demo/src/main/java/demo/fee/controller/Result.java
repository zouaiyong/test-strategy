package demo.fee.controller;

import lombok.Data;

@Data
public class Result<T> {
    private int code;
    private String msg;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }
}
