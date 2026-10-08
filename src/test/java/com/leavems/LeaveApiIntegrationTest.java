package com.leavems;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leavems.entity.Role;
import com.leavems.entity.User;
import com.leavems.repository.LeaveRequestRepository;
import com.leavems.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the HTTP surface the Next.js route handlers call, using a real
 * server on a random port and an in-memory database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestBeans.class)
class LeaveApiIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    UserRepository userRepository;

    @Autowired
    LeaveRequestRepository leaveRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    private final RestTemplate rest = buildRestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * The plain RestTemplate is unsuitable here for two reasons: its default
     * request factory rejects PATCH as an invalid HTTP method, and it throws on
     * 4xx responses instead of returning them for the assertions to inspect.
     */
    private static RestTemplate buildRestTemplate() {
        RestTemplate template = new RestTemplate(new JdkClientHttpRequestFactory());
        template.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                // Every assertion checks a 4xx status, so they must come back
                // as responses rather than exceptions.
                return false;
            }
        });
        return template;
    }

    @BeforeEach
    void seed() {
        leaveRepository.deleteAll();
        userRepository.deleteAll();

        userRepository.save(new User("Employee One", "employee@test.com",
                passwordEncoder.encode("secret1"), Role.EMPLOYEE));
        userRepository.save(new User("Other Employee", "other@test.com",
                passwordEncoder.encode("secret1"), Role.EMPLOYEE));
        userRepository.save(new User("The Manager", "manager@test.com",
                passwordEncoder.encode("secret1"), Role.MANAGER));
        userRepository.save(new User("The Admin", "admin@test.com",
                passwordEncoder.encode("secret1"), Role.ADMIN));
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * RestTemplate's getForEntity has no (String, HttpEntity) overload, so
     * authenticated GETs go through exchange.
     */
    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private String login(String email, String password, Role role) throws Exception {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", email, "password", password, "role", role.name()),
                        jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return mapper.readTree(response.getBody()).get("token").asText();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private HttpHeaders authHeaders(String token) {
        HttpHeaders headers = jsonHeaders();
        headers.setBearerAuth(token);
        return headers;
    }

    @Test
    void loginReturnsATokenAndNeverThePasswordHash() throws Exception {
        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        assertThat(token).isNotBlank();

        JsonNode user = mapper.readTree(
                get("/api/auth/me", authHeaders(token)).getBody());
        assertThat(user.get("email").asText()).isEqualTo("employee@test.com");
        assertThat(user.has("password")).isFalse();
    }

    @Test
    void loginRejectsAWrongPassword() {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", "employee@test.com", "password", "wrong", "role", "EMPLOYEE"),
                        jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void loginRejectsTheWrongRole() {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/login"),
                new HttpEntity<>(Map.of("email", "employee@test.com", "password", "secret1", "role", "ADMIN"),
                        jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("employee");
    }

    @Test
    void unauthenticatedRequestsAreRejected() {
        assertThat(rest.getForEntity(url("/api/leaves"), String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void anEmployeeOnlySeesTheirOwnLeaves() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@test.com").orElseThrow();
        User other = userRepository.findByEmailIgnoreCase("other@test.com").orElseThrow();

        createLeave(employee, LocalDate.now().plusDays(10), "mine");
        createLeave(other, LocalDate.now().plusDays(11), "theirs");

        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        String body = get("/api/leaves", authHeaders(token)).getBody();

        assertThat(body).contains("mine").doesNotContain("theirs");
    }

    @Test
    void aManagerSeesEveryLeave() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@test.com").orElseThrow();
        User other = userRepository.findByEmailIgnoreCase("other@test.com").orElseThrow();
        createLeave(employee, LocalDate.now().plusDays(10), "mine");
        createLeave(other, LocalDate.now().plusDays(11), "theirs");

        String token = login("manager@test.com", "secret1", Role.MANAGER);
        String body = get("/api/leaves", authHeaders(token)).getBody();

        assertThat(body).contains("mine").contains("theirs");
    }

    @Test
    void aManagerCanApproveAndTheStatusPersists() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@test.com").orElseThrow();
        Long leaveId = createLeave(employee, LocalDate.now().plusDays(10), "please approve");

        String managerToken = login("manager@test.com", "secret1", Role.MANAGER);
        ResponseEntity<String> patch = rest.exchange(url("/api/leaves/" + leaveId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "Approved"), authHeaders(managerToken)), String.class);

        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(patch.getBody()).contains("Approved");
        assertThat(leaveRepository.findById(leaveId).orElseThrow().getStatus().name()).isEqualTo("Approved");
    }

    @Test
    void anEmployeeCannotApproveALeave() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@test.com").orElseThrow();
        Long leaveId = createLeave(employee, LocalDate.now().plusDays(10), "self approval attempt");

        String employeeToken = login("employee@test.com", "secret1", Role.EMPLOYEE);
        ResponseEntity<String> patch = rest.exchange(url("/api/leaves/" + leaveId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "Approved"), authHeaders(employeeToken)), String.class);

        assertThat(patch.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void aLeaveCannotBeDecidedTwice() throws Exception {
        User employee = userRepository.findByEmailIgnoreCase("employee@test.com").orElseThrow();
        Long leaveId = createLeave(employee, LocalDate.now().plusDays(10), "double decision");

        String managerToken = login("manager@test.com", "secret1", Role.MANAGER);
        rest.exchange(url("/api/leaves/" + leaveId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "Approved"), authHeaders(managerToken)), String.class);

        ResponseEntity<String> second = rest.exchange(url("/api/leaves/" + leaveId), HttpMethod.PATCH,
                new HttpEntity<>(Map.of("status", "Rejected"), authHeaders(managerToken)), String.class);

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void theBalanceEndpointReportsRemainingAllowance() throws Exception {
        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        JsonNode balance = mapper.readTree(
                get("/api/leaves/balance", authHeaders(token)).getBody());

        assertThat(balance.get("limits").get("Vacation").asInt()).isEqualTo(20);
        assertThat(balance.get("used").get("Vacation").asInt()).isZero();
        assertThat(balance.get("limits").get("Sick Leave").asInt()).isEqualTo(10);
    }

    @Test
    void aLeaveStartingInThePastIsRejected() throws Exception {
        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        ResponseEntity<String> response = rest.exchange(url("/api/leaves"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "Vacation",
                        "startDate", LocalDate.now().minusDays(3).toString(),
                        "endDate", LocalDate.now().minusDays(1).toString(),
                        "reason", "already happened"), authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void aSundayStartDateIsRejected() throws Exception {
        // Walk forward to the next Sunday so the test does not depend on the day it runs.
        LocalDate sunday = LocalDate.now().plusDays(1);
        while (sunday.getDayOfWeek() != java.time.DayOfWeek.SUNDAY) {
            sunday = sunday.plusDays(1);
        }

        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        ResponseEntity<String> response = rest.exchange(url("/api/leaves"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "Vacation",
                        "startDate", sunday.toString(),
                        "endDate", sunday.plusDays(1).toString(),
                        "reason", "sunday start"), authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("Sunday");
    }

    @Test
    void aRequestOverTheAnnualLimitIsRejected() throws Exception {
        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);
        // Personal leave has a 5-day allowance; 10 working days must fail.
        ResponseEntity<String> response = rest.exchange(url("/api/leaves"), HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "type", "Personal",
                        "startDate", LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(2).toString(),
                        "endDate", LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusWeeks(3).plusDays(4).toString(),
                        "reason", "way too long"), authHeaders(token)), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void everyLeaveTypeCanBePersistedAndRoundTrips() throws Exception {
        String token = login("employee@test.com", "secret1", Role.EMPLOYEE);

        // Regression: the ck_leaves_type check constraint originally listed
        // "Sick Leave" while the column stores the enum name "SickLeave", so any
        // sick-leave request failed with a 500 instead of being created.
        String[][] cases = {
                {"Vacation", "Vacation"},
                {"Sick Leave", "SickLeave"},
                {"Personal", "Personal"},
        };

        int offset = 30;
        for (String[] testCase : cases) {
            String sentType = testCase[0];
            String expectedStoredType = testCase[1];

            LocalDate start = LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusDays(offset);
            ResponseEntity<String> response = rest.exchange(url("/api/leaves"), HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "type", sentType,
                            "startDate", start.toString(),
                            "endDate", start.plusDays(1).toString(),
                            "reason", "type check"), authHeaders(token)), String.class);

            assertThat(response.getStatusCode())
                    .as("creating a %s request", sentType)
                    .isEqualTo(HttpStatus.CREATED);

            // The response must use the wire format the frontend renders.
            assertThat(mapper.readTree(response.getBody()).get("type").asText()).isEqualTo(sentType);

            // And the stored value must be the enum constant.
            JsonNode stored = mapper.readTree(response.getBody());
            long id = stored.get("id").asLong();
            assertThat(leaveRepository.findById(id).orElseThrow().getType().name())
                    .isEqualTo(expectedStoredType);

            offset += 7;
        }
    }

    @Test
    void signupHashesThePasswordAndCannotCreateAnAdmin() throws Exception {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/signup"),
                new HttpEntity<>(Map.of("name", "New Person", "email", "new@test.com",
                                "password", "hunter2", "role", "ADMIN"), jsonHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        User created = userRepository.findByEmailIgnoreCase("new@test.com").orElseThrow();
        assertThat(created.getRole()).isEqualTo(Role.EMPLOYEE);
        assertThat(created.getPassword()).isNotEqualTo("hunter2");
        assertThat(passwordEncoder.matches("hunter2", created.getPassword())).isTrue();
    }

    @Test
    void signupRejectsADuplicateEmail() {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/signup"),
                new HttpEntity<>(Map.of("name", "Duplicate", "email", "employee@test.com",
                        "password", "secret1", "role", "EMPLOYEE"), jsonHeaders()), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    /** Creates a leave through the API so status/ownership rules are exercised. */
    private Long createLeave(User employee, LocalDate start, String reason) {
        leaveRepository.save(new com.leavems.entity.LeaveRequest(employee,
                com.leavems.entity.LeaveType.Vacation, start, start.plusDays(1), reason));
        return leaveRepository.findAll().stream()
                .filter(l -> reason.equals(l.getReason()))
                .reduce((first, second) -> second)
                .orElseThrow()
                .getId();
    }
}