module datacenter {
    exports br.edu.ufersa.cc.seg;

    opens br.edu.ufersa.cc.seg.datacenter.entities;

    requires common;

    requires lombok;
    requires org.slf4j;
    requires io.javalin;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
}
