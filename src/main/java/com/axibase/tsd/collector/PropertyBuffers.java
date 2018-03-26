package com.axibase.tsd.collector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

public class PropertyBuffers {
    private ByteBuffer environmentPropertyBuf;
    private ByteBuffer settingsPropertyBuf;
    private ByteBuffer runtimePropertyBuf;
    private ByteBuffer osPropertyBuf;

    public boolean isAllBuffersInitialized() {
        if (environmentPropertyBuf != null && settingsPropertyBuf != null && runtimePropertyBuf != null && osPropertyBuf != null) {
            return true;
        }
        return false;
    }

    public void setEnvironmentPropertyBuf(ByteBuffer environmentPropertyBuf) {
        this.environmentPropertyBuf = environmentPropertyBuf;
    }

    public void setOsPropertyBuf(ByteBuffer osPropertyBuf) {
        this.osPropertyBuf = osPropertyBuf;
    }

    public void setRuntimePropertyBuf(ByteBuffer runtimePropertyBuf) {
        this.runtimePropertyBuf = runtimePropertyBuf;
    }

    public void setSettingsPropertyBuf(ByteBuffer settingsPropertyBuf) {
        this.settingsPropertyBuf = settingsPropertyBuf;
    }

    public void writeAllTo(WritableByteChannel writer) throws IOException{
        writeEnvironmentPropertyBufTo(writer);
        writeRuntimePropertyBufTo(writer);
        writeSettingsPropertyBufTo(writer);
        writeOsPropertyBufTo(writer);
    }

    public void writeEnvironmentPropertyBufTo(WritableByteChannel writer) throws IOException {
        writer.write(environmentPropertyBuf);
        environmentPropertyBuf.rewind();
    }

    public void writeOsPropertyBufTo(WritableByteChannel writer) throws IOException {
        writer.write(osPropertyBuf);
        osPropertyBuf.rewind();
    }


    public void writeRuntimePropertyBufTo(WritableByteChannel writer) throws IOException {
        writer.write(runtimePropertyBuf);
        runtimePropertyBuf.rewind();
    }


    public void writeSettingsPropertyBufTo(WritableByteChannel writer) throws IOException {
        writer.write(settingsPropertyBuf);
        settingsPropertyBuf.rewind();
    }
}
