package com.et.server.repository;

import com.et.server.entity.Member;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MemberRepository {

    @PersistenceContext
    EntityManager em;

    public Long save(Member member) {
        if (member.getId() == null) {
            em.persist(member);
        } else {
            em.merge(member);
        }
        return member.getId();
    }

    public Member findById(Long id) {
        return em.find(Member.class, id);
    }

    public void delete(Member member) {
        em.remove(member);
    }

    @SuppressWarnings("unchecked")
    public List<Member> findAll(Long homeId) {
        return em.createQuery("from Member m where m.home.id = :homeId")
                .setParameter("homeId", homeId)
                .getResultList();
    }
}
