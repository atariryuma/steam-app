package com.steamdeck.mobile.core.util;

import java.io.File;

public interface OnExtractFileListener {
    File onExtractFile(File destination, long size);
}
