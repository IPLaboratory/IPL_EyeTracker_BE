package com.et.server.repository;

import com.et.server.entity.Home;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HomeRepository {

    @PersistenceContext
    EntityManager em;

    public Long save(Home home) {
        if (home.getId() == null) {
            em.persist(home);
        } else {
            em.merge(home);
        }
        return home.getId();
    }

    public Home findById(Long id) {
        return em.find(Home.class, id);
    }

    public List<Home> findByName(String name) {
        return em.createQuery("select m from Home m where m.name = :name", Home.class)
                .setParameter("name", name).getResultList();
    }
}
