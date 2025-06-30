package neoproject.neoproxy.core;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public record HostSign(int port, Socket host, ObjectInputStream objectInputStream,
                       ObjectOutputStream objectOutputStream) {
}
