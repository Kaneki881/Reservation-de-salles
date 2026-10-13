package com.example.util;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

/** Creates several users, rooms and future reservations in one transaction. */
public class DataInitializer {
    private final EntityManager em;

    public DataInitializer(EntityManager em) { this.em = em; }

    public Seed initData() {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            Utilisateur alice = new Utilisateur("Alice Dupont", "alice.dupont@example.com");
            Utilisateur bob = new Utilisateur("Bob Martin", "bob.martin@example.com");
            Utilisateur chloe = new Utilisateur("Chloé Amrani", "chloe.amrani@example.com");
            Salle a = new Salle("Salle A", 20, "Projecteur et visioconférence");
            Salle b = new Salle("Salle B", 8, "Réunions courtes");
            Salle c = new Salle("Salle C", 40, "Formation");
            for (Utilisateur user : Arrays.asList(alice, bob, chloe)) em.persist(user);
            for (Salle salle : Arrays.asList(a, b, c)) em.persist(salle);
            LocalDateTime base = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
            Reservation r1 = new Reservation(alice, a, base, base.plusHours(2));
            Reservation r2 = new Reservation(bob, b, base.plusHours(4), base.plusHours(5));
            Reservation r3 = new Reservation(chloe, a, base.plusDays(1), base.plusDays(1).plusHours(1));
            for (Reservation reservation : Arrays.asList(r1, r2, r3)) em.persist(reservation);
            tx.commit();
            return new Seed(Arrays.asList(alice.getId(), bob.getId(), chloe.getId()),
                Arrays.asList(a.getId(), b.getId(), c.getId()),
                Arrays.asList(r1.getId(), r2.getId(), r3.getId()), base);
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    public static class Seed {
        public final List<Long> utilisateurs;
        public final List<Long> salles;
        public final List<Long> reservations;
        public final LocalDateTime base;

        Seed(List<Long> utilisateurs, List<Long> salles, List<Long> reservations, LocalDateTime base) {
            this.utilisateurs = utilisateurs;
            this.salles = salles;
            this.reservations = reservations;
            this.base = base;
        }
    }
}
