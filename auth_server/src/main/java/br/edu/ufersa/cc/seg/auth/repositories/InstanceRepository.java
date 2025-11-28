package br.edu.ufersa.cc.seg.auth.repositories;

import java.util.List;
import java.util.Optional;

import br.edu.ufersa.cc.seg.auth.entities.Instance;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;

public class InstanceRepository {

    private EntityManagerFactory emf = Persistence.createEntityManagerFactory("instances");

    public List<Instance> listAll() {
        final var em = emf.createEntityManager();
        final var query = em.createQuery("select c from Instance c", Instance.class);
        final var result = query.getResultList();

        em.close();
        return result;
    }

    public long countAll() {
        final var em = emf.createEntityManager();
        final var count = em.createQuery("SELECT COUNT(c) FROM Instance c", Long.class)
                .getSingleResult();
        em.close();
        return count;
    }

    public Optional<Instance> getByIdentifier(final String identifier) {
        final var em = emf.createEntityManager();
        final var query = em.createQuery("select c from Instance c where c.identifier = :identifier", Instance.class);
        query.setParameter("identifier", identifier);
        final var result = query.getSingleResultOrNull();

        em.close();
        return Optional.ofNullable(result);
    }

    public Optional<Instance> getByIdentifierAndSecret(final String identifier, final String secret) {
        final var em = emf.createEntityManager();
        final var query = em.createQuery(
                "select c from Instance c where c.identifier = :identifier and c.secret = :secret", Instance.class);
        query.setParameter("identifier", identifier);
        query.setParameter("secret", secret);
        final var result = query.getSingleResultOrNull();

        em.close();
        return Optional.ofNullable(result);
    }

    public void create(final Instance instance) {
        final var em = emf.createEntityManager();
        em.getTransaction().begin();

        em.persist(instance);

        em.getTransaction().commit();
        em.close();
    }

}
