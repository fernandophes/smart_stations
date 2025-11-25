module datacenter {
    exports br.edu.ufersa.cc.seg;

    opens br.edu.ufersa.cc.seg.datacenter.entities;

    requires common;
    

    requires lombok;
    requires org.slf4j;
    requires org.hibernate.orm.core;
    requires jakarta.persistence;
}
