package com.lannooo.server;

import com.google.gson.Gson;

import java.util.LinkedHashMap;
import java.util.Map;

public class MessageRequest {
    private String subtype;
    private Map<String, Object> data;

    public MessageRequest(String subtype) {
        this.subtype = subtype;
        this.data = new LinkedHashMap<>();
    }

    public void put(String key, Object value) {
        this.data.put(key, value);
    }

    public String getSubtype() {
        return subtype;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public String toJsonString() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public static MessageRequest fromJsonString(String jsonString) {
        Gson gson = new Gson();
        return gson.fromJson(jsonString, MessageRequest.class);
    }

    @Override
    public String toString() {
        return this.toJsonString();
    }
}
