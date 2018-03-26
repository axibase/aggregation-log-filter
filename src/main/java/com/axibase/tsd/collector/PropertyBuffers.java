package com.axibase.tsd.collector;

import java.nio.ByteBuffer;

public class PropertyBuffers {
    private ByteBuffer environmentPropertyBuf;
    private ByteBuffer settingsPropertyBuf;
    private ByteBuffer runtimePropertyBuf;
    private ByteBuffer osPropertyBuf;

    public PropertyBuffers() {
    }

    public ByteBuffer getEnvironmentPropertyBuf() {
        return environmentPropertyBuf;
    }

    public void setEnvironmentPropertyBuf(ByteBuffer environmentPropertyBuf) {
        this.environmentPropertyBuf = environmentPropertyBuf;
    }

    public ByteBuffer getSettingsPropertyBuf() {
        return settingsPropertyBuf;
    }

    public void setSettingsPropertyBuf(ByteBuffer settingsPropertyBuf) {
        this.settingsPropertyBuf = settingsPropertyBuf;
    }

    public ByteBuffer getRuntimePropertyBuf() {
        return runtimePropertyBuf;
    }

    public void setRuntimePropertyBuf(ByteBuffer runtimePropertyBuf) {
        this.runtimePropertyBuf = runtimePropertyBuf;
    }

    public ByteBuffer getOsPropertyBuf() {
        return osPropertyBuf;
    }

    public void setOsPropertyBuf(ByteBuffer osPropertyBuf) {
        this.osPropertyBuf = osPropertyBuf;
    }

    public boolean hasNull() {
        if (environmentPropertyBuf != null && settingsPropertyBuf != null && runtimePropertyBuf != null && osPropertyBuf != null) {
            return true;
        }
        return false;
    }
}
