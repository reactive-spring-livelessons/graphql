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


@Component
class CrmClient {

    private final Map<Customer, Collection<Order>> db = new ConcurrentHashMap<>();
    private final AtomicInteger id = new AtomicInteger();

    private int id() {
        return this.id.incrementAndGet();
    }

    CrmClient() {
        Flux
                .fromIterable(List.of("A", "B", "C", "D"))
                .flatMap(this::addCustomer)
                .subscribe(c -> {
                    var list = this.db.get(c);
                    for (var orderId = 1; orderId <= Math.random() * 100; orderId++)
                        list.add(new Order(orderId, c.id()));
                });
    }

    Mono<Customer> addCustomer(String name) {
        var key = new Customer(id(), name);
        this.db.put(key, new CopyOnWriteArrayList<>());
        return Mono.just(key);
    }

    Flux<Customer> getCustomers() {
        return Flux.fromIterable(this.db.keySet());
    }

    Flux<Customer> getCustomersByName(String name) {
        return getCustomers()
                .filter(p -> p.name().equalsIgnoreCase(name));
    }

    Mono<Customer> getCustomerById(Integer customerId) {
        return Mono.just(this.db.keySet().stream().filter(c -> c.id().equals(customerId)).findFirst().get());
    }

    Flux<Order> getOrdersFor(Integer customerId) {
        return getCustomerById(customerId)
                .map(this.db::get)
                .flatMapMany(Flux::fromIterable);
    }
}

@Controller
class CrmGraphqlController {

    private final CrmClient crm;

    CrmGraphqlController(CrmClient crm) {
        this.crm = crm;
    }

    @QueryMapping
    Flux<Customer> customersByName(@Argument String name) {
        return this.crm.getCustomersByName(name);
    }

    @QueryMapping
    Flux<Customer> customers() {
        return this.crm.getCustomers();
    }

    @SchemaMapping(typeName = "Customer")
    Flux<Order> orders(Customer customer) {
        return this.crm.getOrdersFor(customer.id());
    }

    @MutationMapping
    Mono<Customer> addCustomer(@Argument String name) {
        return this.crm.addCustomer(name);
    }

    @SubscriptionMapping
    Flux<CustomerEvent> customerEvents(@Argument Integer id ) {
        return crm.getCustomerById(id )
                .flatMapMany(c -> Flux
                        .fromStream(Stream.generate(() -> new CustomerEvent(c, Math.random() > .5 ? CustomerEventType.CREATED : CustomerEventType.UPDATED))))
                .take(10)
                .delayElements(Duration.ofSeconds(1));
    }
}

enum CustomerEventType {
  CREATED, UPDATED
}

record CustomerEvent(Customer customer, CustomerEventType event ) {
}