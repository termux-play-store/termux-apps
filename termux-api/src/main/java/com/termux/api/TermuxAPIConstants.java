package com.termux.api;

public class TermuxAPIConstants {

    public static final String TERMUX_PACKAGE_NAME = "com.termux";

    public static final String TERMUX_API_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".api";

    /**
     * Termux:API Receiver name.
     */
    public static final String TERMUX_API_RECEIVER_NAME = TERMUX_API_PACKAGE_NAME + ".TermuxApiReceiver";

    /**
     * The Uri authority for Termux:API app file shares
     */
    public static final String TERMUX_API_FILE_SHARE_URI_AUTHORITY = TERMUX_PACKAGE_NAME + ".sharedfiles";

}
