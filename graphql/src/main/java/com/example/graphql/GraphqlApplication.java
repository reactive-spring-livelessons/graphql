package com.example.graphql;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.graphql.data.method.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@SpringBootApplication
public class GraphqlApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphqlApplication.class, args);
    }

}

record Order(Integer id, Integer customerId) {
}

record Customer(Integer id, String name) {
}


@Controller
class CrmGraphqlController {

    private final CrmClient crm;

    CrmGraphqlController(CrmClient crm) {
        this.crm = crm;
    }

    @QueryMapping
    Flux<Customer> customers() {
        return this.crm.getCustomers();
    }

    @QueryMapping
    Flux<Customer> customersByName(@Argument String name) {
        return this.crm.getCustomersByName(name);
    }

    @MutationMapping
    Mono<Customer> addCustomer(@Argument String name) {
        return this.crm.addCustomer(name);
    }

    @SchemaMapping(typeName = "Customer")
    Flux<Order> orders(Customer customer) {
        return this.crm.getOrdersFor(customer.id());
    }

    @SubscriptionMapping
    Flux<CustomerEvent> customerEvents(@Argument Integer id) {
        return this.crm.getCustomerEvents(id);
    }
}

@Component
class CrmClient {

    private final Map<Customer, Collection<Order>> db = new ConcurrentHashMap<>();
    private final AtomicInteger id = new AtomicInteger();

    CrmClient() {

        Flux.fromIterable(List.of("Yuxin", "Josh", "Madhura", "Olga", "Violetta", "Dr. Syer", "Stéphane", "Jürgen"))
                .flatMap(this::addCustomer)
                .subscribe(customer -> {
                    var list = this.db.get(customer);
                    for (var orderId = 1; orderId <= (Math.random() * 100); orderId++) {
                        list.add(new Order(orderId, customer.id()));
                    }
                });
    }

    Flux<Order> getOrdersFor(Integer customerId) {
        return getCustomerById(customerId)
                .map(this.db::get)
                .flatMapMany(Flux::fromIterable);
    }

    Flux<Customer> getCustomers() {
        return Flux.fromIterable(this.db.keySet());
    }

    Flux<Customer> getCustomersByName(String name) {
        return getCustomers().filter(c -> c.name().equalsIgnoreCase(name));
    }

    Mono<Customer> addCustomer(String name) {
        var key = new Customer(id(), name);
        this.db.put(key, new CopyOnWriteArrayList<>());
        return Mono.just(key);
    }

    Mono<Customer> getCustomerById(Integer customerId) {
        return getCustomers().filter(c -> c.id().equals(customerId)).singleOrEmpty();
    }

    Flux<CustomerEvent> getCustomerEvents(Integer customerId) {
        return getCustomerById(customerId)
                .flatMapMany(customer -> Flux.fromStream(
                        Stream.generate(() -> {
                            var event = Math.random() > .5 ? CustomerEventType.UPDATED : CustomerEventType.CREATED;
                            return new CustomerEvent(customer, event);
                        })
                ))
                .take(10)
                .delayElements(Duration.ofSeconds(1));
    }

    private int id() {
        return this.id.incrementAndGet();
    }
}

record CustomerEvent(Customer customer, CustomerEventType event) {
}

enum CustomerEventType {
    CREATED, UPDATED
}