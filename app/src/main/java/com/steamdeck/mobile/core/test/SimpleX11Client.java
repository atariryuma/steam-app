package com.steamdeck.mobile.core.test;

import android.content.Context;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Simple X11 client for testing XServer connectivity.
 *
 * Best practice: Minimal connection test approach
 * - Connect to Unix domain socket
 * - Perform X11 handshake
 * - Verify protocol version
 *
 * References:
 * - X Window System Protocol: https://www.x.org/releases/X11R7.7/doc/xproto/x11protocol.html
 * - Minimal test approach: https://gist.github.com/bert/875200
 */
public class SimpleX11Client {
    private static final String TAG = "SimpleX11Client";

    // X11 protocol constants
    private static final byte BYTE_ORDER_LSB = 0x6C; // 'l' for little-endian
    private static final short PROTOCOL_MAJOR_VERSION = 11;
    private static final short PROTOCOL_MINOR_VERSION = 0;

    private final Context context;

    public SimpleX11Client(Context context) {
        this.context = context;
    }

    /**
     * Test connection to XServer.
     *
     * Best practice: Minimal test - just verify handshake succeeds.
     * No window creation needed for basic connectivity test.
     *
     * @return Test result message
     * @throws Exception if connection fails
     */
    public String testConnection() throws Exception {
        Log.d(TAG, "Starting X11 connectivity test");

        // Find XServer socket
        String socketPath = findXServerSocket();
        Log.i(TAG, "Found X11 socket at: " + socketPath);

        // Test connection with handshake
        try (UnixDomainSocketChannel channel = UnixDomainSocketChannel.open(socketPath);
             DataInputStream input = new DataInputStream(channel.getInputStream());
             DataOutputStream output = new DataOutputStream(channel.getOutputStream())) {

            // Send X11 connection setup
            sendConnectionSetup(output);

            // Receive response
            ConnectionSetupResponse response = receiveConnectionSetup(input);

            if (!response.success) {
                throw new Exception("X11 handshake failed: " + response.reason);
            }

            // Success - return info
            Log.i(TAG, "X11 connection test successful");
            return formatSuccessMessage(response);

        } catch (Exception e) {
            Log.e(TAG, "X11 connection test failed", e);
            throw new Exception("XServer connection test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Test window display (visual verification).
     * Best practice: Create simple window, display 3 seconds, cleanup.
     *
     * @return Test result message
     * @throws Exception if window creation/display fails
     */
    public String testWindowDisplay() throws Exception {
        Log.d(TAG, "Starting X11 window display test");

        // Find XServer socket
        String socketPath = findXServerSocket();
        Log.i(TAG, "Found X11 socket at: " + socketPath);

        // Test window display
        try (UnixDomainSocketChannel channel = UnixDomainSocketChannel.open(socketPath);
             DataInputStream input = new DataInputStream(channel.getInputStream());
             DataOutputStream output = new DataOutputStream(channel.getOutputStream())) {

            // Step 1: Connection setup
            sendConnectionSetup(output);
            ConnectionSetupResponse response = receiveConnectionSetup(input);

            if (!response.success) {
                throw new Exception("X11 handshake failed: " + response.reason);
            }

            Log.i(TAG, "Connected to XServer, creating window...");

            // Step 2: Create window
            int windowId = createWindow(output, response);
            Log.i(TAG, "Window created with ID: 0x" + Integer.toHexString(windowId));

            // Step 3: Map window (make visible)
            mapWindow(output, windowId);
            Log.i(TAG, "Window mapped (displayed)");

            // Step 4: Flush to ensure display
            flushRequests(output);

            // Step 5: Wait 3 seconds for visual confirmation
            Thread.sleep(3000);

            // Step 6: Cleanup - destroy window
            destroyWindow(output, windowId);
            flushRequests(output);
            Log.i(TAG, "Window destroyed");

            // Success
            return String.format(
                "✓ Window display test successful!\n\n" +
                "Window ID: 0x%08X\n" +
                "Screen: %d × %d pixels\n" +
                "Display duration: 3 seconds\n\n" +
                "Visual verification complete.",
                windowId,
                response.screenWidthInPixels,
                response.screenHeightInPixels
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception("Window display test interrupted", e);
        } catch (Exception e) {
            Log.e(TAG, "X11 window display test failed", e);
            throw new Exception("Window display test failed: " + e.getMessage(), e);
        }
    }

    /**
     * Find XServer socket file.
     * Best practice: Check expected location first, then fallbacks.
     */
    private String findXServerSocket() throws Exception {
        // Primary path: winlator rootfs
        File appFilesDir = context.getFilesDir();
        File socketFile = new File(appFilesDir, "winlator/rootfs/tmp/.X11-unix/X0");

        if (socketFile.exists()) {
            return socketFile.getAbsolutePath();
        }

        // Fallback: try alternative locations
        String[] alternatives = {
            new File(appFilesDir.getParentFile(), ".X11-unix/X0").getAbsolutePath(),
            "/tmp/.X11-unix/X0"
        };

        for (String path : alternatives) {
            if (new File(path).exists()) {
                Log.d(TAG, "Found socket at alternative path: " + path);
                return path;
            }
        }

        // Not found
        throw new Exception(
            "XServer socket not found.\n\n" +
            "Expected location:\n" +
            socketFile.getAbsolutePath() + "\n\n" +
            "Make sure XServer is running:\n" +
            "Settings → Steam Client → Open Steam"
        );
    }

    /**
     * Format success message with server info.
     */
    private String formatSuccessMessage(ConnectionSetupResponse response) {
        return String.format(
            "✓ XServer is running!\n\n" +
            "Protocol: X11.%d (release %d)\n" +
            "Vendor: %s\n" +
            "Screen: %d × %d pixels\n" +
            "Root Window: 0x%08X\n\n" +
            "Connection test successful.",
            response.protocolMajorVersion,
            response.protocolMinorVersion,
            response.vendor,
            response.screenWidthInPixels,
            response.screenHeightInPixels,
            response.rootWindowId
        );
    }

    /**
     * Send X11 connection setup request.
     */
    private void sendConnectionSetup(DataOutputStream output) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(BYTE_ORDER_LSB);          // byte-order (LSB first)
        buffer.put((byte) 0);                // pad
        buffer.putShort(PROTOCOL_MAJOR_VERSION); // protocol-major-version
        buffer.putShort(PROTOCOL_MINOR_VERSION); // protocol-minor-version
        buffer.putShort((short) 0);          // authorization-protocol-name-length
        buffer.putShort((short) 0);          // authorization-protocol-data-length
        buffer.putShort((short) 0);          // pad

        output.write(buffer.array());
        output.flush();
    }

    /**
     * Receive X11 connection setup response.
     */
    private ConnectionSetupResponse receiveConnectionSetup(DataInputStream input) throws Exception {
        ConnectionSetupResponse response = new ConnectionSetupResponse();

        byte status = input.readByte();
        response.success = (status == 1);

        if (!response.success) {
            byte reasonLength = input.readByte();
            response.protocolMajorVersion = Short.reverseBytes(input.readShort());
            response.protocolMinorVersion = Short.reverseBytes(input.readShort());
            short additionalDataLength = Short.reverseBytes(input.readShort());

            byte[] reasonBytes = new byte[reasonLength];
            input.readFully(reasonBytes);
            response.reason = new String(reasonBytes);
            return response;
        }

        // Skip to important fields
        input.skipBytes(1); // unused
        response.protocolMajorVersion = Short.reverseBytes(input.readShort());
        response.protocolMinorVersion = Short.reverseBytes(input.readShort());
        short additionalDataLength = Short.reverseBytes(input.readShort());

        // Read release number
        input.skipBytes(4);

        // Read resource ID base and mask
        response.resourceIdBase = Integer.reverseBytes(input.readInt());
        response.resourceIdMask = Integer.reverseBytes(input.readInt());

        // Skip motion buffer size
        input.skipBytes(4);

        // Read vendor length
        short vendorLength = Short.reverseBytes(input.readShort());

        // Read maximum request length
        input.skipBytes(2);

        // Read number of screens
        byte numberOfScreens = input.readByte();

        // Skip formats count
        input.skipBytes(1);

        // Skip image byte order, bitmap format bit order, bitmap format scanline unit/pad
        input.skipBytes(4);

        // Skip min/max keycode
        input.skipBytes(2);

        // Skip pad
        input.skipBytes(4);

        // Read vendor string
        byte[] vendorBytes = new byte[vendorLength];
        input.readFully(vendorBytes);
        response.vendor = new String(vendorBytes);

        // Pad to 4-byte boundary
        int vendorPad = (4 - (vendorLength % 4)) % 4;
        input.skipBytes(vendorPad);

        // Skip pixmap formats
        // (We'll skip this for simplicity)

        // Read screen information
        if (numberOfScreens > 0) {
            response.rootWindowId = Integer.reverseBytes(input.readInt());
            input.skipBytes(4); // default colormap
            int whitePixel = Integer.reverseBytes(input.readInt());
            int blackPixel = Integer.reverseBytes(input.readInt());
            input.skipBytes(4); // current input masks
            response.screenWidthInPixels = Short.reverseBytes(input.readShort()) & 0xFFFF;
            response.screenHeightInPixels = Short.reverseBytes(input.readShort()) & 0xFFFF;
            input.skipBytes(4); // width/height in millimeters
            input.skipBytes(2); // min/max installed maps
            response.rootVisualId = Integer.reverseBytes(input.readInt());
            // Skip rest of screen data
        }

        return response;
    }

    /**
     * Create a window.
     * X11 protocol: CreateWindow opcode = 1
     */
    private int createWindow(DataOutputStream output, ConnectionSetupResponse response) throws Exception {
        // Generate window ID from resource base
        int windowId = response.resourceIdBase;

        ByteBuffer buffer = ByteBuffer.allocate(32);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 1);           // opcode: CreateWindow
        buffer.put((byte) 0);           // depth: copy from parent
        buffer.putShort((short) 8);     // request length: 8 * 4 = 32 bytes

        buffer.putInt(windowId);        // window ID
        buffer.putInt(response.rootWindowId); // parent window

        buffer.putShort((short) 0);     // x position
        buffer.putShort((short) 0);     // y position
        buffer.putShort((short) 400);   // width
        buffer.putShort((short) 300);   // height

        buffer.putShort((short) 0);     // border width
        buffer.putShort((short) 1);     // class: InputOutput

        buffer.putInt(response.rootVisualId); // visual ID
        buffer.putInt(0);               // value mask (no attributes)

        output.write(buffer.array());
        return windowId;
    }

    /**
     * Map window (make it visible).
     * X11 protocol: MapWindow opcode = 8
     */
    private void mapWindow(DataOutputStream output, int windowId) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 8);           // opcode: MapWindow
        buffer.put((byte) 0);           // unused
        buffer.putShort((short) 2);     // request length: 2 * 4 = 8 bytes
        buffer.putInt(windowId);        // window ID

        output.write(buffer.array());
    }

    /**
     * Destroy window.
     * X11 protocol: DestroyWindow opcode = 4
     */
    private void destroyWindow(DataOutputStream output, int windowId) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 4);           // opcode: DestroyWindow
        buffer.put((byte) 0);           // unused
        buffer.putShort((short) 2);     // request length: 2 * 4 = 8 bytes
        buffer.putInt(windowId);        // window ID

        output.write(buffer.array());
    }

    /**
     * Flush requests to server.
     */
    private void flushRequests(DataOutputStream output) throws Exception {
        output.flush();
    }

    /**
     * Connection setup response data.
     */
    private static class ConnectionSetupResponse {
        boolean success;
        String reason;
        short protocolMajorVersion;
        short protocolMinorVersion;
        int resourceIdBase;
        int resourceIdMask;
        String vendor;
        int rootWindowId;
        int rootVisualId;
        int screenWidthInPixels;
        int screenHeightInPixels;

        @Override
        public String toString() {
            if (!success) {
                return "Failed: " + reason;
            }
            return String.format("Success (X%d.%d, vendor=%s, root=0x%08X)",
                protocolMajorVersion, protocolMinorVersion, vendor, rootWindowId);
        }
    }

    /**
     * Unix domain socket wrapper with AutoCloseable support.
     * Best practice: Implements AutoCloseable for try-with-resources.
     */
    private static class UnixDomainSocketChannel implements AutoCloseable {
        private final android.net.LocalSocket socket;

        private UnixDomainSocketChannel(String path) throws Exception {
            socket = new android.net.LocalSocket();
            android.net.LocalSocketAddress address = new android.net.LocalSocketAddress(
                path,
                android.net.LocalSocketAddress.Namespace.FILESYSTEM
            );
            socket.connect(address);
        }

        public static UnixDomainSocketChannel open(String path) throws Exception {
            return new UnixDomainSocketChannel(path);
        }

        public java.io.InputStream getInputStream() throws Exception {
            return socket.getInputStream();
        }

        public java.io.OutputStream getOutputStream() throws Exception {
            return socket.getOutputStream();
        }

        @Override
        public void close() throws Exception {
            if (socket != null) {
                socket.close();
            }
        }
    }
}
