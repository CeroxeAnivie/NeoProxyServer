package neoproxy.neoproxyserver.core.exceptions;

public class BlockingMessageException extends Exception {
    private final String customMessage;

    public BlockingMessageException(String customMessage) {
        super(customMessage);
        this.customMessage = customMessage;
    }

    public String getCustomMessage() {
        return customMessage;
    }
}