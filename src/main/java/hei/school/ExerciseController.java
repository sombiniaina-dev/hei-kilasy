package hei.school;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ExerciseController {

    private static final RowMapper<Exercise> EXERCISE_MAPPER = (rs, i) -> {
        Exercise e = new Exercise();
        e.setId(rs.getLong("id"));
        e.setTeacherId(rs.getLong("teacher_id"));
        e.setTitle(rs.getString("title"));
        e.setDescription(rs.getString("description"));
        e.setMaxPoints(rs.getDouble("max_points"));
        e.setDeadline(rs.getDate("deadline").toLocalDate());
        int duration = rs.getInt("duration_minutes");
        e.setDurationMinutes(rs.wasNull() ? null : duration);
        return e;
    };

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/exercises")
    public List<Exercise> list() {
        return jdbcTemplate.query("SELECT * FROM exercise ORDER BY deadline", (rs, i) -> {
            Exercise e = new Exercise();
            e.setId(rs.getLong("id"));
            e.setTeacherId(rs.getLong("teacher_id"));
            e.setTitle(rs.getString("title"));
            e.setDescription(rs.getString("description"));
            e.setMaxPoints(rs.getDouble("max_points"));
            e.setDeadline(rs.getDate("deadline").toLocalDate());
            return e;
        });
    }

    @GetMapping("/exercises/{id}")
    public ResponseEntity<?> get(@PathVariable long id) {
        List<Exercise> found = jdbcTemplate.query("SELECT * FROM exercise WHERE id = ?", (rs, i) -> {
            Exercise e = new Exercise();
            e.setId(rs.getLong("id"));
            e.setTeacherId(rs.getLong("teacher_id"));
            e.setTitle(rs.getString("title"));
            e.setDescription(rs.getString("description"));
            e.setMaxPoints(rs.getDouble("max_points"));
            e.setDeadline(rs.getDate("deadline").toLocalDate());
            return e;
        }, id);
        if (found.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        return ResponseEntity.ok(found.get(0));
    }

    @PostMapping("/exercises")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        if (title == null || title.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le titre est obligatoire"));
        }
        if (body.get("deadline") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "L'échéance est obligatoire (format AAAA-MM-JJ)"));
        }
        LocalDate deadline;
        try {
            deadline = LocalDate.parse((String) body.get("deadline"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(Map.of("error", "L'échéance est obligatoire (format AAAA-MM-JJ)"));
        }
        if (deadline.isBefore(LocalDate.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "L'échéance doit être dans le futur"));
        }
        double maxPoints = 20;
        if (body.get("maxPoints") != null) {
            maxPoints = ((Number) body.get("maxPoints")).doubleValue();
        }
        if (maxPoints <= 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "Le barème doit être positif"));
        }
        // TODO: get the logged-in teacher once authentication exists
        long teacherId = 1;
        if (body.get("teacherId") != null) {
            teacherId = ((Number) body.get("teacherId")).longValue();
        }

        jdbcTemplate.update(
                "INSERT INTO exercise (teacher_id, title, description, max_points, deadline) VALUES (?, ?, ?, ?, ?)",
                teacherId, title, body.get("description"), maxPoints, java.sql.Date.valueOf(deadline));
        Long id = jdbcTemplate.queryForObject("SELECT max(id) FROM exercise", Long.class);

        Map<String, Object> result = new HashMap<>();
        result.put("id", id);
        result.put("title", title);
        result.put("maxPoints", maxPoints);
        result.put("deadline", deadline);
        System.out.println("Nouvel exercice créé : " + title + " (id " + id + ")");
        return ResponseEntity.status(201).body(result);
    }

    @PostMapping("/exercises/{id}/start")
    public ResponseEntity<?> start(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (body.get("studentId") == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "studentId est obligatoire"));
        }
        long studentId = ((Number) body.get("studentId")).longValue();

        List<Exercise> exercises = jdbcTemplate.query("SELECT * FROM exercise WHERE id = ?", EXERCISE_MAPPER, id);
        if (exercises.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("error", "Exercice introuvable"));
        }
        Exercise exercise = exercises.get(0);
        Integer students = jdbcTemplate.queryForObject("SELECT count(*) FROM student WHERE id = ?", Integer.class, studentId);
        if (students == null || students == 0) {
            return ResponseEntity.status(404).body(Map.of("error", "Étudiant introuvable"));
        }
        if (exercise.getDurationMinutes() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Cet exercice n'est pas minuté"));
        }
        Integer submitted = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM submission WHERE exercise_id = ? AND student_id = ?", Integer.class, id, studentId);
        if (submitted != null && submitted > 0) {
            return ResponseEntity.status(409).body(Map.of("error", "Cet étudiant a déjà soumis cet exercice"));
        }

        LocalDateTime now = LocalDateTime.now();
        int inserted = jdbcTemplate.update(
                "INSERT INTO exercise_start (exercise_id, student_id, started_at) VALUES (?, ?, ?) "
                        + "ON CONFLICT (exercise_id, student_id) DO NOTHING",
                id, studentId, Timestamp.valueOf(now));
        LocalDateTime startedAt = jdbcTemplate.queryForObject(
                "SELECT started_at FROM exercise_start WHERE exercise_id = ? AND student_id = ?",
                Timestamp.class, id, studentId).toLocalDateTime();

        Map<String, Object> result = new HashMap<>();
        result.put("exerciseId", id);
        result.put("studentId", studentId);
        result.put("startedAt", startedAt);
        result.put("durationMinutes", exercise.getDurationMinutes());
        result.put("endsAt", startedAt.plusMinutes(exercise.getDurationMinutes()));
        result.put("message", inserted > 0 ? "Décompte démarré" : "Décompte déjà démarré");
        return ResponseEntity.status(inserted > 0 ? 201 : 200).body(result);
    }
}
