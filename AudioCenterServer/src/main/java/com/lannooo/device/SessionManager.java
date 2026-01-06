package com.lannooo.device;

import org.springframework.stereotype.Component;


@Component
public class SessionManager {
    private volatile String expKey;

    public void setExpKey(String expKey) {
        this.expKey = expKey;
    }

    public void create(String key) {
        setExpKey(key);
    }

    public void close() {
        setExpKey(null);
    }

    public String getExpKey() {
        return expKey;
    }
}
