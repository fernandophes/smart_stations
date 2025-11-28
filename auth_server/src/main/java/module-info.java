module auth_server {
    opens br.edu.ufersa.cc.seg.auth.entities;

    requires common;

    requires lombok;
    requires org.slf4j;
    requires jakarta.persistence;
    requires org.hibernate.orm.core;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
}
