package com.example.service;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.model.Utilisateur;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.OptimisticLockException;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

public class ReservationService {
    private static final Logger LOGGER = Logger.getLogger(ReservationService.class.getName());
    private final EntityManagerFactory emf;

    public ReservationService(EntityManagerFactory emf) { this.emf = emf; }

    public Reservation creer(Long utilisateurId, Long salleId, LocalDateTime debut, LocalDateTime fin) {
        verifierDates(debut, fin);
        return transaction(em -> {
            // The room lock serializes overlap checks and inserts for this room.
            Salle salle = salleVerrouillee(em, salleId);
            Utilisateur utilisateur = em.find(Utilisateur.class, utilisateurId);
            if (utilisateur == null) throw new IllegalArgumentException("Utilisateur introuvable : " + utilisateurId);
            verifierDisponibilite(em, salleId, debut, fin, null);
            Reservation reservation = new Reservation(utilisateur, salle, debut, fin);
            em.persist(reservation);
            LOGGER.info("Réservation créée : " + reservation.getId());
            return reservation;
        });
    }

    public Reservation modifier(Long id, int versionAttendue, LocalDateTime debut, LocalDateTime fin) {
        verifierDates(debut, fin);
        return transaction(em -> {
            Reservation reservation = em.find(Reservation.class, id);
            if (reservation == null) throw new IllegalArgumentException("Réservation introuvable : " + id);
            salleVerrouillee(em, reservation.getSalle().getId());
            if (reservation.getVersion() != versionAttendue) {
                throw new OptimisticLockException("Réservation modifiée entre-temps : " + id);
            }
            verifierDisponibilite(em, reservation.getSalle().getId(), debut, fin, id);
            reservation.setDateDebut(debut);
            reservation.setDateFin(fin);
            em.flush();
            LOGGER.info("Réservation modifiée : " + id);
            return reservation;
        });
    }

    public void supprimer(Long id, int versionAttendue) {
        transaction(em -> {
            Reservation reservation = em.find(Reservation.class, id);
            if (reservation == null) throw new IllegalArgumentException("Réservation introuvable : " + id);
            salleVerrouillee(em, reservation.getSalle().getId());
            if (reservation.getVersion() != versionAttendue) {
                throw new OptimisticLockException("Réservation modifiée entre-temps : " + id);
            }
            em.remove(reservation);
            LOGGER.info("Réservation supprimée : " + id);
            return null;
        });
    }

    public Reservation trouverParId(Long id) {
        EntityManager em = emf.createEntityManager();
        try { return em.find(Reservation.class, id); }
        finally { em.close(); }
    }

    public List<Reservation> listerParSalleEtDateJPQL(String nomSalle, LocalDateTime apres) {
        EntityManager em = emf.createEntityManager();
        try {
            return em.createQuery(
                "SELECT r FROM Reservation r JOIN FETCH r.salle JOIN FETCH r.utilisateur " +
                "WHERE r.salle.nom = :nom AND r.dateDebut > :apres ORDER BY r.dateDebut", Reservation.class)
                .setParameter("nom", nomSalle).setParameter("apres", apres).getResultList();
        } finally { em.close(); }
    }

    public List<Reservation> listerParSalleEtDateCriteria(String nomSalle, LocalDateTime apres) {
        EntityManager em = emf.createEntityManager();
        try {
            CriteriaBuilder cb = em.getCriteriaBuilder();
            CriteriaQuery<Reservation> query = cb.createQuery(Reservation.class);
            Root<Reservation> root = query.from(Reservation.class);
            root.fetch("salle");
            root.fetch("utilisateur");
            Predicate filtre = cb.and(
                cb.equal(root.get("salle").get("nom"), nomSalle),
                cb.greaterThan(root.<LocalDateTime>get("dateDebut"), apres));
            query.select(root).where(filtre).orderBy(cb.asc(root.get("dateDebut")));
            return em.createQuery(query).getResultList();
        } finally { em.close(); }
    }

    private Salle salleVerrouillee(EntityManager em, Long id) {
        Salle salle = em.find(Salle.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (salle == null) throw new IllegalArgumentException("Salle introuvable : " + id);
        return salle;
    }

    private void verifierDisponibilite(EntityManager em, Long salleId,
                                       LocalDateTime debut, LocalDateTime fin, Long exclureId) {
        TypedQuery<Long> query = em.createQuery(
            "SELECT COUNT(r) FROM Reservation r WHERE r.salle.id = :salle " +
            "AND r.dateDebut < :fin AND r.dateFin > :debut " +
            "AND (:exclure IS NULL OR r.id <> :exclure)", Long.class);
        long conflits = query.setParameter("salle", salleId).setParameter("debut", debut)
            .setParameter("fin", fin).setParameter("exclure", exclureId).getSingleResult();
        if (conflits > 0) throw new ReservationConflictException("Salle déjà réservée sur ce créneau");
    }

    private void verifierDates(LocalDateTime debut, LocalDateTime fin) {
        if (debut == null || fin == null || !fin.isAfter(debut)) {
            throw new IllegalArgumentException("La date de fin doit suivre la date de début");
        }
    }

    private <T> T transaction(Work<T> work) {
        EntityManager em = emf.createEntityManager();
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            T result = work.execute(em);
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            LOGGER.warning("Opération de réservation annulée : " + e.getMessage());
            throw e;
        } finally { em.close(); }
    }

    private interface Work<T> { T execute(EntityManager em); }
}
