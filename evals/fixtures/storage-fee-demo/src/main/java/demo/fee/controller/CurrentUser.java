package demo.fee.controller;

/** 从登录上下文取当前操作人；fixture 里简化为常量。 */
public final class CurrentUser {
    private CurrentUser() {
    }

    public static Long id() {
        return 1L;
    }
}
