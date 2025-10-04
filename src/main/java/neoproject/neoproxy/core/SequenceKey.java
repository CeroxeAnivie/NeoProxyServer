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
    private double balance;//mb
    private String expireTime;//2026/01/01-13:33
    private final File keyFile;
    private int port = -1;
    private double rate = 0;//mbps

    public SequenceKey(File keyFile) throws IOException {
        this.keyFile = keyFile;
        readAndSetElementFromFile(this, keyFile);

    }

    private SequenceKey(String name, double balance, String expireTime, int port, int rate) {
        this.keyFile = new File(KEY_FILE_DIR + File.separator + name);
        this.balance = balance;
        this.expireTime = expireTime;
        this.port = port;
        this.rate = rate;
    }

    public static boolean createNewKey(String name, double balance, String expireTime, int port, int rate) {
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
        String endTime1 = endTime.replaceAll("-", " ");
        // 定义时间格式
        //2023/03/02 13:33
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

        try {
            // 将传入的字符串转换为 LocalDateTime
            LocalDateTime endDateTime = LocalDateTime.parse(endTime1, formatter);

            // 获取当前的系统时间
            LocalDateTime currentDateTime = LocalDateTime.now();

            // 比较当前时间与结束时间
            return currentDateTime.isAfter(endDateTime);
        } catch (DateTimeParseException e) {
            // 如果日期格式不正确，输出错误日志并返回 false
            sayError("SequenceKey", e.getMessage());
            return false; // 可以根据需求选择返回 false 或者抛出异常
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
            sequenceKey.rate = Integer.parseInt(lineConfigReader.get("rate"));

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
