package neoproxy.neoproxyserver.core.exceptions;

public class SilentException extends Exception {
    private SilentException() {
        super();
    }

    public static void throwException() throws SilentException {
        throw new SilentException();
    }
}
