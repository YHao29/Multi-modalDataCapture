package com.lannooo.shell.command;

import com.lannooo.common.AppConstants;
import com.lannooo.common.ArgsUtils;
import com.lannooo.device.Device;
import com.lannooo.device.DeviceManager;
import com.lannooo.device.PhoneDevice;
import com.lannooo.shell.ShellHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.command.annotation.Command;
import org.springframework.shell.command.annotation.Option;

import java.util.Map;

@Command(command = "device", description = "Device commands")
public class DeviceCommands {
    @Autowired
    ShellHelper shellHelper;

    @Autowired
    DeviceManager deviceManager;

    @Command(command = "function", description = "Enable/Disable device's functions")
    public void enableDevice(
            @Option(longNames = "device", shortNames = 'd', required = true) String deviceId,
            @Option(longNames = "capability", shortNames = 'c', required = true,
                    description = "capture|playback|all") String capability,
            @Option(longNames = "enable", shortNames = 'e', defaultValue = "on") String enableAction) {
        if (!ArgsUtils.checkOptionIn(capability, AppConstants.VALID_DEVICE_CAPABILITIES)) {
            shellHelper.printError("Invalid capability " + capability);
            return;
        }
        if (!ArgsUtils.checkOptionIn(enableAction, AppConstants.VALID_ENABLE_ACTIONS)) {
            shellHelper.printError("Invalid enable action " + enableAction);
            return;
        }
        if (deviceManager.getDevices().isEmpty()) {
            shellHelper.printError("No devices found");
            return;
        }

        if (deviceManager.updateDeviceFunctions(deviceId, capability, enableAction)) {
            shellHelper.printSuccess("Device function: " + capability + " -> " + enableAction);
        } else {
            shellHelper.printError("Device function update failed");
        }
    }

    @Command(command = "list", description = "List all devices")
    public void listDevices(
            @Option(longNames = "detail",
                    shortNames = 'l',
                    defaultValue = "false") boolean inDetail) {
        Map<String, Device> devices = deviceManager.getDevices();
        Map<String, Boolean> captureStatus = deviceManager.getCaptureStatus();
        Map<String, Boolean> playbackStatus = deviceManager.getPlaybackStatus();

        if (devices.isEmpty()) {
            System.out.println("No devices found");
            return;
        }

        System.out.println("--------- Devices ----------");
        devices.forEach((id, device) -> {
            String status = (captureStatus.get(id) ? "[R:on, " : "[R:off, ");
            status += (playbackStatus.get(id) ? "P:on]" : "P:off]");
            System.out.println(status + " " + device.shortDesc());
            if (inDetail) {
                if (device instanceof PhoneDevice) {
                    System.out.println("\tLocal Address:\t\t" + ((PhoneDevice) device).getLocalAddress());
                    System.out.println("\tRemote Address:\t\t" + ((PhoneDevice) device).getRemoteAddress());
                    Map<String, Object> extraInfo = ((PhoneDevice) device).getExtra();
                    extraInfo.forEach((key, value) -> System.out.println("\t" + key + ":\t\t" + value));
                }
            }
        });
        System.out.println("----------------------------");
    }



}
