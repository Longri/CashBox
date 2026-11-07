# CashBox
CashBox ist eine Java/Spring-Boot-Anwendung, mit der sich das Guthaben eines realen
Sparkontos auf virtuelle Spartöpfe (Sparziele) verteilen lässt. Jede Ein-, Aus- und
Umbuchung wird als unveränderliche Buchung protokolliert; nicht zugeordnetes Geld
bleibt in einem Puffer. Die Konsistenz (Kontostand = Puffer + Summe der Sparziele)
wird sowohl in der Anwendung als auch in der Datenbank geprüft.

Stack: Java, Spring Boot, JPA/Hibernate, MariaDB 11, Docker Compose (inkl. Adminer)
