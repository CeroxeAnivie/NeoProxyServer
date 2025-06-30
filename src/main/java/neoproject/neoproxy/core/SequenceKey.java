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

public class SequenceKey {
    private double rate;//mb
    private String expireTime;//2026/01/01 13:33
    private File keyFile;
    private int port = -1;

    public SequenceKey(File keyFile) {

        try {
            this.keyFile = keyFile;
            readAndSetElementFromFile(keyFile);
        } catch (Exception e) {
            NeoProxyServer.debugOperation(e);
            keyFile.delete();
        }
    }

    public static boolean isOutOfDate(String endTime) {
        // 定义时间格式
        //2023/3/2 13:33
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

        try {
            // 将传入的字符串转换为 LocalDateTime
            LocalDateTime endDateTime = LocalDateTime.parse(endTime, formatter);

            // 获取当前的系统时间
            LocalDateTime currentDateTime = LocalDateTime.now();

            // 比较当前时间与结束时间
            return currentDateTime.isAfter(endDateTime);
        } catch (DateTimeParseException e) {
            // 如果日期格式不正确，输出错误日志并返回 false
            System.err.println("日期格式错误: " + e.getMessage());
            return false; // 可以根据需求选择返回 false 或者抛出异常
        }
    }

    public static void removeVaultOnAll(SequenceKey sequenceKey) {
        NeoProxyServer.sequenceKeyDatabase.remove(sequenceKey);
    }

    public void readAndSetElementFromFile(File vaultFile) throws IOException {
        LineConfigReader lineConfigReader = new LineConfigReader(vaultFile);
        lineConfigReader.load();
        rate = Double.parseDouble(lineConfigReader.get("rate"));
        expireTime = lineConfigReader.get("expireTime");
        if (lineConfigReader.containsKey("port")) {
            port = Integer.parseInt(lineConfigReader.get("port"));
        }

    }

    public String getExpireTime() {
        return expireTime;
    }

    public boolean isOutOfDate() {
        return SequenceKey.isOutOfDate(expireTime);
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        if (port > 0 && port <= 65535) {
            this.port = port;
        }
    }

    public double getRate() {
        return rate;
    }

    public String getName() {
        return this.keyFile.getName();
    }

    public File getFile() {
        return keyFile;
    }

    public void addMib(double mib) {
        rate = rate + mib;
    }

    public synchronized void mineMib(double mib) throws NoMoreNetworkFlowException {
//        System.out.println("mib = " + mib);
        if (rate > 0) {
            rate = rate - mib;
        } else {
            if (keyFile.exists()) {
                keyFile.delete();
                //the exception will auto say
            }
            NoMoreNetworkFlowException.throwException(this.keyFile.getName());
        }

    }

    public void save() {
        try {
            if ((!SequenceKey.isOutOfDate(expireTime)) && rate > 0) {
                keyFile.delete();
                keyFile.createNewFile();
                BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(keyFile, StandardCharsets.UTF_8));
                bufferedWriter.write("rate=" + rate);
                bufferedWriter.newLine();
                bufferedWriter.write("expireTime="+expireTime);
                if (port != -1) {
                    bufferedWriter.newLine();
                    bufferedWriter.write("port=" + port);
                }
                bufferedWriter.close();
            } else {
                if (keyFile.exists()) {
                    keyFile.delete();
                }
            }
        } catch (Exception e) {
            NeoProxyServer.debugOperation(e);
        }
    }
}
