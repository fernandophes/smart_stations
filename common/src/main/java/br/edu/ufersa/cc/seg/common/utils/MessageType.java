package br.edu.ufersa.cc.seg.common.utils;

import lombok.Getter;

@Getter
public enum MessageType {

    /*
     * Requests
     */
    REGISTER_SERVER,
    LOCATE_SERVER,
    REMOVE_SERVER,
    SEND_SNAPSHOT,
    STORE_SNAPSHOT,
    AUTHENTICATE,
    USE_SYMMETRIC,

    /*
     * Responses
     */
    OK,
    ERROR,
    UNAUTHORIZED,
    ;

}
