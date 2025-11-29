module client {
    exports br.edu.ufersa.cc.seg;

    requires common;

    requires lombok;
    requires org.slf4j;
    requires com.fasterxml.jackson.databind;
    requires org.apache.httpcomponents.httpcore;
    requires org.apache.httpcomponents.httpclient;
}
