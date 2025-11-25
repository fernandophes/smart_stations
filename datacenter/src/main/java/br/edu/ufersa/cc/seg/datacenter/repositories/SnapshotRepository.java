package br.edu.ufersa.cc.seg.datacenter.repositories;

import java.util.List;

import br.edu.ufersa.cc.seg.common.utils.Element;
import br.edu.ufersa.cc.seg.datacenter.entities.Snapshot;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class SnapshotRepository {

    private static EntityManagerFactory emf = Persistence.createEntityManagerFactory("snapshots");

    public List<Snapshot> listAll() {
        final var em = emf.createEntityManager();
        final var query = em.createQuery("select c from Snapshot c", Snapshot.class);
        final var result = query.getResultList();

        em.close();
        return result;
    }

    public List<Snapshot> listByElement(final Element element) {
        final var em = emf.createEntityManager();
        final var query = em.createQuery(
                "SELECT c FROM Snapshot c WHERE c.element = :element", Snapshot.class);
        query.setParameter("element", element);

        final var result = query.getResultList();
        em.close();
        return result;
    }

    public long countAll() {
        final var em = emf.createEntityManager();
        final var count = em.createQuery("SELECT COUNT(c) FROM Snapshot c", Long.class)
                .getSingleResult();
        em.close();
        return count;
    }

    public void create(final Snapshot snapshot) {
        final var em = emf.createEntityManager();
        em.getTransaction().begin();

        em.persist(snapshot);

        em.getTransaction().commit();
        em.close();
    }

}
