package com.steamdeck.mobile.core.xserver.requests;

import static com.steamdeck.mobile.core.xserver.XClientRequestHandler.RESPONSE_CODE_SUCCESS;

import com.steamdeck.mobile.core.xconnector.XInputStream;
import com.steamdeck.mobile.core.xconnector.XOutputStream;
import com.steamdeck.mobile.core.xconnector.XStreamLock;
import com.steamdeck.mobile.core.xserver.XClient;
import com.steamdeck.mobile.core.xserver.errors.XRequestError;

import java.io.IOException;

public abstract class FontRequests {
    public static void openFont(XClient client, XInputStream inputStream, XOutputStream outputStream) throws XRequestError {
        inputStream.skip(4);
        int length = inputStream.readShort();
        inputStream.skip(2);
        String name = inputStream.readString8(length);
        if (!name.equals("cursor")) throw new UnsupportedOperationException("OpenFont supports only name: cursor.");
    }

    public static void listFonts(XClient client, XInputStream inputStream, XOutputStream outputStream) throws IOException, XRequestError {
        inputStream.skip(2);
        short patternLength = inputStream.readShort();
        inputStream.readString8(patternLength);

        try (XStreamLock lock = outputStream.lock()) {
            outputStream.writeByte(RESPONSE_CODE_SUCCESS);
            outputStream.writeByte((byte)0);
            outputStream.writeShort(client.getSequenceNumber());
            outputStream.writeInt(0);
            outputStream.writeShort((short)0);
            outputStream.writePad(22);
        }
    }
}
