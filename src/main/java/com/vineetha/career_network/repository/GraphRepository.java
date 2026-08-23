package com.vineetha.career_network.repository;

import com.vineetha.career_network.model.ConnectionSuggestion;
import com.vineetha.career_network.model.Person;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Value;
import org.neo4j.driver.Values;
import org.neo4j.driver.types.Node;
import org.neo4j.driver.types.Path;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;


@Repository
public class GraphRepository {

    private final Driver driver;

    public GraphRepository(Driver driver) {
        this.driver = driver;
    }

    private Session session() {
        return driver.session();
    }

    /** Simple name/headline search - the entry point into the graph. */
    public List<Person> searchPeople(String query, int limit) {
        String cypher =
                "MATCH (p:Person) " +
                "WHERE $query = '' OR toLower(p.name) CONTAINS toLower($query) " +
                "   OR toLower(p.headline) CONTAINS toLower($query) " +
                "OPTIONAL MATCH (p)-[:WORKS_AT]->(c:Company) " +
                "OPTIONAL MATCH (p)-[:HAS_SKILL]->(s:Skill) " +
                "WITH p, c, collect(DISTINCT s.name) AS skills " +
                "RETURN p, c.name AS company, skills " +
                "ORDER BY p.name " +
                "LIMIT $limit";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher, Values.parameters(
                            "query", query == null ? "" : query, "limit", limit))
                    .list(this::toPerson));
        }
    }

    /** Full profile for one person: attributes + current company + skills. */
    public Optional<Person> getPerson(String id) {
        String cypher =
                "MATCH (p:Person {id: $id}) " +
                "OPTIONAL MATCH (p)-[:WORKS_AT]->(c:Company) " +
                "OPTIONAL MATCH (p)-[:HAS_SKILL]->(s:Skill) " +
                "WITH p, c, collect(DISTINCT s.name) AS skills " +
                "RETURN p, c.name AS company, skills";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher, Values.parameters("id", id))
                    .list(this::toPerson)
                    .stream().findFirst());
        }
    }

    /** Direct (1-hop) connections of a person. */
    public List<Person> getConnections(String id) {
        String cypher =
                "MATCH (p:Person {id: $id})-[:KNOWS]-(friend:Person) " +
                "OPTIONAL MATCH (friend)-[:WORKS_AT]->(c:Company) " +
                "OPTIONAL MATCH (friend)-[:HAS_SKILL]->(s:Skill) " +
                "WITH friend, c, collect(DISTINCT s.name) AS skills " +
                "RETURN DISTINCT friend AS p, c.name AS company, skills " +
                "ORDER BY friend.name";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher, Values.parameters("id", id))
                    .list(this::toPerson));
        }
    }

    /** People with a given skill - a plain 1-hop lookup used by the skill search box. */
    public List<Person> findBySkill(String skillName, int limit) {
        String cypher =
                "MATCH (p:Person)-[:HAS_SKILL]->(:Skill {name: $skill}) " +
                "OPTIONAL MATCH (p)-[:WORKS_AT]->(c:Company) " +
                "OPTIONAL MATCH (p)-[:HAS_SKILL]->(s:Skill) " +
                "WITH p, c, collect(DISTINCT s.name) AS skills " +
                "RETURN p, c.name AS company, skills " +
                "ORDER BY p.name LIMIT $limit";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher,
                            Values.parameters("skill", skillName, "limit", limit))
                    .list(this::toPerson));
        }
    }

    public List<String> listSkills() {
        String cypher = "MATCH (s:Skill) RETURN s.name AS name ORDER BY name";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher)
                    .list(record -> record.get("name").asString()));
        }
    }

    public List<ConnectionSuggestion> suggestConnections(String id, int limit) {
        String cypher =
                "MATCH (me:Person {id: $id}) " +
                "OPTIONAL MATCH (me)-[:KNOWS]-(direct:Person) " +
                "WITH me, collect(DISTINCT direct.id) AS directIds " +
                "MATCH (me)-[:KNOWS]-(mutual:Person)-[:KNOWS]-(candidate:Person) " +
                "WHERE candidate.id <> $id AND NOT candidate.id IN directIds " +
                "WITH candidate, count(DISTINCT mutual) AS mutualConnections " +
                "OPTIONAL MATCH (me2:Person {id: $id})-[:HAS_SKILL]->(s:Skill)<-[:HAS_SKILL]-(candidate) " +
                "OPTIONAL MATCH (candidate)-[:WORKS_AT]->(c:Company) " +
                "WITH candidate, c, mutualConnections, count(DISTINCT s) AS sharedSkills " +
                "RETURN candidate, c.name AS company, mutualConnections, sharedSkills " +
                "ORDER BY mutualConnections DESC, sharedSkills DESC, candidate.name " +
                "LIMIT $limit";
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run(cypher,
                            Values.parameters("id", id, "limit", limit))
                    .list(this::toSuggestion));
        }
    }

    public Optional<List<Person>> shortestPath(String fromId, String toId, int maxHops) {
        String cypher =
                "MATCH path = shortestPath((a:Person {id: $fromId})-[:KNOWS*1.." + maxHops + "]-(b:Person {id: $toId})) " +
                "RETURN path";
        try (Session session = session()) {
            return session.executeRead(tx -> {
                var result = tx.run(cypher, Values.parameters("fromId", fromId, "toId", toId));
                if (!result.hasNext()) {
                    return Optional.<List<Person>>empty();
                }
                Record record = result.next();
                Path path = record.get("path").asPath();
                List<Person> people = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                for (Node node : path.nodes()) {
                    Person p = basicPerson(node);
                    if (seen.add(p.getId())) {
                        people.add(p);
                    }
                }
                return Optional.of(people);
            });
        }
    }


    /** Connect two people (mutual/bidirectional relationship). */
    public void connect(String personId, String otherPersonId) {
        String cypher =
                "MATCH (a:Person {id: $a}), (b:Person {id: $b}) " +
                "MERGE (a)-[:KNOWS]-(b)";
        try (Session session = session()) {
            session.executeWrite(tx -> tx.run(cypher,
                    Values.parameters("a", personId, "b", otherPersonId)).consume());
        }
    }

    public Person createPerson(String id, String name, String headline, String location,
                                String email, String company, List<String> skills) {
        String cypher =
                "CREATE (p:Person {id: $id, name: $name, headline: $headline, " +
                "                   location: $location, email: $email}) " +
                "WITH p " +
                "UNWIND (CASE WHEN size($skills) = 0 THEN [null] ELSE $skills END) AS skillName " +
                "FOREACH (ignore IN CASE WHEN skillName IS NOT NULL THEN [1] ELSE [] END | " +
                "  MERGE (s:Skill {name: skillName}) " +
                "  MERGE (p)-[:HAS_SKILL]->(s) " +
                ") " +
                "WITH DISTINCT p " +
                "FOREACH (ignore IN CASE WHEN $company IS NOT NULL AND $company <> '' THEN [1] ELSE [] END | " +
                "  MERGE (c:Company {name: $company}) " +
                "  MERGE (p)-[:WORKS_AT]->(c) " +
                ") " +
                "RETURN p";
        try (Session session = session()) {
            session.executeWrite(tx -> tx.run(cypher, Values.parameters(
                    "id", id, "name", name, "headline", headline == null ? "" : headline,
                    "location", location == null ? "" : location, "email", email == null ? "" : email,
                    "skills", skills == null ? List.of() : skills,
                    "company", company)).consume());
        }
        return new Person(id, name, headline, location, email, (company == null || company.isBlank()) ? null : company,
                skills == null ? List.of() : skills);
    }

    /** Delete a person and every relationship attached to them. */
    public boolean deletePerson(String id) {
        try (Session session = session()) {
            boolean existed = session.executeRead(tx -> tx.run(
                            "MATCH (p:Person {id: $id}) RETURN count(p) AS c", Values.parameters("id", id))
                    .single().get("c").asLong() > 0);
            session.executeWrite(tx -> tx.run(
                    "MATCH (p:Person {id: $id}) DETACH DELETE p", Values.parameters("id", id)).consume());
            return existed;
        }
    }
    public long countPeople() {
        try (Session session = session()) {
            return session.executeRead(tx -> tx.run("MATCH (p:Person) RETURN count(p) AS c")
                    .single().get("c").asLong());
        }
    }

    /** Execute a raw multi-statement Cypher script (used only for seeding). */
    public void runScript(List<String> statements) {
        try (Session session = session()) {
            session.executeWrite(tx -> {
                for (String statement : statements) {
                    if (!statement.isBlank()) {
                        tx.run(statement);
                    }
                }
                return null;
            });
        }
    }

    private Person toPerson(Record record) {
        Node node = record.get("p").asNode();
        Value companyValue = record.get("company");
        Value skillsValue = record.get("skills");
        List<String> skills = skillsValue == null || skillsValue.isNull()
                ? List.of()
                : skillsValue.asList(Value::asString);
        return new Person(
                node.get("id").asString(),
                node.get("name").asString(),
                node.containsKey("headline") ? node.get("headline").asString("") : "",
                node.containsKey("location") ? node.get("location").asString("") : "",
                node.containsKey("email") ? node.get("email").asString("") : "",
                companyValue == null || companyValue.isNull() ? null : companyValue.asString(),
                skills
        );
    }

    private Person basicPerson(Node node) {
        return new Person(
                node.get("id").asString(),
                node.get("name").asString(),
                node.containsKey("headline") ? node.get("headline").asString("") : "",
                node.containsKey("location") ? node.get("location").asString("") : "",
                node.containsKey("email") ? node.get("email").asString("") : "",
                null,
                List.of()
        );
    }

    private ConnectionSuggestion toSuggestion(Record record) {
        Node node = record.get("candidate").asNode();
        Value companyValue = record.get("company");
        return new ConnectionSuggestion(
                node.get("id").asString(),
                node.get("name").asString(),
                node.containsKey("headline") ? node.get("headline").asString("") : "",
                companyValue == null || companyValue.isNull() ? null : companyValue.asString(),
                record.get("mutualConnections").asLong(),
                record.get("sharedSkills").asLong()
        );
    }
}

