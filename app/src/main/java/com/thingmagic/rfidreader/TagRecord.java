package com.thingmagic.rfidreader;

import java.util.Arrays;

public class TagRecord {
    public int readCount = 0;
    String epcString;
    String tid;

    byte[] data = new byte[0];

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public int getReadCount() {
        return readCount;
    }

    public void setReadCount(int readCount) {
        this.readCount = readCount;
    }

    public String getEpcString() {
        return epcString;
    }

    public void setEpcString(String epcString) {
        this.epcString = epcString;
    }

    public String getTid() {
        return tid;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    @Override
    public String toString() {
        return "TagRecord{" +
                "readCount=" + readCount +
                ", epcString='" + epcString + '\'' +
                ", tid='" + tid + '\'' +
                '}';
    }
}
