package neoproject.neoproxy.core.exceptions;

import neoproject.neoproxy.core.SequenceKey;

public class OutDatedVaultException extends Exception {
    private OutDatedVaultException(String msg) {
        super(msg);
    }

    public static void throwException(SequenceKey sequenceKey) throws OutDatedVaultException {
        throw new OutDatedVaultException("The vault " + sequenceKey.getName() + " are out of date.");
    }
}
