package com.example;

import com.example.model.Reservation;
import com.example.model.Salle;
import com.example.service.ReservationConflictException;
import com.example.service.ReservationService;
import com.example.util.DataInitializer;
import com.example.util.MigrationExecutor;
import com.example.util.PerformanceReport;
import java.time.LocalDateTime;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;

public class MainApp {
    private static final String URL = "jdbc:h2:mem:reservationdb;MODE=LEGACY;DB_CLOSE_DELAY=-1";

    public static void main(String[] args) throws Exception {
        // The migrations build the schema before Hibernate validates it.
        new MigrationExecutor(URL, "sa", "").executeAll();
        EntityManagerFactory emf = Persistence.createEntityManagerFactory("ReservationPU");
        try {
            EntityManager em = emf.createEntityManager();
            DataInitializer.Seed seed;
            try { seed = new DataInitializer(em).initData(); }
            finally { em.close(); }

            ReservationService service = new ReservationService(emf);
            LocalDateTime debut = seed.base.plusDays(3);
            Reservation nouvelle = service.creer(seed.utilisateurs.get(0), seed.salles.get(2), debut, debut.plusHours(1));
            System.out.println("Création : réservation " + nouvelle.getId());
            Reservation modifiee = service.modifier(nouvelle.getId(), nouvelle.getVersion(), debut.plusHours(1), debut.plusHours(2));
            System.out.println("Modification : version " + modifiee.getVersion());
            try {
                service.creer(seed.utilisateurs.get(1), seed.salles.get(0), seed.base.plusMinutes(30), seed.base.plusHours(1));
            } catch (ReservationConflictException e) {
                System.out.println("Conflit attendu : " + e.getMessage());
            }
            System.out.println("JPQL, Salle A : " + service.listerParSalleEtDateJPQL("Salle A", seed.base.minusDays(1)).size());
            System.out.println("Criteria, Salle A : " + service.listerParSalleEtDateCriteria("Salle A", seed.base.minusDays(1)).size());
            service.supprimer(modifiee.getId(), modifiee.getVersion());
            System.out.println("Suppression : réservation " + modifiee.getId());

            // Two EntityManagers make the L2 cache visible across sessions.
            emf.getCache().evict(Salle.class, seed.salles.get(0));
            lireSalle(emf, seed.salles.get(0));
            lireSalle(emf, seed.salles.get(0));
            new PerformanceReport(emf).printReport();
        } finally { emf.close(); }
    }

    private static void lireSalle(EntityManagerFactory emf, Long salleId) {
        EntityManager em = emf.createEntityManager();
        try { System.out.println("Salle consultée : " + em.find(Salle.class, salleId).getNom()); }
        finally { em.close(); }
    }
}
