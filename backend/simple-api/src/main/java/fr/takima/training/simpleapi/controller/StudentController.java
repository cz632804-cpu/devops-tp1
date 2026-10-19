package fr.takima.training.simpleapi.controller;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST API for the students and departments initialized in PostgreSQL.
 * JdbcTemplate uses the DataSource configured in application.properties.
 */
@RestController
public class StudentController {

    private final JdbcTemplate jdbcTemplate;

    public StudentController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // DTOs define the JSON structure returned by the API.
    public record Department(long id, String name) {}

    public record Student(
            long id,
            String firstname,
            String lastname,
            Department department
    ) {}

    /**
     * Get all students belonging to a specific department.
     * Example: GET /departments/IRC/students
     */
    @GetMapping("/departments/{name}/students")
    public List<Student> getStudentsByDepartment(
            @PathVariable String name
    ) {
        return jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id,
                    s.first_name,
                    s.last_name,
                    d.id AS department_id,
                    d.name AS department_name
                FROM students s
                JOIN departments d
                    ON s.department_id = d.id
                WHERE d.name = ?
                ORDER BY s.id
                """,
                (rs, rowNum) -> new Student(
                        rs.getLong("student_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        new Department(
                                rs.getLong("department_id"),
                                rs.getString("department_name")
                        )
                ),
                name
        );
    }

    /**
     * List all available departments.
     */
    @GetMapping("/departments")
    public List<Department> getDepartments() {
        return jdbcTemplate.query(
                "SELECT id, name FROM departments ORDER BY id",
                (rs, rowNum) -> new Department(
                        rs.getLong("id"),
                        rs.getString("name")
                )
        );
    }

    /**
     * Get one student, including the associated department.
     */
    @GetMapping("/students/{id}")
    public Student getStudentById(@PathVariable long id) {
        List<Student> students = jdbcTemplate.query(
                """
                SELECT
                    s.id AS student_id,
                    s.first_name,
                    s.last_name,
                    d.id AS department_id,
                    d.name AS department_name
                FROM students s
                JOIN departments d
                    ON s.department_id = d.id
                WHERE s.id = ?
                """,
                (rs, rowNum) -> new Student(
                        rs.getLong("student_id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        new Department(
                                rs.getLong("department_id"),
                                rs.getString("department_name")
                        )
                ),
                id
        );

        if (students.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Student not found"
            );
        }

        return students.get(0);
    }
}