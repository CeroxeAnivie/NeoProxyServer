package neoproject.neoproxy.core;

import neoproject.neoproxy.NeoProxyServer;
import neoproject.neoproxy.core.exceptions.NoMoreNetworkFlowException;
import plethora.utils.config.LineConfigReader;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static neoproject.neoproxy.NeoProxyServer.*;

public class SequenceKey {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd-HH:mm");
    private static final String url = "jdbc:h2:./data/key";

    protected double balance;//mb
    protected String expireTime;//2026/01/01-13:33
    protected int port = -1;
    protected double rate = 0;//mbps
    private final File keyFile;

    public SequenceKey(File keyFile) throws IOException {
        this.keyFile = keyFile;
        readAndSetElementFromFile(this, keyFile);

    }

    private SequenceKey(String name, double balance, String expireTime, int port, double rate) {
        this.keyFile = new File(KEY_FILE_DIR + File.separator + name);
        this.balance = balance;
        this.expireTime = expireTime;
        this.port = port;
        this.rate = rate;
    }

    public static boolean createNewKey(String name, double balance, String expireTime, int port, double rate) {
        for (SequenceKey sequenceKey : sequenceKeyDatabase) {//检查是否已经存在，如果存在，返回 false 表示创建失败
            if (sequenceKey.getName().equals(name)){
                return false;
            }
        }
        SequenceKey sequenceKey = new SequenceKey(name, balance, expireTime, port, rate);
        if (saveToFile(sequenceKey)) {
            try {
                readAndSetElementFromFile(sequenceKey, sequenceKey.getFile());
            } catch (IOException e) {
                debugOperation(e);
            }
            sequenceKeyDatabase.add(sequenceKey);
            return true;
        } else {
            return false;
        }
    }

    public static boolean isOutOfDate(String endTime) {
        try {
            // 将输入的字符串解析为 LocalDateTime 对象
            LocalDateTime inputTime = LocalDateTime.parse(endTime, FORMATTER);
            // 获取当前的日期和时间
            LocalDateTime now = LocalDateTime.now();
            // 如果当前时间在输入时间之后，则已过期，返回 true
            return now.isAfter(inputTime);
        } catch (DateTimeParseException e) {
            // 如果字符串格式不正确，无法解析，则认为是无效输入
            // 根据需求，这里可以选择抛出异常或返回一个默认值。
            // 通常，对于无效的过期时间，可以认为它“未过期”或“已过期”。
            // 此处按常规逻辑，无法判断则视为未过期（返回 false）。
            // 你也可以根据业务需求改为抛出异常。
            return false;
        }
    }

    public static boolean removeKey(String name) {
        for (SequenceKey sequenceKey : NeoProxyServer.sequenceKeyDatabase) {
            if (sequenceKey.getFile().getName().equals(name)) {
                boolean b = sequenceKey.getFile().delete();
                if (b) {//保持文件始终跟集合同步
                    NeoProxyServer.sequenceKeyDatabase.remove(sequenceKey);
                }
                return b;
            }
        }
        return false;
    }

    private static void readAndSetElementFromFile(SequenceKey sequenceKey, File vaultFile) throws IOException {
        try {
            LineConfigReader lineConfigReader = new LineConfigReader(vaultFile);
            lineConfigReader.load();
            sequenceKey.balance = Double.parseDouble(lineConfigReader.get("balance"));
            sequenceKey.expireTime = lineConfigReader.get("expireTime");
            if (lineConfigReader.containsKey("port")) {
                sequenceKey.port = Integer.parseInt(lineConfigReader.get("port"));
            }
            sequenceKey.rate = Double.parseDouble(lineConfigReader.get("rate"));

        }catch (Exception e){
            debugOperation(e);
            throw new IOException();
        }
    }

    public String getExpireTime() {
        return expireTime;
    }

    public boolean isOutOfDate() {
        return SequenceKey.isOutOfDate(expireTime);
    }

    public boolean isExist() {
        return keyFile.exists();
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port > 0 && port <= 65535) {
            this.port = port;
        }
    }

    public double getBalance() {
        return balance;
    }

    public String getName() {
        return this.keyFile.getName();
    }

    public File getFile() {
        return keyFile;
    }

    public void addMib(double mib) {
        balance = balance + mib;
    }

    public synchronized void mineMib(double mib) throws NoMoreNetworkFlowException {
//        System.out.println("mib = " + mib);
        if (balance > 0) {
            balance = balance - mib;
        } else {
            NoMoreNetworkFlowException.throwException(this.keyFile.getName());
        }

    }

    public static boolean saveToFile(SequenceKey sequenceKey) {
        try {
            if (!SequenceKey.isOutOfDate(sequenceKey.expireTime) && sequenceKey.balance > 0) {
                if (sequenceKey.keyFile.exists()) {
                    if (!sequenceKey.keyFile.delete()) {//无法删除
                        myConsole.warn("SK-Manager", "Unable to delete key file: " + sequenceKey.getName());
                        return false;
                    }
                }
                if (!sequenceKey.keyFile.createNewFile()) {//无法创建
                    myConsole.warn("SK-Manager", "Unable to create key file: " + sequenceKey.getName());
                    return false;
                }
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(sequenceKey.keyFile, StandardCharsets.UTF_8));
                bufferedWriter.write("balance=" + sequenceKey.balance);
                bufferedWriter.newLine();
                bufferedWriter.write("expireTime=" + sequenceKey.expireTime);
                bufferedWriter.newLine();
                bufferedWriter.write("port=" + sequenceKey.port);
                bufferedWriter.newLine();
                bufferedWriter.write("rate=" + sequenceKey.rate);
                bufferedWriter.close();
                return true;
            } else {
                if (sequenceKey.keyFile.exists()) {
                    boolean b = sequenceKey.keyFile.delete();
                    if (b) {
                        myConsole.warn("SK-Manager", "Unable to delete key file: " + sequenceKey.getName());
                    }
                }
                return false;
            }
        } catch (Exception e) {
            NeoProxyServer.debugOperation(e);
            return false;
        }
    }

    public double getRate() {
        return rate;
    }
}
