package com.leavems;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leavems.entity.LeavePolicy;
import com.leavems.entity.Role;
import com.leavems.entity.User;
import com.leavems.repository.LeavePolicyRepository;
import com.leavems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the policy endpoints: every role can read the active policies, only an
 * admin can write, and a saved policy is readable afterwards so a page refresh
 * shows the stored value rather than the seeded default.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestBeans.class)
class LeavePolicyApiIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    LeavePolicyRepository policyRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Same two adjustments as {@link LeaveApiIntegrationTest}: the default
     * factory rejects PATCH and DELETE, and the default error handler throws on
     * the 4xx responses these tests assert on.
     */
    private final RestTemplate rest = buildRestTemplate();

    private static RestTemplate buildRestTemplate() {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        return template;
    }

    @BeforeEach
    void seed() {
        policyRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("Policy Admin", "policy-admin@test.com",
                passwordEncoder.encode("secret1"), Role.ADMIN));
        userRepository.save(new User("Policy Manager", "policy-manager@test.com",
                passwordEncoder.encode("secret1"), Role.MANAGER));
        userRepository.save(new User("Policy Employee", "policy-employee@test.com",
                passwordEncoder.encode("secret1"), Role.EMPLOYEE));
    }

    @Test
    @DisplayName("every role can read the active policies")
    void everyRoleCanReadActivePolicies() throws Exception {
        policyRepository.save(new LeavePolicy("Read Me", "Everyone sees this.", 1));

        for (String token : new String[]{adminToken(), managerToken(), employeeToken()}) {
            ResponseEntity<String> response = exchange(HttpMethod.GET, "/api/policies", token, null);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            JsonNode body = mapper.readTree(response.getBody());
            assertThat(body.isArray()).isTrue();
            assertThat(body).isNotEmpty();
        }
    }

    @Test
    @DisplayName("an admin can create a policy and another role can read it back")
    void anAdminCanCreateAPolicy() throws Exception {
        String title = "Carry-over Policy";

        ResponseEntity<String> created = exchange(HttpMethod.POST, "/api/policies", adminToken(), Map.of(
                "title", title,
                "description", "Unused days may be carried into next year."
        ));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        JsonNode saved = mapper.readTree(created.getBody());
        assertThat(saved.get("title").asText()).isEqualTo(title);
        assertThat(saved.get("id").asLong()).isPositive();
        assertThat(saved.get("active").asBoolean()).isTrue();

        // Read it back as an employee: this is what a dashboard refresh relies on.
        JsonNode listed = mapper.readTree(exchange(HttpMethod.GET, "/api/policies", employeeToken(), null).getBody());
        assertThat(titlesIn(listed)).contains(title);
    }

    @Test
    @DisplayName("an admin edit is persisted and visible to every role")
    void anAdminEditIsPersisted() throws Exception {
        LeavePolicy policy = policyRepository.save(new LeavePolicy("Draft Policy", "Original text.", 2));

        ResponseEntity<String> updated = exchange(HttpMethod.PATCH, "/api/policies/" + policy.getId(), adminToken(), Map.of(
                "title", policy.getTitle(),
                "description", "Rewritten by the admin."
        ));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Read through the API as a manager rather than off the entity, so this
        // asserts what another dashboard would actually render.
        JsonNode listed = mapper.readTree(exchange(HttpMethod.GET, "/api/policies", managerToken(), null).getBody());
        JsonNode seen = nodeWithId(listed, policy.getId());
        assertThat(seen).isNotNull();
        assertThat(seen.get("description").asText()).isEqualTo("Rewritten by the admin.");
    }

    @Test
    @DisplayName("an edit that omits active keeps the stored flag")
    void aPartialEditKeepsTheActiveFlag() throws Exception {
        LeavePolicy policy = policyRepository.save(new LeavePolicy("Partial Policy", "Before.", 3));
        policy.setActive(false);
        policyRepository.save(policy);

        ResponseEntity<String> updated = exchange(HttpMethod.PATCH, "/api/policies/" + policy.getId(), adminToken(), Map.of(
                "title", policy.getTitle(),
                "description", "After."
        ));
        assertThat(updated.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(mapper.readTree(updated.getBody()).get("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a manager cannot create, edit or delete a policy")
    void aManagerCannotWrite() throws Exception {
        LeavePolicy policy = policyRepository.save(new LeavePolicy("Locked Policy", "Read only.", 4));
        String token = managerToken();
        // The refusal must come from the server, not just from hidden controls.

        assertThat(exchange(HttpMethod.POST, "/api/policies", token, Map.of(
                "title", "Manager Attempt", "description", "Should be refused."
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.PATCH, "/api/policies/" + policy.getId(), token, Map.of(
                "title", policy.getTitle(), "description", "Manager edit."
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(exchange(HttpMethod.DELETE, "/api/policies/" + policy.getId(), token, null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // The refused edit must not have been applied.
        assertThat(policyRepository.findById(policy.getId()).orElseThrow().getDescription())
                .isEqualTo("Read only.");
    }

    @Test
    @DisplayName("an employee cannot write a policy")
    void anEmployeeCannotWrite() throws Exception {
        assertThat(exchange(HttpMethod.POST, "/api/policies", employeeToken(), Map.of(
                "title", "Employee Attempt", "description", "Should be refused."
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("an anonymous visitor cannot read the policies")
    void anonymousCannotRead() {
        assertThat(exchange(HttpMethod.GET, "/api/policies", null, null).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("a retired policy is hidden from readers but an admin can still list it")
    void aRetiredPolicyIsHiddenFromReaders() throws Exception {
        LeavePolicy policy = policyRepository.save(new LeavePolicy("Retired Policy", "No longer applies.", 5));
        policy.setActive(false);
        policyRepository.save(policy);

        JsonNode reader = mapper.readTree(exchange(HttpMethod.GET, "/api/policies", employeeToken(), null).getBody());
        assertThat(nodeWithId(reader, policy.getId())).isNull();

        ResponseEntity<String> all = exchange(HttpMethod.GET, "/api/policies/all", adminToken(), null);
        assertThat(all.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(nodeWithId(mapper.readTree(all.getBody()), policy.getId())).isNotNull();
    }

    @Test
    @DisplayName("only an admin may list retired policies")
    void onlyAnAdminMayListRetiredPolicies() throws Exception {
        assertThat(exchange(HttpMethod.GET, "/api/policies/all", managerToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("a duplicate title is rejected with a readable message")
    void aDuplicateTitleIsRejected() throws Exception {
        policyRepository.save(new LeavePolicy("Duplicate Policy", "First.", 6));

        ResponseEntity<String> response = exchange(HttpMethod.POST, "/api/policies", adminToken(), Map.of(
                "title", "Duplicate Policy", "description", "Second."
        ));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mapper.readTree(response.getBody()).get("message").asText()).contains("Duplicate Policy");
    }

    @Test
    @DisplayName("a blank title is rejected")
    void aBlankTitleIsRejected() throws Exception {
        assertThat(exchange(HttpMethod.POST, "/api/policies", adminToken(), Map.of(
                "title", "   ", "description", "Has a description."
        )).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("deleting a policy removes it from every reader's list")
    void deletingAPolicyRemovesIt() throws Exception {
        LeavePolicy policy = policyRepository.save(new LeavePolicy("Temporary Policy", "Delete me.", 7));

        assertThat(exchange(HttpMethod.DELETE, "/api/policies/" + policy.getId(), adminToken(), null).getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(policyRepository.findById(policy.getId())).isEmpty();

        JsonNode listed = mapper.readTree(exchange(HttpMethod.GET, "/api/policies", adminToken(), null).getBody());
        assertThat(nodeWithId(listed, policy.getId())).isNull();
    }

    @Test
    @DisplayName("updating a policy that does not exist returns 404")
    void updatingAMissingPolicyReturnsNotFound() throws Exception {
        assertThat(exchange(HttpMethod.PATCH, "/api/policies/999999", adminToken(), Map.of(
                "title", "Ghost", "description", "No such row."
        )).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("policies come back in display order")
    void policiesAreOrdered() throws Exception {
        policyRepository.save(new LeavePolicy("Third", "c", 30));
        policyRepository.save(new LeavePolicy("First", "a", 10));
        policyRepository.save(new LeavePolicy("Second", "b", 20));

        JsonNode listed = mapper.readTree(exchange(HttpMethod.GET, "/api/policies", adminToken(), null).getBody());
        assertThat(titlesIn(listed)).containsExactly("First", "Second", "Third");
    }

    // ---------- helpers ----------

    private String adminToken() throws Exception {
        return login("policy-admin@test.com", Role.ADMIN);
    }

    private String managerToken() throws Exception {
        return login("policy-manager@test.com", Role.MANAGER);
    }

    private String employeeToken() throws Exception {
        return login("policy-employee@test.com", Role.EMPLOYEE);
    }

    private String login(String email, Role role) throws Exception {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", email, "password", "secret1", "role", role.name()),
                        jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).get("token").asText();
    }

    private ResponseEntity<String> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = jsonHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private static JsonNode nodeWithId(JsonNode array, long id) {
        for (JsonNode node : array) {
            if (node.get("id").asLong() == id) {
                return node;
            }
        }
        return null;
    }

    private static java.util.List<String> titlesIn(JsonNode array) {
        java.util.List<String> titles = new java.util.ArrayList<>();
        for (JsonNode node : array) {
            titles.add(node.get("title").asText());
        }
        return titles;
    }
}
