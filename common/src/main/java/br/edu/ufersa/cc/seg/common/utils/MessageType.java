package br.edu.ufersa.cc.seg.common.utils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum MessageType {

    /*
     * Requests
     */
    REGISTER_SERVER,
    LOCATE_SERVER,
    REMOVE_SERVER,

    /*
     * Responses
     */
    OK,
    ERROR,
    ;

}
