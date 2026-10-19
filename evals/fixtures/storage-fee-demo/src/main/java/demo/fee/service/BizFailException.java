package demo.fee.service;

public class BizFailException extends RuntimeException {
    public BizFailException(String message) {
        super(message);
    }
}
