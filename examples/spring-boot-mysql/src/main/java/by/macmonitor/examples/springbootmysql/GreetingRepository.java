package by.macmonitor.examples.springbootmysql;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GreetingRepository extends JpaRepository<Greeting, Long> {}
