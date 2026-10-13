# Réservation de salles — TP JPA/Hibernate

Application Java 8+ autonome avec JPA 2.2, Hibernate 5.4, Ehcache 3/JCache, H2 et JUnit 5. Aucun serveur MySQL n'est nécessaire pour lancer le TP. La base H2 est en mémoire et les données sont recréées à chaque exécution.

## Exécution

Dans le dossier du projet :

```powershell
mvn clean test
mvn exec:java
```

Sur cette machine, Maven est installé à `C:\Program Files\maven-mvnd-1.0.3-windows-amd64\mvn\bin\mvn.cmd` mais n'est pas dans `PATH`. Avec le proxy local, Java peut nécessiter `$env:MAVEN_OPTS='-Djavax.net.ssl.trustStoreType=Windows-ROOT'` avant Maven. Dans IntelliJ, lancer `com.example.MainApp` après import du `pom.xml`, ou exécuter la classe de test `com.example.ReservationTest`.

`MainApp` applique les deux migrations SQL avant de créer `ReservationPU` ; Hibernate valide ensuite le schéma. La migration v1 crée les tables et les contraintes ; la v2 ajoute `salles.description` et un index sur `utilisateurs.email`. Les scripts sont réexécutables pour le laboratoire H2. Un déploiement réel devrait employer un outil de migration versionné et une base persistante.

Le service vérifie les dates et les chevauchements (intervalles à fin exclue). Lors d'une création ou d'une modification, il verrouille la ligne de la salle pendant la vérification et l'écriture pour empêcher deux réservations simultanées du même créneau. `@Version` protège les modifications concurrentes d'une réservation existante. Les méthodes de recherche montrent JPQL et Criteria API. Le rapport affiche les statistiques Hibernate ; le test de cache lit une salle dans deux `EntityManager` distincts pour écarter le cache de premier niveau.

Le cache Ehcache 3 utilise `hibernate-jcache`, compatible avec `org.hibernate.cache.jcache.JCacheRegionFactory`. `hibernate-ehcache` cible Ehcache 2. H2 2.3 emploie `MODE=LEGACY` pour la syntaxe d'identité d'Hibernate 5.4.
