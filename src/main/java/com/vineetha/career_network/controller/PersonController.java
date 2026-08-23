package com.vineetha.career_network.controller;

import com.vineetha.career_network.model.ConnectRequest;
import com.vineetha.career_network.model.ConnectionSuggestion;
import com.vineetha.career_network.model.CreatePersonRequest;
import com.vineetha.career_network.model.Person;
import com.vineetha.career_network.service.PersonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PersonController {

    private final PersonService personService;

    public PersonController(PersonService personService) {
        this.personService = personService;
    }

    @GetMapping("/people")
    public List<Person> search(@RequestParam(name = "q", defaultValue = "") String q) {
        return personService.search(q);
    }

    @GetMapping("/people/{id}")
    public Person getProfile(@PathVariable String id) {
        return personService.getProfile(id);
    }

    @PostMapping("/people")
    public ResponseEntity<Person> createPerson(@RequestBody CreatePersonRequest request) {
        Person created = personService.createPerson(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/people/{id}")
    public ResponseEntity<Object> deletePerson(@PathVariable String id) {
        personService.deletePerson(id);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    @GetMapping("/people/{id}/connections")
    public List<Person> getConnections(@PathVariable String id) {
        return personService.getConnections(id);
    }

    @GetMapping("/people/{id}/suggestions")
    public List<ConnectionSuggestion> getSuggestions(@PathVariable String id) {
        return personService.getSuggestions(id);
    }

    @GetMapping("/skills")
    public List<String> listSkills() {
        return personService.listSkills();
    }

    @GetMapping("/skills/{skill}/people")
    public List<Person> peopleWithSkill(@PathVariable String skill) {
        return personService.findBySkill(skill);
    }

    @PostMapping("/connections")
    public ResponseEntity<Object> connect(@RequestBody ConnectRequest request) {
        personService.connect(request.getPersonId(), request.getOtherPersonId());
        return ResponseEntity.ok(Map.of("connected", true));
    }

    @GetMapping("/path")
    public ResponseEntity<Object> shortestPath(@RequestParam String from, @RequestParam String to) {
        return personService.shortestPath(from, to)
                .<ResponseEntity<Object>>map(people -> ResponseEntity.ok(Map.of(
                        "connected", true,
                        "hops", people.size() - 1,
                        "people", people)))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "connected", false,
                        "hops", -1,
                        "people", List.of())));
    }
}

