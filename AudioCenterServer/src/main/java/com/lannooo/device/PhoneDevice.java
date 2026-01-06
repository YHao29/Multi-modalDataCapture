package com.lannooo.device;

import java.util.Map;

public class PhoneDevice implements Device {
    private String id;
    private String name;
    private String remoteAddress;
    private String localAddress;
    private Map<String, Object> extra;

    public PhoneDevice(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setRemoteAddress(String remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public String getLocalAddress() {
        return localAddress;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    @Override
    public String toString() {
        return shortDesc();
    }

    @Override
    public String shortDesc() {
        return "<Phone> " + name + " (" + id + ")";
    }
}
