package com.lannooo.device;

public interface Device {
    default String shortDesc() {
        return "<Unknown>";
    }
}
