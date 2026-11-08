package lms;

/** An error that is safe to show to the user (wrong password, book not available, ...). */
public class ApiException extends RuntimeException {
    public final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }
}
