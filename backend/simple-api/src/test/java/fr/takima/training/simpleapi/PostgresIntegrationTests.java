
package fr.takima.training.simpleapi;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PostgresIntegrationTests {

    // Use an isolated PostgreSQL database for tests.
    // The real application database is not modified.
    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:17.2-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("testuser")
                    .withPassword("testpassword");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    // Reset the test database before each test.
    @BeforeEach
    void prepareDatabase() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS departments (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(20) NOT NULL
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS students (
                    id SERIAL PRIMARY KEY,
                    department_id INT NOT NULL REFERENCES departments(id),
                    first_name VARCHAR(20) NOT NULL,
                    last_name VARCHAR(20) NOT NULL
                )
                """);

        jdbcTemplate.execute(
                "TRUNCATE TABLE students, departments RESTART IDENTITY CASCADE"
        );

        jdbcTemplate.update(
                "INSERT INTO departments (name) VALUES (?)",
                "IRC"
        );

        jdbcTemplate.update(
                "INSERT INTO departments (name) VALUES (?)",
                "ETI"
        );

        jdbcTemplate.update(
                """
                INSERT INTO students
                    (department_id, first_name, last_name)
                VALUES (?, ?, ?)
                """,
                1, "Eli", "Copter"
        );
    }

    @Test
    void shouldConnectToPostgreSQL() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );

        assertThat(result).isEqualTo(1);
    }

    @Test
    void shouldListDepartments() throws Exception {
        mockMvc.perform(get("/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("IRC"))
                .andExpect(jsonPath("$[1].name").value("ETI"));
    }

    @Test
    void shouldGetDepartmentByName() throws Exception {
        mockMvc.perform(get("/departments/IRC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("IRC"));
    }

    @Test
    void shouldReturn404ForUnknownDepartment() throws Exception {
        mockMvc.perform(get("/departments/UNKNOWN"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldListStudentsByDepartment() throws Exception {
        mockMvc.perform(get("/departments/IRC/students"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].firstname").value("Eli"))
                .andExpect(jsonPath("$[0].lastname").value("Copter"))
                .andExpect(jsonPath("$[0].department.name").value("IRC"));
    }

    @Test
    void shouldGetStudentById() throws Exception {
        mockMvc.perform(get("/students/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstname").value("Eli"))
                .andExpect(jsonPath("$.department.id").value(1));
    }

    @Test
    void shouldReturn404ForUnknownStudent() throws Exception {
        mockMvc.perform(get("/students/9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldAddStudent() throws Exception {
        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Alice",
                                  "lastname": "Martin",
                                  "department": {
                                    "id": 1,
                                    "name": "IRC"
                                  }
                                }
                                """))
                .andExpect(status().isCreated());

        // Verify that POST actually inserted a database record.
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*) FROM students
                WHERE first_name = ? AND last_name = ?
                """,
                Integer.class,
                "Alice",
                "Martin"
        );

        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldRejectInvalidStudent() throws Exception {
        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "",
                                  "lastname": "Martin",
                                  "department": {
                                    "id": 1,
                                    "name": "IRC"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectUnknownDepartmentWhenAddingStudent()
            throws Exception {
        mockMvc.perform(post("/students")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "firstname": "Alice",
                                  "lastname": "Martin",
                                  "department": {
                                    "id": 999,
                                    "name": "UNKNOWN"
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
