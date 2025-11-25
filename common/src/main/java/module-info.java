module common {
    exports br.edu.ufersa.cc.seg.common.dto;
    exports br.edu.ufersa.cc.seg.common.utils;
    exports br.edu.ufersa.cc.seg.common.crypto;
    exports br.edu.ufersa.cc.seg.common.network;
    exports br.edu.ufersa.cc.seg.common.factories;

    opens br.edu.ufersa.cc.seg.common.network to com.fasterxml.jackson.databind;
    opens br.edu.ufersa.cc.seg.common.utils to com.fasterxml.jackson.databind;

    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires org.slf4j;
    requires lombok;
}
