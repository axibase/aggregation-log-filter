package com.axibase.tsd.collector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.charset.StandardCharsets;

class PropertyBuffers {
    private ByteBuffer environmentPropertyBuf;
    private ByteBuffer settingsPropertyBuf;
    private ByteBuffer runtimePropertyBuf;
    private ByteBuffer osPropertyBuf;

    private ByteBuffer allocate(StringBuilder propertyCommand){
        byte[] bytes = propertyCommand.toString().getBytes(StandardCharsets.UTF_8);
        ByteBuffer commandBuffer = ByteBuffer.allocate(bytes.length).put(bytes);
        commandBuffer.rewind();
        return commandBuffer;
    }

    public boolean isAllBuffersInitialized() {
        if (environmentPropertyBuf != null && settingsPropertyBuf != null && runtimePropertyBuf != null && osPropertyBuf != null) {
            return true;
        }
        return false;
    }

    public void initEnvironmentPropertyBuf(StringBuilder propertyCommand) {
        this.environmentPropertyBuf = allocate(propertyCommand);
    }

    public void initOsPropertyBuf(StringBuilder propertyCommand) {
        this.osPropertyBuf = allocate(propertyCommand);
    }

    public void initRuntimePropertyBuf(StringBuilder propertyCommand) {
        this.runtimePropertyBuf = allocate(propertyCommand);
    }

    public void initSettingsPropertyBuf(StringBuilder propertyCommand) {
        this.settingsPropertyBuf = allocate(propertyCommand);
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
