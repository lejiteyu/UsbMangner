package lyon.usb.usbdevicemanager.USBManager;

import android.hardware.usb.UsbDevice;

public class UsbStatusChangeEvent {
    public boolean isConnected = false;
    public boolean isGetPermission = false;
    public UsbDevice usbDevice;

    public String filePath = "";


    public UsbStatusChangeEvent(boolean isConnected) {
        this.isConnected = isConnected;
    }

    public UsbStatusChangeEvent(String filePath) {
        this.filePath = filePath;
    }

    public UsbStatusChangeEvent(boolean isGetPermission, UsbDevice usbDevice) {

        this.isGetPermission = isGetPermission;
        this.usbDevice = usbDevice;
    }

}
