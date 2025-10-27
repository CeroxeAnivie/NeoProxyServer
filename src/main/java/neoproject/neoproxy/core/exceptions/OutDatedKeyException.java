package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.management.SequenceKey;

public class OutDatedKeyException extends Exception {
    private OutDatedKeyException(String msg) {
        super(msg);
    }

    public static void throwException(SequenceKey sequenceKey) throws OutDatedKeyException {
        throw new OutDatedKeyException("The vault " + sequenceKey.getName() + " are out of date.");
    }
}
