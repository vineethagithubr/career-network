package com.vineetha.career_network.service;

import com.vineetha.career_network.model.ConnectionSuggestion;
import com.vineetha.career_network.model.CreatePersonRequest;
import com.vineetha.career_network.model.Person;
import com.vineetha.career_network.repository.GraphRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PersonService {

    private final GraphRepository graphRepository;

    public PersonService(GraphRepository graphRepository) {
        this.graphRepository = graphRepository;
    }

    public List<Person> search(String query) {
        return graphRepository.searchPeople(query, 25);
    }

    public Person getProfile(String id) {
        return graphRepository.getPerson(id)
                .orElseThrow(() -> new NoSuchElementException("Person not found: " + id));
    }

    public List<Person> getConnections(String id) {

        return graphRepository.getConnections(id);
    }

    public List<ConnectionSuggestion> getSuggestions(String id) {

        return graphRepository.suggestConnections(id, 10);
    }

    public List<Person> findBySkill(String skill) {

        return graphRepository.findBySkill(skill, 50);
    }

    public List<String> listSkills() {
        return graphRepository.listSkills();
    }

    public void connect(String personId, String otherPersonId) {
        if (personId == null || otherPersonId == null || personId.equals(otherPersonId)) {
            throw new IllegalArgumentException("Two different, existing people are required to connect.");
        }
        graphRepository.connect(personId, otherPersonId);
    }

    public Optional<List<Person>> shortestPath(String fromId, String toId) {
        if (fromId == null || toId == null || fromId.equals(toId)) {
            throw new IllegalArgumentException("Please choose two different people.");
        }
        return graphRepository.shortestPath(fromId, toId, 6);
    }

    public Person createPerson(CreatePersonRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new IllegalArgumentException("A name is required to add a person.");
        }
        List<String> skills = request.getSkills() == null
                ? List.of()
                : request.getSkills().stream()
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .distinct()
                        .collect(Collectors.toList());
        String id = "p-" + UUID.randomUUID().toString().substring(0, 8);
        return graphRepository.createPerson(
                id,
                request.getName().trim(),
                request.getHeadline() == null ? "" : request.getHeadline().trim(),
                request.getLocation() == null ? "" : request.getLocation().trim(),
                request.getEmail() == null ? "" : request.getEmail().trim(),
                request.getCompany() == null ? null : request.getCompany().trim(),
                skills);
    }

    public void deletePerson(String id) {
        boolean existed = graphRepository.deletePerson(id);
        if (!existed) {
            throw new NoSuchElementException("Person not found: " + id);
        }
    }
}

